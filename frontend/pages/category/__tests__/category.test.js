/**
 * category.js tests —— 锁定前后端分类契约 + 空载修复(C5 几何层 mp-02 RED→GREEN)。
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
};

// productList 模块:mock 掉真实网络,只验 loadProducts 被以正确 category 调用。
const mockLoadProducts = jest.fn().mockResolvedValue();
const mockModule = {
  loadProducts: mockLoadProducts,
  state: { products: [{ id: 'p1' }], isLoading: false, isError: false },
  isEmpty: false,
  hasNext: false,
  getErrorMessage: () => '',
  getEmptyStateMessage: () => '',
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
    ctx = {
      data: JSON.parse(JSON.stringify(pageConfig.data)),
      productModule: pageConfig.productModule,
      onLoad: pageConfig.onLoad,
      initCategories: pageConfig.initCategories,
      onCategoryTap: pageConfig.onCategoryTap,
      loadCategoryProducts: pageConfig.loadCategoryProducts,
      updateViewFromModule: pageConfig.updateViewFromModule,
      handleError: pageConfig.handleError,
    };
    ctx.setData = jest.fn(function (patch) { Object.assign(this.data, patch); }.bind(ctx));
    ctx.productModule = mockModule;
  });

  it('CATEGORIES 与后端 ProductCategory displayName 契约一致(中文 id,5 类)', () => {
    const ids = pageConfig.data.categories.map((c) => c.id);
    expect(ids).toEqual(BACKEND_CATEGORIES);
    // name 也用中文 displayName(sidebar 文案 = 分类名)
    pageConfig.data.categories.forEach((c) => {
      expect(BACKEND_CATEGORIES).toContain(c.name);
    });
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
});
