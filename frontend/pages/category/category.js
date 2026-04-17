const { ProductListModule } = require('../../src/modules/productList/productList.js');

// Initialize product list module
const productListModule = new ProductListModule({ pageSize: 20 });

// Category definitions with emoji icons (fallback when images fail)
const CATEGORIES = [
  { id: 'fish', name: '鱼类', icon: '🐟', description: '新鲜海鱼' },
  { id: 'shrimp', name: '虾蟹', icon: '🦐', description: '虾蟹贝类' },
  { id: 'shell', name: '贝类', icon: '🐚', description: '各类贝壳' },
  { id: 'live', name: '活鲜', icon: '🦞', description: '鲜活水产' },
  { id: 'frozen', name: '冷冻', icon: '🧊', description: '冷冻海鲜' },
  { id: 'dried', name: '干货', icon: '🏺', description: '海鲜干货' }
];

Page({
  data: {
    categories: CATEGORIES,
    selectedCategory: null,
    selectedCategoryName: '',
    products: [],
    isLoading: false,
    isLoadingMore: false,
    isError: false,
    isEmpty: false,
    errorMessage: '',
    emptyMessage: '',
    hasMore: true
  },

  productModule: productListModule,

  onLoad: function () {
    this.initCategories();
  },

  onShow: function () {
  },

  async initCategories() {
    this.setData({ selectedCategory: null, products: [] });
  },

  onCategoryTap: function (e) {
    const categoryId = e.currentTarget.dataset.id;
    const category = CATEGORIES.find(c => c.id === categoryId);

    this.setData({ selectedCategory: categoryId, selectedCategoryName: category ? category.name : '' });
    this.loadCategoryProducts(categoryId);
  },

  async loadCategoryProducts(categoryId) {
    wx.showLoading({ title: '加载中...' });

    try {
      await this.productModule.loadProducts({ page: 0, category: categoryId });
      this.updateViewFromModule();
    } catch (err) {
      console.error('Failed to load category products', err);
      this.handleError(err);
    } finally {
      wx.hideLoading();
    }
  },

  updateViewFromModule() {
    this.setData({
      products: this.productModule.state.products,
      isLoading: this.productModule.state.isLoading,
      isError: this.productModule.state.isError,
      isEmpty: this.productModule.isEmpty,
      hasMore: this.productModule.hasNext,
      errorMessage: this.productModule.getErrorMessage(),
      emptyMessage: this.productModule.getEmptyStateMessage()
    });
  },

  handleError(err) {
    this.setData({
      isError: true,
      errorMessage: this.productModule.getErrorMessage()
    });
  },

  async onPullDownRefresh() {
    const categoryId = this.data.selectedCategory;
    if (!categoryId) {
      wx.stopPullDownRefresh();
      return;
    }

    try {
      await this.productModule.refreshProducts();
      this.updateViewFromModule();
      wx.showToast({ title: '刷新成功', icon: 'success', duration: 1500 });
    } catch (err) {
      console.error('Refresh failed', err);
      this.handleError(err);
    } finally {
      wx.stopPullDownRefresh();
    }
  },

  async onReachBottom() {
    if (!this.data.selectedCategory || !this.productModule.hasNext || this.productModule.isLoading) {
      return;
    }

    this.setData({ isLoadingMore: true });

    try {
      await this.productModule.loadNextPage();
      this.updateViewFromModule();
    } catch (err) {
      console.error('Load more failed', err);
      wx.showToast({ title: '加载更多失败', icon: 'none' });
    } finally {
      this.setData({ isLoadingMore: false });
    }
  },

  goToDetail: function (e) {
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.navigateTo({
        url: '/pages-sub/user/login/login'
      });
      return;
    }
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: `/pages-sub/product/product-detail/product-detail?id=${id}`
    });
  },

  addToCart: function (e) {
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    const product = e.currentTarget.dataset.product;
    const cartUtil = require('../../utils/cart.js');
    cartUtil.addToCart(product);
    wx.showToast({
      title: '已加入购物车',
      icon: 'success',
      duration: 1500
    });
  },

  onRetry: function () {
    const categoryId = this.data.selectedCategory;
    if (categoryId) {
      this.loadCategoryProducts(categoryId);
    }
  },

  onBackToCategories: function () {
    this.setData({
      selectedCategory: null,
      selectedCategoryName: '',
      products: [],
      isEmpty: false,
      isError: false
    });
  }
});
