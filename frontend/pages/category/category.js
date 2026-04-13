const { ProductListModule } = require('../../src/modules/productList/productList.js');

// Initialize product list module
const productListModule = new ProductListModule({ pageSize: 20 });

// Category definitions with icons
const CATEGORIES = [
  { id: 'fish', name: '鱼类', icon: '/images/icons/fish.png', description: '新鲜海鱼' },
  { id: 'shrimp', name: '虾蟹', icon: '/images/icons/shrimp.png', description: '虾蟹贝类' },
  { id: 'shell', name: '贝类', icon: '/images/icons/shell.png', description: '各类贝壳' },
  { id: 'live', name: '活鲜', icon: '/images/icons/live.png', description: '鲜活水产' },
  { id: 'frozen', name: '冷冻', icon: '/images/icons/frozen.png', description: '冷冻海鲜' },
  { id: 'dried', name: '干货', icon: '/images/icons/dried.png', description: '海鲜干货' }
];

Page({
  data: {
    categories: CATEGORIES,
    selectedCategory: null,
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

  /**
   * Initialize category view
   */
  async initCategories() {
    // Show all categories on load, don't auto-select
    this.setData({ selectedCategory: null, products: [] });
  },

  /**
   * Handle category tap - load products for that category
   */
  onCategoryTap: function (e) {
    const categoryId = e.currentTarget.dataset.id;
    const category = CATEGORIES.find(c => c.id === categoryId);

    this.setData({ selectedCategory: categoryId });
    this.loadCategoryProducts(categoryId);
  },

  /**
   * Load products for a specific category
   */
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
      emptyMessage: this.productModule.getEmptyStateMessage()
    });
  },

  /**
   * Handle errors
   */
  handleError(err) {
    this.setData({
      isError: true,
      errorMessage: this.productModule.getErrorMessage()
    });
  },

  /**
   * Pull down refresh
   */
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

  /**
   * Load more (reach bottom)
   */
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

  /**
   * Navigate to product detail
   */
  goToDetail: function (e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: `/pages-sub/product/product-detail/product-detail?id=${id}`
    });
  },

  /**
   * Add product to cart
   */
  addToCart: function (e) {
    const product = e.currentTarget.dataset.product;
    const cartUtil = require('../../utils/cart.js');
    cartUtil.addToCart(product);
    wx.showToast({
      title: '已加入购物车',
      icon: 'success',
      duration: 1500
    });
  },

  /**
   * Back to category list
   */
  onBackToCategories: function () {
    this.setData({
      selectedCategory: null,
      products: [],
      isEmpty: false,
      isError: false
    });
  }
});
