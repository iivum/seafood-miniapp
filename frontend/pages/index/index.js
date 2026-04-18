const { ProductListModule } = require('../../src/modules/productList/productList.js');
const cartUtil = require('../../utils/cart.js');

// Initialize product list module
const productListModule = new ProductListModule({ pageSize: 20 });

// Hot search keywords (mock data)
const HOT_SEARCH_KEYWORDS = [
  { id: 1, keyword: '三文鱼', count: 5200 },
  { id: 2, keyword: '龙虾', count: 4800 },
  { id: 3, keyword: '大闸蟹', count: 4200 },
  { id: 4, keyword: '生蚝', count: 3800 },
  { id: 5, keyword: '帝王蟹', count: 3500 },
  { id: 6, keyword: '鲍鱼', count: 3100 },
  { id: 7, keyword: '皮皮虾', count: 2900 },
  { id: 8, keyword: '扇贝', count: 2600 }
];

Page({
  data: {
    categories: [
        {id: 'fish', name: '鱼类', icon: '🐟'},
        {id: 'shrimp', name: '虾蟹', icon: '🦐'},
        {id: 'shell', name: '贝类', icon: '🐚'},
        {id: 'live', name: '活鲜', icon: '🦞'}
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
    // Search
    searchKeyword: '',
    searchFocused: false,
    // Hot search
    hotSearchKeywords: HOT_SEARCH_KEYWORDS,
    showHotSearch: true,
  },

  productModule: productListModule,

  onLoad: function () {
    this.initProductList();
  },

  onShow: function () {
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
    this.productModule.clearError();

    try {
      await this.productModule.refreshProducts();
      this.updateViewFromModule();

      wx.showToast({
        title: '刷新成功',
        icon: 'success',
        duration: 1500
      });
    } catch (err) {
      console.error('Pull to refresh failed', err);
      this.handleError(err);
    } finally {
      wx.stopPullDownRefresh();
    }
  },

  /**
   * Reach bottom - triggered when user scrolls to bottom (load more)
   */
  async onReachBottom() {
    if (!this.productModule.hasNext || this.productModule.isLoading) {
      return;
    }

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
    const keyword = this.data.searchKeyword;
    const productsWithHighlight = this.highlightProducts(this.productModule.state.products, keyword);

    this.setData({
      products: productsWithHighlight,
      isLoading: this.productModule.state.isLoading,
      isError: this.productModule.state.isError,
      isEmpty: this.productModule.isEmpty,
      hasMore: this.productModule.hasNext,
      errorMessage: this.productModule.getErrorMessage(),
      emptyMessage: this.productModule.getEmptyStateMessage(),
    });
  },

  /**
   * Highlight matching keyword in product name
   */
  highlightProducts: function(products, keyword) {
    if (!keyword || !keyword.trim()) {
      return products.map(p => ({ ...p, nameHighlight: p.name, nameHighlighted: false }));
    }

    const lowerKeyword = keyword.toLowerCase().trim();
    return products.map(p => {
      const lowerName = p.name.toLowerCase();
      const index = lowerName.indexOf(lowerKeyword);
      if (index === -1) {
        return { ...p, nameHighlight: p.name, nameHighlighted: false };
      }

      // Split name into parts: before, match, after
      const before = p.name.substring(0, index);
      const match = p.name.substring(index, index + keyword.trim().length);
      const after = p.name.substring(index + keyword.trim().length);

      return {
        ...p,
        nameHighlight: p.name,
        nameHighlighted: true,
        nameBefore: before,
        nameMatch: match,
        nameAfter: after
      };
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
   * Handle search input
   */
  onSearchInput: function(e) {
    this.setData({
      searchKeyword: e.detail.value
    });
  },

  /**
   * Execute search
   */
  async onSearch() {
    const keyword = this.data.searchKeyword.trim();

    wx.showLoading({ title: '搜索中...' });

    try {
      await this.productModule.loadProducts({ page: 0, keyword: keyword });
      this.updateViewFromModule();
    } catch (err) {
      console.error('Search failed', err);
      this.handleError(err);
    } finally {
      wx.hideLoading();
    }
  },

  /**
   * Clear search
   */
  onClearSearch: function() {
    this.setData({ searchKeyword: '' });
    this.productModule.loadProducts({ page: 0, keyword: '' });
    this.updateViewFromModule();
  },

  /**
   * Handle category tap
   */
  onCategoryTap: function (e) {
    const category = e.currentTarget.dataset.category;
    wx.showLoading({ title: '加载中...' });

    this.productModule.loadProducts({ page: 0, category: category })
      .then(() => this.updateViewFromModule())
      .catch(err => this.handleError(err))
      .finally(() => wx.hideLoading());
  },

  /**
   * Handle hot search keyword tap
   */
  onHotSearchTap: function(e) {
    const keyword = e.currentTarget.dataset.keyword;
    this.setData({
      searchKeyword: keyword,
      showHotSearch: false
    });
    // Use setTimeout to ensure setData completes before onSearch reads searchKeyword
    setTimeout(() => {
      this.onSearch();
    }, 0);
  },

  /**
   * Show hot search
   */
  onShowHotSearch: function() {
    this.setData({ showHotSearch: true });
  },
});
