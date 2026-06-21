/**
 * category.js tests —— 锁定前后端分类契约 + 空载修复(C5 几何层 mp-02 RED→GREEN)
 * + 覆盖页面生命周期/交互方法(下拉刷新 / 触底加载 / 跳详情 / 加购 / 重试 / 返回)。
 *
 * 后端 ProductCategory(sealed interface)只认 5 个中文 displayName:
 *   鱼类 / 虾蟹 / 贝类 / 软体 / 海藻;repo.findByCategory 精确匹配商品 category 字段。
 * 旧 bug:静态 CATEGORIES id 是英文(fish/shrimp/…)且含后端不存在的类(活鲜/冷冻/干货),
 *   loadProducts({category:'fish'}) 匹配 0 商品;且 onLoad 不自动选类 → grid 空载。
 */

global.wx = {
  showLoading: jest.fn(),
  hideLoading: jest.fn(),
  showToast: jest.fn(),
  navigateTo: jest.fn(),
  stopPullDownRefresh: jest.fn(),
};

// getApp —— goToDetail / addToCart 据 globalData.userInfo 判登录态。
const mockApp = { globalData: { userInfo: { id: 'u-1' } } };
global.getApp = jest.fn(() => mockApp);

// utils/cart.js —— addToCart 内 lazy require,mock 掉验调用。
jest.mock('../../../utils/cart.js', () => ({ addToCart: jest.fn() }));
const cartUtil = require('../../../utils/cart.js');

// productList 模块:mock 掉真实网络,只验交互被以正确参数调用。
const mockLoadProducts = jest.fn().mockResolvedValue();
const mockRefresh = jest.fn().mockResolvedValue();
const mockLoadNext = jest.fn().mockResolvedValue();
const mockModule = {
  loadProducts: mockLoadProducts,
  refreshProducts: mockRefresh,
  loadNextPage: mockLoadNext,
  state: { products: [{ id: 'p1' }], isLoading: false, isError: false },
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
require('../category.js');

// 后端 ProductCategory sealed interface 的 5 个 displayName(单一事实源)。
const BACKEND_CATEGORIES = ['鱼类', '虾蟹', '贝类', '软体', '海藻'];

describe('category', () => {
  let ctx;

  beforeEach(() => {
    jest.clearAllMocks();
    mockApp.globalData.userInfo = { id: 'u-1' };
    mockModule.isLoading = false;
    mockModule.hasNext = true;
    ctx = {
      data: JSON.parse(JSON.stringify(pageConfig.data)),
      productModule: mockModule,
      onLoad: pageConfig.onLoad,
      onShow: pageConfig.onShow,
      initCategories: pageConfig.initCategories,
      onCategoryTap: pageConfig.onCategoryTap,
      loadCategoryProducts: pageConfig.loadCategoryProducts,
      updateViewFromModule: pageConfig.updateViewFromModule,
      handleError: pageConfig.handleError,
      onPullDownRefresh: pageConfig.onPullDownRefresh,
      onReachBottom: pageConfig.onReachBottom,
      goToDetail: pageConfig.goToDetail,
      addToCart: pageConfig.addToCart,
      onRetry: pageConfig.onRetry,
      onBackToCategories: pageConfig.onBackToCategories,
    };
    ctx.setData = jest.fn(function (patch) { Object.assign(this.data, patch); }.bind(ctx));
  });

  it('CATEGORIES 与后端 ProductCategory displayName 契约一致(中文 id,5 类)', () => {
    const ids = pageConfig.data.categories.map((c) => c.id);
    expect(ids).toEqual(BACKEND_CATEGORIES);
    pageConfig.data.categories.forEach((c) => expect(BACKEND_CATEGORIES).toContain(c.name));
  });

  it('onLoad 自动选中首个分类并加载其商品(不再空载)', async () => {
    ctx.onLoad();
    await new Promise((r) => setTimeout(r, 0));
    expect(ctx.data.selectedCategory).toBe('鱼类');
    // selectedCategoryName 喂 cat-header__title 绑定(wxml 曾误绑 activeCategoryName → 标题空)
    expect(ctx.data.selectedCategoryName).toBe('鱼类');
    expect(mockLoadProducts).toHaveBeenCalledWith({ page: 0, category: '鱼类' });
  });

  it('onCategoryTap 用中文 displayName 作过滤值传给后端', async () => {
    ctx.onCategoryTap({ currentTarget: { dataset: { id: '虾蟹' } } });
    await new Promise((r) => setTimeout(r, 0));
    expect(ctx.data.selectedCategory).toBe('虾蟹');
    expect(mockLoadProducts).toHaveBeenCalledWith({ page: 0, category: '虾蟹' });
  });

  it('onShow 不抛错', () => {
    expect(() => ctx.onShow()).not.toThrow();
  });

  it('loadCategoryProducts 失败时走 handleError 置 isError', async () => {
    mockLoadProducts.mockRejectedValueOnce(new Error('net'));
    await ctx.loadCategoryProducts('鱼类');
    expect(ctx.data.isError).toBe(true);
    expect(wx.hideLoading).toHaveBeenCalled();
  });

  it('onPullDownRefresh 有选中分类时刷新并提示成功', async () => {
    ctx.data.selectedCategory = '鱼类';
    await ctx.onPullDownRefresh();
    expect(mockRefresh).toHaveBeenCalled();
    expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '刷新成功' }));
    expect(wx.stopPullDownRefresh).toHaveBeenCalled();
  });

  it('onPullDownRefresh 无选中分类时直接停止', async () => {
    ctx.data.selectedCategory = null;
    await ctx.onPullDownRefresh();
    expect(mockRefresh).not.toHaveBeenCalled();
    expect(wx.stopPullDownRefresh).toHaveBeenCalled();
  });

  it('onReachBottom 有下一页时加载更多', async () => {
    ctx.data.selectedCategory = '鱼类';
    await ctx.onReachBottom();
    expect(mockLoadNext).toHaveBeenCalled();
    expect(ctx.data.isLoadingMore).toBe(false);
  });

  it('onReachBottom 无下一页时早退', async () => {
    ctx.data.selectedCategory = '鱼类';
    mockModule.hasNext = false;
    await ctx.onReachBottom();
    expect(mockLoadNext).not.toHaveBeenCalled();
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

  it('addToCart 已登录加入购物车并提示', () => {
    ctx.addToCart({ currentTarget: { dataset: { product: { id: 'p1' } } } });
    expect(cartUtil.addToCart).toHaveBeenCalledWith({ id: 'p1' });
    expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '已加入购物车' }));
  });

  it('addToCart 未登录提示先登录', () => {
    mockApp.globalData.userInfo = null;
    ctx.addToCart({ currentTarget: { dataset: { product: { id: 'p1' } } } });
    expect(cartUtil.addToCart).not.toHaveBeenCalled();
    expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '请先登录' }));
  });

  it('onRetry 有选中分类时重新加载', () => {
    ctx.data.selectedCategory = '贝类';
    ctx.onRetry();
    expect(wx.showLoading).toHaveBeenCalled();
  });

  it('onBackToCategories 重置选中态', () => {
    ctx.data.selectedCategory = '鱼类';
    ctx.onBackToCategories();
    expect(ctx.data.selectedCategory).toBeNull();
    expect(ctx.data.products).toEqual([]);
  });
});
