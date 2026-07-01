/**
 * index.js tests —— mp-01 首页对齐 OD 原型(openspec change mp-od-prototype-alignment)。
 *
 * 覆盖本次变更涉及的 .js 逻辑(brief `.superpowers/sdd/mp-od-1-home-brief.md`):
 *  1. categories 数据 bug 修复:硬编码 4 项(fish/shrimp/shell/live,含后端不存在的
 *     "活鲜")→ 与后端 ProductCategory(sealed interface)一致的 5 个中文 displayName
 *     (参见 pages/category/category.js 同一契约、同一 bug 修法)。
 *  2. section header 动态文案:"今日 {N} 款推荐" 用 productModule 真实
 *     totalProducts,不再硬编码"每日 10 款 · 限时优惠"。
 *  3. filter chip 行:onClearFilter(新增 —— 此前 wxml 空态 shared-empty 已引用
 *     bind:retry="onClearFilter" 但从未定义,是死绑定 bug)+ onCategoryTap 同步
 *     activeCategory(供"全部" chip 判断激活态)+ onFilterPlaceholderTap(3 个非
 *     功能性占位 chip 用 toast 明确反馈,不伪造筛选行为)。
 *  4. onBellTap:通知铃铛纯装饰,toast 反馈。
 *
 * 此前本文件从未被任何测试 require,不进 Jest 覆盖率统计;本次首次接入 UI 的
 * search/hot-search 等既有方法(未改动逻辑)也一并补上用例,避免 index.js 首次
 * 进入覆盖率统计后拖累全局阈值(CLAUDE.md:覆盖率全局 ≥80%)。
 */

global.wx = {
  showLoading: jest.fn(),
  hideLoading: jest.fn(),
  showToast: jest.fn(),
  navigateTo: jest.fn(),
  stopPullDownRefresh: jest.fn(),
  getStorageSync: jest.fn(() => ''),
};

const mockApp = { globalData: { userInfo: { id: 'u-1' } } };
global.getApp = jest.fn(() => mockApp);

const mockAddItem = jest.fn().mockResolvedValue();
jest.mock('../../../src/features/cart/api', () => ({
  CartAPI: { addItem: mockAddItem },
}));

const mockGetBanners = jest.fn().mockResolvedValue([]);
jest.mock('../../../src/api/banner.js', () => ({
  BannerAPI: { getBanners: mockGetBanners },
}));

const mockLoadProducts = jest.fn().mockResolvedValue();
const mockRefresh = jest.fn().mockResolvedValue();
const mockLoadNext = jest.fn().mockResolvedValue();
const mockClearError = jest.fn();
const mockModule = {
  loadProducts: mockLoadProducts,
  refreshProducts: mockRefresh,
  loadNextPage: mockLoadNext,
  clearError: mockClearError,
  state: {
    products: [{ id: 'p1', name: '三文鱼' }],
    isLoading: false,
    isError: false,
    pagination: { currentPage: 0, totalPages: 1, totalProducts: 9 },
  },
  isEmpty: false,
  isLoading: false,
  hasNext: true,
  getErrorMessage: () => '加载失败',
  getEmptyStateMessage: () => '暂无商品',
};
jest.mock('../../../src/modules/productList/productList.js', () => ({
  ProductListModule: jest.fn().mockImplementation(() => mockModule),
}));

let pageConfig;
global.Page = (config) => { pageConfig = config; };
require('../index.js');

// 后端 ProductCategory sealed interface 的 5 个 displayName(单一事实源,
// 与 pages/category/category.js 测试用同一常量)。
const BACKEND_CATEGORIES = ['鱼类', '虾蟹', '贝类', '软体', '海藻'];

describe('index (mp-01 首页)', () => {
  let ctx;

  beforeEach(() => {
    jest.clearAllMocks();
    mockApp.globalData.userInfo = { id: 'u-1' };
    wx.getStorageSync.mockReturnValue('mock-token');
    mockModule.state.pagination.totalProducts = 9;
    mockModule.isLoading = false;
    mockModule.hasNext = true;
    ctx = {
      data: JSON.parse(JSON.stringify(pageConfig.data)),
      productModule: mockModule,
      onLoad: pageConfig.onLoad,
      onShow: pageConfig.onShow,
      loadBanners: pageConfig.loadBanners,
      onBannerTap: pageConfig.onBannerTap,
      initProductList: pageConfig.initProductList,
      onPullDownRefresh: pageConfig.onPullDownRefresh,
      onReachBottom: pageConfig.onReachBottom,
      updateViewFromModule: pageConfig.updateViewFromModule,
      highlightProducts: pageConfig.highlightProducts,
      handleError: pageConfig.handleError,
      onRetry: pageConfig.onRetry,
      goToDetail: pageConfig.goToDetail,
      addToCart: pageConfig.addToCart,
      onSearchInput: pageConfig.onSearchInput,
      onSearch: pageConfig.onSearch,
      onClearSearch: pageConfig.onClearSearch,
      onCategoryTap: pageConfig.onCategoryTap,
      onClearFilter: pageConfig.onClearFilter,
      onFilterPlaceholderTap: pageConfig.onFilterPlaceholderTap,
      onBellTap: pageConfig.onBellTap,
      onHotSearchTap: pageConfig.onHotSearchTap,
      onShowHotSearch: pageConfig.onShowHotSearch,
    };
    ctx.setData = jest.fn(function (patch) { Object.assign(this.data, patch); }.bind(ctx));
  });

  it('categories 与后端 ProductCategory displayName 契约一致(中文 id,5 类)', () => {
    const ids = pageConfig.data.categories.map((c) => c.id);
    expect(ids).toEqual(BACKEND_CATEGORIES);
    pageConfig.data.categories.forEach((c) => expect(BACKEND_CATEGORIES).toContain(c.name));
  });

  it('updateViewFromModule 用真实 totalProducts 更新 section header 计数(非硬编码文案)', () => {
    mockModule.state.pagination.totalProducts = 9;
    ctx.updateViewFromModule();
    expect(ctx.data.totalProducts).toBe(9);
  });

  it('onCategoryTap 同步 activeCategory(供"全部" chip 判断激活态)并按中文分类名过滤', async () => {
    ctx.onCategoryTap({ currentTarget: { dataset: { category: '虾蟹' } } });
    expect(ctx.data.activeCategory).toBe('虾蟹');
    await new Promise((r) => setTimeout(r, 0));
    expect(mockLoadProducts).toHaveBeenCalledWith({ page: 0, category: '虾蟹' });
  });

  it('onCategoryTap 失败时走 handleError', async () => {
    mockLoadProducts.mockRejectedValueOnce(new Error('net'));
    ctx.onCategoryTap({ currentTarget: { dataset: { category: '虾蟹' } } });
    await new Promise((r) => setTimeout(r, 0));
    expect(ctx.data.isError).toBe(true);
  });

  it('onClearFilter 清空 activeCategory 并重新加载全部商品(不带 category 筛选)', async () => {
    ctx.data.activeCategory = '虾蟹';
    ctx.onClearFilter();
    expect(ctx.data.activeCategory).toBe('');
    await new Promise((r) => setTimeout(r, 0));
    expect(mockLoadProducts).toHaveBeenCalledWith({ page: 0, category: '' });
  });

  it('onFilterPlaceholderTap 展示"功能开发中" toast,不触发筛选(占位 chip 不伪造行为)', () => {
    ctx.onFilterPlaceholderTap({ currentTarget: { dataset: { label: '捕捞当日' } } });
    expect(wx.showToast).toHaveBeenCalledWith(
      expect.objectContaining({ title: expect.stringContaining('捕捞当日') })
    );
    expect(mockLoadProducts).not.toHaveBeenCalled();
  });

  it('onBellTap 展示"功能开发中" toast(通知铃铛纯装饰,无后端能力)', () => {
    ctx.onBellTap();
    expect(wx.showToast).toHaveBeenCalledWith(
      expect.objectContaining({ title: expect.stringContaining('开发中') })
    );
  });

  it('onLoad 不抛错(触发 initProductList + loadBanners)', () => {
    expect(() => ctx.onLoad()).not.toThrow();
  });

  it('onShow 不抛错', () => {
    expect(() => ctx.onShow()).not.toThrow();
  });

  it('loadBanners 成功时 setData banners', async () => {
    mockGetBanners.mockResolvedValueOnce([{ id: 'b1', title: 'x' }]);
    await ctx.loadBanners();
    expect(ctx.data.banners).toEqual([{ id: 'b1', title: 'x' }]);
  });

  it('loadBanners 失败时降级空数组', async () => {
    mockGetBanners.mockRejectedValueOnce(new Error('net'));
    await ctx.loadBanners();
    expect(ctx.data.banners).toEqual([]);
  });

  it('onBannerTap 有 targetProductId 时跳详情', () => {
    ctx.data.banners = [{ id: 'b1', targetProductId: 'p9' }];
    ctx.onBannerTap({ currentTarget: { dataset: { bannerId: 'b1' } } });
    expect(wx.navigateTo).toHaveBeenCalledWith(
      expect.objectContaining({ url: expect.stringContaining('p9') })
    );
  });

  it('onBannerTap 无 targetProductId 时不跳转', () => {
    ctx.data.banners = [{ id: 'b1', targetProductId: null }];
    ctx.onBannerTap({ currentTarget: { dataset: { bannerId: 'b1' } } });
    expect(wx.navigateTo).not.toHaveBeenCalled();
  });

  it('initProductList 成功时加载首页商品', async () => {
    await ctx.initProductList();
    expect(mockLoadProducts).toHaveBeenCalledWith({ page: 0 });
    expect(wx.hideLoading).toHaveBeenCalled();
  });

  it('initProductList 失败时走 handleError', async () => {
    mockLoadProducts.mockRejectedValueOnce(new Error('net'));
    await ctx.initProductList();
    expect(ctx.data.isError).toBe(true);
    expect(wx.hideLoading).toHaveBeenCalled();
  });

  it('onPullDownRefresh 成功时刷新并提示成功', async () => {
    await ctx.onPullDownRefresh();
    expect(mockRefresh).toHaveBeenCalled();
    expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '刷新成功' }));
    expect(wx.stopPullDownRefresh).toHaveBeenCalled();
  });

  it('onPullDownRefresh 失败时走 handleError', async () => {
    mockRefresh.mockRejectedValueOnce(new Error('net'));
    await ctx.onPullDownRefresh();
    expect(ctx.data.isError).toBe(true);
    expect(wx.stopPullDownRefresh).toHaveBeenCalled();
  });

  it('onReachBottom 有下一页时加载更多', async () => {
    await ctx.onReachBottom();
    expect(mockLoadNext).toHaveBeenCalled();
    expect(ctx.data.isLoadingMore).toBe(false);
  });

  it('onReachBottom 无下一页时早退', async () => {
    mockModule.hasNext = false;
    await ctx.onReachBottom();
    expect(mockLoadNext).not.toHaveBeenCalled();
  });

  it('onReachBottom 加载更多失败时提示', async () => {
    mockLoadNext.mockRejectedValueOnce(new Error('net'));
    await ctx.onReachBottom();
    expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '加载更多失败' }));
  });

  it('highlightProducts 无关键词时原样返回', () => {
    const result = ctx.highlightProducts([{ name: '三文鱼' }], '');
    expect(result).toEqual([{ name: '三文鱼', nameHighlight: '三文鱼', nameHighlighted: false }]);
  });

  it('highlightProducts 命中关键词时拆分高亮片段', () => {
    const result = ctx.highlightProducts([{ name: '波士顿龙虾' }], '龙虾');
    expect(result[0].nameHighlighted).toBe(true);
    expect(result[0].nameMatch).toBe('龙虾');
  });

  it('onRetry 清 error 后重新加载', async () => {
    await ctx.onRetry();
    expect(mockClearError).toHaveBeenCalled();
    expect(mockLoadProducts).toHaveBeenCalledWith({ page: 0 });
  });

  it('goToDetail 已登录跳商品详情', () => {
    ctx.goToDetail({ currentTarget: { dataset: { id: 'p9' } } });
    expect(wx.navigateTo).toHaveBeenCalledWith({ url: '/pages-sub/product/product-detail/product-detail?id=p9' });
  });

  it('goToDetail 未登录跳登录页', () => {
    mockApp.globalData.userInfo = null;
    ctx.goToDetail({ currentTarget: { dataset: { id: 'p9' } } });
    expect(wx.navigateTo).toHaveBeenCalledWith({ url: '/pages-sub/user/login/login' });
  });

  it('addToCart 已登录调 CartAPI.addItem 并提示', async () => {
    ctx.addToCart({ currentTarget: { dataset: { product: { id: 'p1' } } } });
    await new Promise((r) => setTimeout(r, 0));
    expect(mockAddItem).toHaveBeenCalledWith({ productId: 'p1', quantity: 1 });
    expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '已加入购物车' }));
  });

  it('addToCart 未登录跳 login 页(带 redirect)', () => {
    wx.getStorageSync.mockReturnValue('');
    ctx.addToCart({ currentTarget: { dataset: { product: { id: 'p1' } } } });
    expect(mockAddItem).not.toHaveBeenCalled();
    expect(wx.navigateTo).toHaveBeenCalledWith(
      expect.objectContaining({ url: expect.stringMatching(/login.*redirect=/) })
    );
  });

  it('addToCart 后端失败时提示加入失败', async () => {
    mockAddItem.mockRejectedValueOnce(new Error('net'));
    ctx.addToCart({ currentTarget: { dataset: { product: { id: 'p1' } } } });
    await new Promise((r) => setTimeout(r, 0));
    expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '加入失败' }));
  });

  it('onSearchInput 更新 searchKeyword', () => {
    ctx.onSearchInput({ detail: { value: '龙虾' } });
    expect(ctx.data.searchKeyword).toBe('龙虾');
  });

  it('onSearch 用当前关键词加载商品(搜索框首次接入 UI,复用已有方法)', async () => {
    ctx.data.searchKeyword = ' 龙虾 ';
    await ctx.onSearch();
    expect(mockLoadProducts).toHaveBeenCalledWith({ page: 0, keyword: '龙虾' });
  });

  it('onSearch 失败时走 handleError', async () => {
    mockLoadProducts.mockRejectedValueOnce(new Error('net'));
    await ctx.onSearch();
    expect(ctx.data.isError).toBe(true);
  });

  it('onClearSearch 清空关键词并重新加载', () => {
    ctx.data.searchKeyword = '龙虾';
    ctx.onClearSearch();
    expect(ctx.data.searchKeyword).toBe('');
    expect(mockLoadProducts).toHaveBeenCalledWith({ page: 0, keyword: '' });
  });

  it('onHotSearchTap 填充关键词并触发搜索', async () => {
    jest.useFakeTimers();
    ctx.onHotSearchTap({ currentTarget: { dataset: { keyword: '大闸蟹' } } });
    expect(ctx.data.searchKeyword).toBe('大闸蟹');
    expect(ctx.data.showHotSearch).toBe(false);
    jest.runAllTimers();
    jest.useRealTimers();
    await new Promise((r) => setTimeout(r, 0));
    expect(mockLoadProducts).toHaveBeenCalledWith({ page: 0, keyword: '大闸蟹' });
  });

  it('onShowHotSearch 重新展示热门搜索', () => {
    ctx.data.showHotSearch = false;
    ctx.onShowHotSearch();
    expect(ctx.data.showHotSearch).toBe(true);
  });
});
