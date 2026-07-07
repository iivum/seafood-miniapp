global.wx = {
  showToast: jest.fn(),
  navigateTo: jest.fn(),
  stopPullDownRefresh: jest.fn(),
};

const mockProductViewList = jest.fn();
jest.mock('../../../../src/features/productView/api', () => ({
  ProductViewAPI: { list: (...a) => mockProductViewList(...a), record: jest.fn() },
}));

let pageConfig;
global.Page = (config) => {
  pageConfig = config;
};
require('../footprints-list.js');

describe('footprints-list', () => {
  let ctx;

  beforeEach(() => {
    jest.clearAllMocks();
    ctx = {
      data: JSON.parse(JSON.stringify(pageConfig.data)),
      setData: jest.fn(function (patch) {
        Object.assign(this.data, patch);
      }),
    };
    ctx.setData = ctx.setData.bind(ctx);
    for (const key of Object.keys(pageConfig)) {
      if (typeof pageConfig[key] === 'function') ctx[key] = pageConfig[key].bind(ctx);
    }
  });

  describe('onLoad / loadFootprints', () => {
    it('成功拉取足迹列表,写入 data.items(后端已按 viewedAt 降序返回,不再本地重排)', async () => {
      const items = [{ productId: 'p1', productName: '龙虾', price: 128, imageUrl: 'http://img', available: true, viewedAt: '2026-07-06T00:00:00Z' }];
      mockProductViewList.mockResolvedValueOnce(items);

      await ctx.onLoad();

      expect(ctx.data.items).toEqual(items);
      expect(ctx.data.isEmpty).toBe(false);
    });

    it('空列表时 isEmpty 为 true', async () => {
      mockProductViewList.mockResolvedValueOnce([]);

      await ctx.onLoad();

      expect(ctx.data.isEmpty).toBe(true);
    });

    it('拉取失败时 toast 提示', async () => {
      mockProductViewList.mockRejectedValueOnce(new Error('network'));

      await ctx.onLoad();

      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ icon: 'none' }));
    });
  });

  describe('onItemTap', () => {
    it('可用商品:跳转商品详情页', () => {
      const e = { currentTarget: { dataset: { id: 'p1', available: true } } };
      ctx.onItemTap(e);
      expect(wx.navigateTo).toHaveBeenCalledWith(
        expect.objectContaining({ url: '/pages-sub/product/product-detail/product-detail?id=p1' }),
      );
    });

    it('已下架商品:不跳转,toast 提示', () => {
      const e = { currentTarget: { dataset: { id: 'p-gone', available: false } } };
      ctx.onItemTap(e);
      expect(wx.navigateTo).not.toHaveBeenCalled();
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ icon: 'none' }));
    });
  });

  describe('onPullDownRefresh', () => {
    it('重新拉取后停止下拉动画', async () => {
      mockProductViewList.mockResolvedValueOnce([]);
      await ctx.onPullDownRefresh();
      expect(wx.stopPullDownRefresh).toHaveBeenCalled();
    });
  });
});
