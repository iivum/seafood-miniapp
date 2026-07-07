global.wx = {
  showToast: jest.fn(),
  navigateTo: jest.fn(),
  stopPullDownRefresh: jest.fn(),
};

const mockFavoriteList = jest.fn();
const mockFavoriteRemove = jest.fn();
jest.mock('../../../../src/features/favorite/api', () => ({
  FavoriteAPI: {
    list: (...a) => mockFavoriteList(...a),
    remove: (...a) => mockFavoriteRemove(...a),
    add: jest.fn(),
  },
}));

let pageConfig;
global.Page = (config) => {
  pageConfig = config;
};
require('../favorites-list.js');

describe('favorites-list', () => {
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

  describe('onLoad / loadFavorites', () => {
    it('成功拉取收藏列表,写入 data.items', async () => {
      const items = [{ productId: 'p1', productName: '三文鱼', price: 58, imageUrl: 'http://img', available: true }];
      mockFavoriteList.mockResolvedValueOnce(items);

      await ctx.onLoad();

      expect(ctx.data.items).toEqual(items);
      expect(ctx.data.isEmpty).toBe(false);
    });

    it('空列表时 isEmpty 为 true', async () => {
      mockFavoriteList.mockResolvedValueOnce([]);

      await ctx.onLoad();

      expect(ctx.data.isEmpty).toBe(true);
    });

    it('拉取失败时 toast 提示', async () => {
      mockFavoriteList.mockRejectedValueOnce(new Error('network'));

      await ctx.onLoad();

      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ icon: 'none' }));
    });
  });

  describe('onRemoveFavorite', () => {
    it('调用 FavoriteAPI.remove 后重新拉取列表', async () => {
      mockFavoriteList.mockResolvedValueOnce([]);
      mockFavoriteRemove.mockResolvedValueOnce([]);
      const e = { currentTarget: { dataset: { id: 'p1' } } };

      await ctx.onRemoveFavorite(e);

      expect(mockFavoriteRemove).toHaveBeenCalledWith('p1');
      expect(mockFavoriteList).toHaveBeenCalled();
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
      mockFavoriteList.mockResolvedValueOnce([]);
      await ctx.onPullDownRefresh();
      expect(wx.stopPullDownRefresh).toHaveBeenCalled();
    });
  });
});
