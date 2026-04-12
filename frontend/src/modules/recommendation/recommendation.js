/**
 * Recommendation Module
 *
 * Rule-based recommendation system for product recommendations.
 * Provides category-based, popular, and co-purchased recommendations.
 */

const { ProductAPI } = require('../../api/product.js');
const { SimpleCache } = require('../../../utils/cache.js');

// Cache TTL for recommendations (5 minutes)
const RECOMMENDATION_CACHE_TTL = 5 * 60 * 1000;

// Maximum recommendations to return
const MAX_RECOMMENDATIONS = 10;

/**
 * Recommendation types
 */
const RecommendationType = {
  CATEGORY: 'category',
  POPULAR: 'popular',
  CO_PURCHASED: 'co_purchased',
  SIMILAR: 'similar',
};

/**
 * Recommendation Module class
 * Provides rule-based product recommendations
 */
class RecommendationModule {
  constructor() {
    this.orderHistory = [];
    this.coPurchaseMatrix = new Map();
    this.popularProductsCache = [];
    this.categoryProductsCache = new Map();
    this.cache = new SimpleCache();

    this.loadOrderHistory();
  }

  /**
   * Load order history from local storage
   */
  loadOrderHistory() {
    try {
      const history = wx.getStorageSync('order_history');
      if (history) {
        this.orderHistory = JSON.parse(history);
      }

      const coPurchase = wx.getStorageSync('co_purchase_matrix');
      if (coPurchase) {
        const parsed = JSON.parse(coPurchase);
        this.coPurchaseMatrix = new Map(Object.entries(parsed));
      }
    } catch (e) {
      console.error('Failed to load order history:', e);
      this.orderHistory = [];
      this.coPurchaseMatrix = new Map();
    }
  }

  /**
   * Save order history to local storage
   */
  saveOrderHistory() {
    try {
      wx.setStorageSync('order_history', JSON.stringify(this.orderHistory));

      const coPurchaseObj = Object.fromEntries(this.coPurchaseMatrix);
      wx.setStorageSync('co_purchase_matrix', JSON.stringify(coPurchaseObj));
    } catch (e) {
      console.error('Failed to save order history:', e);
    }
  }

  /**
   * Record a product purchase for recommendation tracking
   */
  recordPurchase(product) {
    // Add to order history
    this.orderHistory.push({
      productId: product.id,
      category: product.category,
      timestamp: Date.now(),
    });

    // Keep only last 100 purchases
    if (this.orderHistory.length > 100) {
      this.orderHistory = this.orderHistory.slice(-100);
    }

    // Update co-purchase matrix
    this.updateCoPurchaseMatrix(product);

    this.saveOrderHistory();
  }

  /**
   * Update co-purchase matrix when a product is purchased
   */
  updateCoPurchaseMatrix(purchasedProduct) {
    // Find other products purchased around the same time (within 30 minutes)
    const thirtyMinutesAgo = Date.now() - 30 * 60 * 1000;
    const recentPurchases = this.orderHistory.filter(
      item => item.timestamp > thirtyMinutesAgo && item.productId !== purchasedProduct.id
    );

    // Update co-purchase counts
    for (const recent of recentPurchases) {
      if (!this.coPurchaseMatrix.has(recent.productId)) {
        this.coPurchaseMatrix.set(recent.productId, []);
      }

      const entries = this.coPurchaseMatrix.get(recent.productId);
      const existing = entries.find(e => e.productId === purchasedProduct.id);

      if (existing) {
        existing.count++;
      } else {
        entries.push({ productId: purchasedProduct.id, count: 1 });
      }
    }
  }

  /**
   * Get co-purchased products for a given product
   */
  getCoPurchased(productId) {
    const entries = this.coPurchaseMatrix.get(productId);
    if (!entries) return [];

    return entries
      .sort((a, b) => b.count - a.count)
      .slice(0, 5)
      .map(e => e.productId);
  }

  /**
   * Get recommendations for a product detail page
   * Combines category, co-purchased, and popular recommendations
   */
  async getProductRecommendations(product) {
    const recommendations = [];

    // 1. Category-based recommendations
    const categoryRec = await this.getCategoryRecommendations(product.category, product.id);
    if (categoryRec.products.length > 0) {
      recommendations.push(categoryRec);
    }

    // 2. Co-purchased recommendations
    const coPurchasedRec = await this.getCoPurchasedRecommendations(product);
    if (coPurchasedRec.products.length > 0) {
      recommendations.push(coPurchasedRec);
    }

    // 3. Popular products
    const popularRec = await this.getPopularRecommendations(product.id);
    if (popularRec.products.length > 0) {
      recommendations.push(popularRec);
    }

    return recommendations;
  }

  /**
   * Get category-based recommendations
   */
  async getCategoryRecommendations(category, excludeId) {
    const cacheKey = SimpleCache.generateKey('category_recs', { category, excludeId });

    // Check cache
    const cached = this.cache.get(cacheKey);
    if (cached) {
      return cached;
    }

    try {
      const result = await ProductAPI.getProducts({
        page: 0,
        pageSize: MAX_RECOMMENDATIONS,
        category: category,
      });

      const products = result.products.filter(p => p.id !== excludeId).slice(0, MAX_RECOMMENDATIONS);

      const recommendation = {
        type: RecommendationType.CATEGORY,
        products: products,
        reason: `同类商品推荐（${category}）`,
      };

      this.cache.set(cacheKey, recommendation, RECOMMENDATION_CACHE_TTL);
      return recommendation;
    } catch (e) {
      console.error('Failed to get category recommendations:', e);
      return {
        type: RecommendationType.CATEGORY,
        products: [],
        reason: `同类商品推荐（${category}）`,
      };
    }
  }

  /**
   * Get co-purchased recommendations
   */
  async getCoPurchasedRecommendations(product) {
    const coPurchasedIds = this.getCoPurchased(product.id);

    if (coPurchasedIds.length === 0) {
      return {
        type: RecommendationType.CO_PURCHASED,
        products: [],
        reason: '常一起购买',
      };
    }

    try {
      // Fetch co-purchased products
      const products = [];
      for (const id of coPurchasedIds) {
        try {
          const result = await ProductAPI.getProducts({
            page: 0,
            pageSize: 1,
            keyword: id,
          });
          if (result.products.length > 0) {
            products.push(result.products[0]);
          }
        } catch (e) {
          // Skip products that fail to load
        }
      }

      return {
        type: RecommendationType.CO_PURCHASED,
        products: products.slice(0, MAX_RECOMMENDATIONS),
        reason: '常一起购买',
      };
    } catch (e) {
      console.error('Failed to get co-purchased recommendations:', e);
      return {
        type: RecommendationType.CO_PURCHASED,
        products: [],
        reason: '常一起购买',
      };
    }
  }

  /**
   * Get popular product recommendations
   */
  async getPopularRecommendations(excludeId) {
    // Check local cache first
    if (this.popularProductsCache.length > 0) {
      const filtered = this.popularProductsCache.filter(p => p.id !== excludeId);
      return {
        type: RecommendationType.POPULAR,
        products: filtered.slice(0, MAX_RECOMMENDATIONS),
        reason: '热门商品',
      };
    }

    try {
      const result = await ProductAPI.getProducts({
        page: 0,
        pageSize: MAX_RECOMMENDATIONS * 2,
      });

      // Filter out the current product and cache
      const products = result.products.filter(p => p.id !== excludeId);
      this.popularProductsCache = products;

      return {
        type: RecommendationType.POPULAR,
        products: products.slice(0, MAX_RECOMMENDATIONS),
        reason: '热门商品',
      };
    } catch (e) {
      console.error('Failed to get popular recommendations:', e);
      return {
        type: RecommendationType.POPULAR,
        products: [],
        reason: '热门商品',
      };
    }
  }

  /**
   * Get user personalized recommendations based on purchase history
   */
  async getPersonalizedRecommendations() {
    const recommendations = [];

    // Get user's preferred categories from order history
    const categoryCounts = new Map();
    for (const item of this.orderHistory) {
      categoryCounts.set(item.category, (categoryCounts.get(item.category) || 0) + 1);
    }

    // Sort categories by frequency
    const sortedCategories = Array.from(categoryCounts.entries())
      .sort((a, b) => b[1] - a[1])
      .map(([category]) => category);

    // Get recommendations for each preferred category
    for (const category of sortedCategories.slice(0, 3)) {
      const rec = await this.getCategoryRecommendations(category, '');
      if (rec.products.length > 0) {
        recommendations.push(rec);
      }
    }

    // Add popular recommendations if we don't have enough
    if (recommendations.length < 3) {
      const popularRec = await this.getPopularRecommendations('');
      if (popularRec.products.length > 0) {
        recommendations.push(popularRec);
      }
    }

    return recommendations;
  }

  /**
   * Clear recommendation cache
   */
  clearCache() {
    this.popularProductsCache = [];
    this.categoryProductsCache.clear();
  }
}

// Export singleton instance
const recommendationModule = new RecommendationModule();

module.exports = {
  RecommendationModule,
  recommendationModule,
  RecommendationType,
};
