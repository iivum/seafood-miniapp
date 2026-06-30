const { ProductListModule } = require('../../src/modules/productList/productList.js');
const { CartAPI } = require('../../src/features/cart/api');

// Initialize product list module
const productListModule = new ProductListModule({ pageSize: 20 });

// 分类定义。id 必须 = 后端 ProductCategory(sealed interface)的中文 displayName ——
// repo.findByCategory 精确匹配商品 category 字段,传英文 id 会匹配 0 商品(旧 bug)。
// 5 类与后端 permits 列表一一对应:鱼类/虾蟹/贝类/软体/海藻。
const CATEGORIES = [
  { id: '鱼类', name: '鱼类', icon: '🐟', description: '新鲜海鱼' },
  { id: '虾蟹', name: '虾蟹', icon: '🦐', description: '鲜活虾蟹' },
  { id: '贝类', name: '贝类', icon: '🐚', description: '各类贝壳' },
  { id: '软体', name: '软体', icon: '🦑', description: '鱿鱼章鱼' },
  { id: '海藻', name: '海藻', icon: '🌿', description: '海带紫菜' }
];

Page({
  data: {
    categories: CATEGORIES,
    selectedCategory: null,
    selectedCategoryName: '',
    selectedCategoryDesc: '',
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
    // 自动选中首个分类并加载其商品 —— 避免空载(onLoad 后 grid 直接有内容,
    // 而非等用户点击;旧逻辑只置空 → 分类页首屏永远空)。
    const first = CATEGORIES[0];
    this.setData({ selectedCategory: first.id, selectedCategoryName: first.name, selectedCategoryDesc: first.description });
    await this.loadCategoryProducts(first.id);
  },

  onCategoryTap: function (e) {
    const categoryId = e.currentTarget.dataset.id;
    const category = CATEGORIES.find(c => c.id === categoryId);

    this.setData({
      selectedCategory: categoryId,
      selectedCategoryName: category ? category.name : '',
      selectedCategoryDesc: category ? category.description : '',
    });
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

  /**
   * Add product to cart — P1 鉴权守卫(Task 4.x follow-up / M8)。
   * 未登录跳 login 页;已登录走 cartApi.addItem(后端 needAuth)。
   * 不再走本地 cartUtil.addToCart(数据永远到不了后端,登录后看不到)。
   * 与首页 pages/index/index.js addToCart 行为对齐。
   */
  addToCart(e) {
    const product = e.currentTarget.dataset.product;
    const productId = product && product.id;
    const token = wx.getStorageSync('accessToken');
    if (!token) {
      // 未登录:跳 login。wx.navigateTo 保留历史栈,登录成功后
      // login 页 onShow 检测到 isAuthenticated 会自动 wx.navigateBack 回分类页。
      wx.navigateTo({
        url: '/pages-sub/user/login/login?redirect=/pages/category/category',
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
        console.error('addToCart 失败', err);
        wx.showToast({ title: '加入失败', icon: 'none' });
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
