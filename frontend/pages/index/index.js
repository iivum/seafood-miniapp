const { ProductListModule } = require('../../src/modules/productList/productList.js');
const { BannerAPI } = require('../../src/api/banner.js');
const { CartAPI } = require('../../src/features/cart/api');

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

// mp-01 首页 5 分类,与后端 ProductCategory(sealed interface)displayName 一一对应
// (参见 pages/category/category.js 同一契约)。id 必须用中文 displayName ——
// repo.findByCategory 精确匹配商品 category 字段,英文/自造 id 会匹配 0 商品。
// 旧 bug:硬编码 4 项(fish/shrimp/shell/live),且 "活鲜" 不在后端 5 类目里,
// 与 OD 原型(frontend/e2e/od-golden/mp-01-home.png)的 5 类目也不符。
const CATEGORIES = [
  { id: '鱼类', name: '鱼类', icon: '🐟' },
  { id: '虾蟹', name: '虾蟹', icon: '🦐' },
  { id: '贝类', name: '贝类', icon: '🐚' },
  { id: '软体', name: '软体', icon: '🦑' },
  { id: '海藻', name: '海藻', icon: '🌿' },
];

Page({
  data: {
    categories: CATEGORIES,
    // 当前生效的分类筛选;空串 = 未筛选(filter chip "全部" 高亮态)。
    activeCategory: '',
    products: [],
    // section header "今日 {N} 款推荐" 用的真实商品总数(来自后端分页 totalProducts),
    // 不再硬编码"每日 10 款 · 限时优惠"。
    totalProducts: 0,
    // home hero 轮播(后端驱动,GET /api/banners;空则 swiper 不渲染)
    banners: [],
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
    this.loadBanners();
  },

  onShow: function () {
  },

  /** 拉取后端 banner → setData;失败降级空列表(swiper wx:for 兜底)。 */
  async loadBanners() {
    try {
      const banners = await BannerAPI.getBanners();
      this.setData({ banners });
    } catch (err) {
      console.warn('Failed to load banners', err);
      this.setData({ banners: [] });
    }
  },

  /** 点击 banner:有 targetProductId 跳商品详情,无则纯展示不跳转。 */
  onBannerTap(e) {
    const bannerId = e.currentTarget.dataset.bannerId;
    const banner = (this.data.banners || []).find((b) => b.id === bannerId);
    if (banner && banner.targetProductId) {
      wx.navigateTo({
        url: `/pages-sub/product/product-detail/product-detail?id=${banner.targetProductId}`,
      });
    }
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
      totalProducts: this.productModule.state.pagination.totalProducts,
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
   * Add product to cart — P1 鉴权守卫。
   * 未登录跳 login 页;已登录走 cartApi.addItem(后端 needAuth)。
   * 不再走本地 cartUtil.addToCart(数据永远到不了后端,登录后看不到)。
   */
  onAddToCart(e) {
    const product = e.currentTarget.dataset.product;
    const productId = product && product.id;
    const token = wx.getStorageSync('accessToken');
    if (!token) {
      // 未登录:跳 login。wx.navigateTo 保留历史栈,登录成功后
      // login 页 onShow 检测到 isAuthenticated 会自动 wx.navigateBack 回首页。
      wx.navigateTo({
        url: '/pages-sub/user/login/login?redirect=/pages/index/index',
      });
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    // 已登录:走后端 needAuth API
    CartAPI.addItem({ productId, quantity: 1 })
      .then(() => {
        wx.showToast({ title: '已加入购物车', icon: 'success' });
      })
      .catch((err) => {
        console.error('onAddToCart 失败', err);
        wx.showToast({ title: '加入失败', icon: 'none' });
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
    // 同步 activeCategory:供 filter chip "全部" 判断激活态(!activeCategory)。
    this.setData({ activeCategory: category });
    wx.showLoading({ title: '加载中...' });

    this.productModule.loadProducts({ page: 0, category: category })
      .then(() => this.updateViewFromModule())
      .catch(err => this.handleError(err))
      .finally(() => wx.hideLoading());
  },

  /**
   * 清空当前分类筛选,展示全部商品。三处复用:
   *   ① filter chip "全部" bindtap(OD 唯一功能性 chip)
   *   ② section header "查看全部→" 链接
   *   ③ 空态 shared-empty 的 retry —— wxml 此前已绑定 bind:retry="onClearFilter",
   *      但该方法从未定义(死绑定 bug),点击"查看全部"重试按钮会直接报错。
   */
  onClearFilter: function () {
    this.setData({ activeCategory: '' });
    wx.showLoading({ title: '加载中...' });

    this.productModule.loadProducts({ page: 0, category: '' })
      .then(() => this.updateViewFromModule())
      .catch(err => this.handleError(err))
      .finally(() => wx.hideLoading());
  },

  /**
   * Filter chip 占位项(捕捞当日 / 限量拾 / 满199减30)。
   * 后端 Product 领域没有对应字段,不能做成真筛选;点击给明确 toast 反馈,
   * 不静默无动作、也不伪造筛选行为。
   */
  onFilterPlaceholderTap: function (e) {
    const label = e.currentTarget.dataset.label;
    wx.showToast({ title: `${label} 功能开发中`, icon: 'none' });
  },

  /**
   * 通知铃铛(纯装饰,无后端通知能力)。
   */
  onBellTap: function () {
    wx.showToast({ title: '功能开发中', icon: 'none' });
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
