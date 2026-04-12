/**
 * Product List Module (JavaScript Version)
 *
 * Manages product list state, loading, filtering, and pagination
 * for the seafood mini-app frontend.
 */

const { ProductAPI } = require('../../api/product.js');

/**
 * Product List Module class for managing product list state and operations
 */
class ProductListModule {
  constructor(options = {}) {
    this.pageSize = options.pageSize || 20;

    // Initialize state with default values
    this.state = {
      products: [],
      isLoading: false,
      isError: false,
      error: null,
      pagination: {
        currentPage: 0,
        totalPages: 0,
        totalProducts: 0,
      },
      filters: {
        category: undefined,
        keyword: undefined,
      },
      hasNext: false,
      hasPrev: false,
    };
  }

  /**
   * Load products from the API
   */
  async loadProducts(params = {}) {
    const loadParams = {
      page: params.page !== undefined ? params.page : 0,
      pageSize: this.pageSize,
      category: params.category !== undefined ? params.category : this.state.filters.category,
      keyword: params.keyword !== undefined ? params.keyword : this.state.filters.keyword,
    };

    // If new filters are provided, update the state filters immediately
    if (params.category !== undefined) {
      this.state.filters.category = params.category;
    }
    if (params.keyword !== undefined) {
      this.state.filters.keyword = params.keyword;
    }

    this.state.isLoading = true;
    this.state.isError = false;
    this.state.error = null;

    try {
      const response = await ProductAPI.getProducts(loadParams);

      this.state.products = response.products;
      this.state.pagination = {
        currentPage: response.page,
        totalPages: response.totalPages,
        totalProducts: response.totalProducts,
      };
      this.state.hasNext = response.hasNext;
      this.state.hasPrev = response.hasPrev;
    } catch (error) {
      this.state.isError = true;
      this.state.error = error instanceof Error ? error : new Error('Unknown error occurred');
      this.state.products = [];
      this.state.hasNext = false;
      this.state.hasPrev = false;
    } finally {
      this.state.isLoading = false;
    }
  }

  /**
   * Load next page of products (for infinite scroll / load more)
   */
  async loadNextPage() {
    if (!this.state.hasNext || this.state.isLoading) {
      return;
    }

    const nextPage = this.state.pagination.currentPage + 1;

    this.state.isLoading = true;

    try {
      const response = await ProductAPI.getProducts({
        page: nextPage,
        pageSize: this.pageSize,
        category: this.state.filters.category,
        keyword: this.state.filters.keyword,
      });

      // Append new products to existing list
      this.state.products = [...this.state.products, ...response.products];
      this.state.pagination = {
        currentPage: response.page,
        totalPages: response.totalPages,
        totalProducts: response.totalProducts,
      };
      this.state.hasNext = response.hasNext;
      this.state.hasPrev = response.hasPrev;
    } catch (error) {
      this.state.isError = true;
      this.state.error = error instanceof Error ? error : new Error('Unknown error occurred');
    } finally {
      this.state.isLoading = false;
    }
  }

  /**
   * Refresh product list (pull-to-refresh - reload from first page)
   */
  async refreshProducts() {
    // Clear existing products before refresh
    this.state.products = [];
    await this.loadProducts({ page: 0 });
  }

  /**
   * Clear error state
   */
  clearError() {
    this.state.isError = false;
    this.state.error = null;
  }

  /**
   * Get product badge text
   */
  getProductBadgeText(product) {
    return product.onSale ? '促销' : null;
  }

  /**
   * Get empty state message
   */
  getEmptyStateMessage() {
    if (this.state.filters.keyword) {
      return '未找到相关商品';
    }
    if (this.state.filters.category) {
      return '该分类下暂无商品';
    }
    return '暂无商品';
  }

  /**
   * Get error message
   */
  getErrorMessage() {
    if (!this.state.error) {
      return '未知错误';
    }

    const message = this.state.error.message;
    if (message.includes('Network')) {
      return '网络连接失败，请检查网络设置';
    }
    if (message.includes('Internal server')) {
      return '服务器错误，请稍后重试';
    }
    return `加载失败: ${message}`;
  }

  // Computed properties

  get isEmpty() {
    return this.state.products.length === 0;
  }

  get isLoading() {
    return this.state.isLoading;
  }

  get isError() {
    return this.state.isError;
  }

  get hasNext() {
    return this.state.hasNext;
  }

  get hasPrev() {
    return this.state.pagination.currentPage > 0;
  }

  get isLoadingMore() {
    return this.state.isLoading && this.state.pagination.currentPage > 0;
  }
}

module.exports = { ProductListModule };
