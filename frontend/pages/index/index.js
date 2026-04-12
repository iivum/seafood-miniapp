const { ProductListModule } = require('../../src/modules/productList/productList.js');
const cartUtil = require('../../utils/cart.js');

// Initialize product list module
const productListModule = new ProductListModule({ pageSize: 20 });

Page({
  data: {
    categories: [
      { name: '鱼类', icon: '/images/icons/fish.png' },
      { name: '虾蟹', icon: '/images/icons/shrimp.png' },
      { name: '贝类', icon: '/images/icons/shell.png' },
      { name: '活鲜', icon: '/images/icons/live.png' }
    ],
    products: [],
    // Loading states
    isLoading: false,
    isLoadingMore: false,
    isError: false,
    isEmpty: false,
    // Error and status messages
    errorMessage: '',
    emptyMessage: '',
    // Pull-down refresh enabled
    hasMore: true,
  },

  productModule: productListModule,

  onLoad: function () {
    this.initProductList();
  },

  onShow: function () {
    // 页面显示时刷新购物车角标（如果需要的话）
  },

  /**
   * Initialize product list - load first page
   */
  async initProductList() {
    wx.showLoading({ title: '加载中...' });
    
    try {
      await this.productModule.loadProducts({ page: 0 });
      this.updateViewFromModule();
    } catch (err) {
      console.error('Failed to load products', err);
      this.handleError(err);
    } finally {
      wx.hideLoading();
    }
  },

  /**
   * Pull down refresh - triggered when user pulls down
   */
  async onPullDownRefresh() {
    // Clear error state first
    this.productModule.clearError();
    
    try {
      await this.productModule.refreshProducts();
      this.updateViewFromModule();
      
      // Show success feedback
      wx.showToast({
        title: '刷新成功',
        icon: 'success',
        duration: 1500
      });
    } catch (err) {
      console.error('Pull to refresh failed', err);
      this.handleError(err);
    } finally {
      // Stop pull-down refresh animation
      wx.stopPullDownRefresh();
    }
  },

  /**
   * Reach bottom - triggered when user scrolls to bottom (load more)
   */
  async onReachBottom() {
    // Check if there's more data to load
    if (!this.productModule.hasNext || this.productModule.isLoading) {
      return;
    }

    // Set loading more state
    this.setData({ isLoadingMore: true });

    try {
      await this.productModule.loadNextPage();
      this.updateViewFromModule();
    } catch (err) {
      console.error('Load more failed', err);
      wx.showToast({
        title: '加载更多失败',
        icon: 'none'
      });
    } finally {
      this.setData({ isLoadingMore: false });
    }
  },

  /**
   * Update page data from module state
   */
  updateViewFromModule() {
    this.setData({
      products: this.productModule.state.products,
      isLoading: this.productModule.state.isLoading,
      isError: this.productModule.state.isError,
      isEmpty: this.productModule.isEmpty,
      hasMore: this.productModule.hasNext,
      errorMessage: this.productModule.getErrorMessage(),
      emptyMessage: this.productModule.getEmptyStateMessage(),
    });
  },

  /**
   * Handle errors from module
   */
  handleError(err) {
    const errorMessage = this.productModule.getErrorMessage();
    this.setData({
      isError: true,
      errorMessage: errorMessage,
    });

    // Show error toast
    wx.showToast({
      title: errorMessage,
      icon: 'none',
      duration: 2000
    });
  },

  /**
   * Retry loading after error
   */
  async onRetry() {
    this.productModule.clearError();
    await this.initProductList();
  },

  goToDetail: function (e) {
    const app = getApp();
    if (!app.globalData.userInfo) {
      // 未登录，跳转到登录页面
      wx.navigateTo({
        url: '/pages/login/login'
      });
      return;
    }
    
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: `/pages/product-detail/product-detail?id=${id}`
    });
  },

  /**
   * Add product to cart
   */
  addToCart: function (e) {
    const product = e.currentTarget.dataset.product;
    cartUtil.addToCart(product);
    wx.showToast({
      title: '已加入购物车',
      icon: 'success',
      duration: 1500
    });
  },

  /**
   * Search functionality
   */
  onSearch: function () {
    wx.showToast({
      title: '搜索功能开发中...',
      icon: 'none'
    });
  },

  /**
   * Handle category tap
   */
  onCategoryTap: function (e) {
    const category = e.currentTarget.dataset.category;
    wx.showToast({
      title: `${category}分类开发中...`,
      icon: 'none'
    });
  },
});
