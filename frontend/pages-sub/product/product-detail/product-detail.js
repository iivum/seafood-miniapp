const request = require('../../utils/request.js');
const cartUtil = require('../../utils/cart.js');
const { recommendationModule } = require('../../src/modules/recommendation/recommendation.js');

Page({
  data: {
    product: {},
    recommendations: [],
    isLoadingRecommendations: false
  },

  onLoad: function(options) {
    const id = options.id;
    this.fetchProductDetail(id);
  },

  fetchProductDetail: function(id) {
    request({
      url: `/products/${id}`,
      needAuth: true
    })
    .then(res => {
      this.setData({ product: res });
      this.fetchRecommendations(res);
    })
    .catch(err => {
      console.error('Fetch product detail failed', err);
      if (err.statusCode !== 401) {
        wx.showToast({
          title: '加载商品失败',
          icon: 'none'
        });
      }
    });
  },

  fetchRecommendations: function(product) {
    this.setData({ isLoadingRecommendations: true });

    recommendationModule.getProductRecommendations(product)
      .then(recommendations => {
        // Filter out empty recommendation groups
        const validRecommendations = recommendations.filter(rec => rec.products.length > 0);
        this.setData({ recommendations: validRecommendations });
      })
      .catch(err => {
        console.error('Failed to load recommendations:', err);
      })
      .finally(() => {
        this.setData({ isLoadingRecommendations: false });
      });
  },

  onAddToCart: function() {
    const product = this.data.product;
    cartUtil.addToCart(product);
    recommendationModule.recordPurchase(product);
    wx.showToast({ title: '已加入购物车', icon: 'success' });
  },

  onBuyNow: function() {
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.navigateTo({
        url: '/pages-sub/user/login/login'
      });
      return;
    }

    const product = this.data.product;
    cartUtil.addToCart(product);
    recommendationModule.recordPurchase(product);
    wx.switchTab({ url: '/pages/cart/cart' });
  },

  goToHome: function() {
    wx.switchTab({ url: '/pages/index/index' });
  },

  goToCart: function() {
    wx.switchTab({ url: '/pages/cart/cart' });
  },

  goToProductDetail: function(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: `/pages-sub/product/product-detail/product-detail?id=${id}`
    });
  }
})
