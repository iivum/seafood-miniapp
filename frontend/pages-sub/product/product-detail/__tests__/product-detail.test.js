/**
 * product-detail.js tests —— mp-03 商品详情对齐 OD 原型
 * (openspec change mp-od-prototype-alignment,brief
 * `.superpowers/sdd/mp-od-3-product-detail-brief.md`)。
 *
 * 覆盖本次新增/修复的逻辑:
 *  1. onBack:悬浮顶栏返回,真实 wx.navigateBack()(非装饰)。
 *  2. onFavoriteTap:悬浮顶栏收藏,纯装饰 toast(参考 mp-01 onBellTap 模式;
 *     与底部 onToggleFavorite 是两个独立入口,互不影响)。
 *  3. onShareAppMessage:小程序原生分享生命周期,标题/图片取当前商品真实字段。
 *  4. onIncrement/onDecrement:数量 stepper 死绑定修复 —— 此前 wxml 引用
 *     bindtap="onIncrement"/"onDecrement" 但从未定义,+/− 完全不响应。
 *  5. onBuyNow 去重复:此前文件定义了两次 onBuyNow,后一份(switchTab 购物车)
 *     覆盖前一份(跳 mp-06),违反 openspec/specs/mini-program/spec.md:91-107
 *     "Direct buy from product detail"。修复后应跳订单确认页并带
 *     source=direct_buy,不再 switchTab 购物车。
 *     已知缺口(未做,见 report):spec 还要求"mp-06 不碰购物车 + items 只显示
 *     这一个商品",但后端 POST /api/orders 无 @RequestBody,只能从用户服务端
 *     购物车建单,不支持显式 items —— 真正合规需要后端加能力,超出本次前端
 *     授权范围。这里维持"addItem 合并进现有购物车"语义,不做隐式清购物车。
 *
 * 此前本文件从未被任何测试 require,不进 Jest 覆盖率统计;为避免 product-detail.js
 * 首次进入覆盖率统计后拖累全局阈值(CLAUDE.md:覆盖率全局 ≥80%),既有方法
 * (未改动逻辑)也一并补上用例 —— 与 pages/index/__tests__/index.test.js、
 * pages/category/__tests__/category.test.js 同一先例。
 */

global.wx = {
  showToast: jest.fn(),
  showModal: jest.fn(),
  navigateTo: jest.fn(),
  navigateBack: jest.fn(),
  switchTab: jest.fn(),
};

const mockApp = { globalData: { userInfo: { id: 'u-1' } } };
global.getApp = jest.fn(() => mockApp);

const mockGetById = jest.fn();
jest.mock('../../../../src/features/product/api', () => ({
  ProductAPI: { getById: (...args) => mockGetById(...args) },
}));

const mockAddItem = jest.fn().mockResolvedValue();
jest.mock('../../../../src/features/cart/store', () => ({
  cartStore: { addItem: (...args) => mockAddItem(...args) },
}));

const mockGetProductRecommendations = jest.fn().mockResolvedValue([]);
const mockRecordPurchase = jest.fn();
jest.mock('../../../../src/modules/recommendation/recommendation.js', () => ({
  recommendationModule: {
    getProductRecommendations: (...args) => mockGetProductRecommendations(...args),
    recordPurchase: (...args) => mockRecordPurchase(...args),
  },
}));

let pageConfig;
global.Page = (config) => { pageConfig = config; };
require('../product-detail.js');

const PRODUCT = { id: 'p-1', name: '波士顿龙虾', imageUrl: 'https://x/lobster.jpg', price: 288, stock: 5, category: '虾蟹' };

describe('product-detail (mp-03 商品详情)', () => {
  let ctx;

  beforeEach(() => {
    jest.clearAllMocks();
    mockApp.globalData.userInfo = { id: 'u-1' };
    mockGetById.mockResolvedValue(PRODUCT);
    mockAddItem.mockResolvedValue();
    mockGetProductRecommendations.mockResolvedValue([]);
    ctx = {
      data: JSON.parse(JSON.stringify(pageConfig.data)),
      onLoad: pageConfig.onLoad,
      fetchProductDetail: pageConfig.fetchProductDetail,
      fetchRecommendations: pageConfig.fetchRecommendations,
      onIncrement: pageConfig.onIncrement,
      onDecrement: pageConfig.onDecrement,
      onAddToCart: pageConfig.onAddToCart,
      onBuyNow: pageConfig.onBuyNow,
      onCustomerService: pageConfig.onCustomerService,
      onToggleFavorite: pageConfig.onToggleFavorite,
      goToHome: pageConfig.goToHome,
      goToCart: pageConfig.goToCart,
      onGoToProductDetail: pageConfig.onGoToProductDetail,
      onBack: pageConfig.onBack,
      onFavoriteTap: pageConfig.onFavoriteTap,
      onShareAppMessage: pageConfig.onShareAppMessage,
    };
    ctx.setData = jest.fn(function (patch) { Object.assign(this.data, patch); }.bind(ctx));
  });

  describe('data 默认值', () => {
    it('quantity 默认 1(mp-03 stepper 死绑定修复)', () => {
      expect(pageConfig.data.quantity).toBe(1);
    });
  });

  describe('onBack(mp-03 悬浮顶栏 §1)', () => {
    it('调用真实 wx.navigateBack()(非装饰)', () => {
      ctx.onBack();
      expect(wx.navigateBack).toHaveBeenCalledTimes(1);
    });
  });

  describe('onFavoriteTap(mp-03 悬浮顶栏 §1)', () => {
    it('展示"功能开发中" toast(纯装饰,参考 onBellTap 模式,不接后端)', () => {
      ctx.onFavoriteTap();
      expect(wx.showToast).toHaveBeenCalledWith(
        expect.objectContaining({ title: expect.stringContaining('开发中') })
      );
    });

    it('不修改 favorited 状态(与底部 onToggleFavorite 是独立入口)', () => {
      ctx.data.favorited = false;
      ctx.onFavoriteTap();
      expect(ctx.data.favorited).toBe(false);
    });
  });

  describe('onShareAppMessage(mp-03 悬浮顶栏 §1;小程序原生分享)', () => {
    it('已加载商品时返回商品名/图片/带 id 的 path', () => {
      ctx.data.product = PRODUCT;
      const result = ctx.onShareAppMessage();
      expect(result).toEqual({
        title: PRODUCT.name,
        imageUrl: PRODUCT.imageUrl,
        path: '/pages-sub/product/product-detail/product-detail?id=p-1',
      });
    });

    it('商品未加载时不抛错(返回空对象兜底)', () => {
      ctx.data.product = null;
      expect(() => ctx.onShareAppMessage()).not.toThrow();
      expect(ctx.onShareAppMessage()).toEqual({});
    });
  });

  describe('onIncrement/onDecrement(mp-03 数量 stepper 死绑定修复)', () => {
    it('onIncrement 在库存范围内 +1', () => {
      ctx.data.product = PRODUCT; // stock 5
      ctx.data.quantity = 1;
      ctx.onIncrement();
      expect(ctx.data.quantity).toBe(2);
    });

    it('onIncrement 到达 product.stock 后不再增加', () => {
      ctx.data.product = PRODUCT; // stock 5
      ctx.data.quantity = 5;
      ctx.onIncrement();
      expect(ctx.data.quantity).toBe(5);
    });

    it('onIncrement 在商品未加载(stock 视为 0)时不增加', () => {
      ctx.data.product = null;
      ctx.data.quantity = 1;
      ctx.onIncrement();
      expect(ctx.data.quantity).toBe(1);
    });

    it('onDecrement 在 >1 时 -1', () => {
      ctx.data.quantity = 3;
      ctx.onDecrement();
      expect(ctx.data.quantity).toBe(2);
    });

    it('onDecrement 到达 1 后不再减少', () => {
      ctx.data.quantity = 1;
      ctx.onDecrement();
      expect(ctx.data.quantity).toBe(1);
    });
  });

  describe('onBuyNow(mp-backend-contract-gaps D3b —— 直接购买改用显式 items 建单,不再合并进购物车)', () => {
    it('未登录时跳登录页,不加购/不跳转订单确认页', () => {
      mockApp.globalData.userInfo = null;
      ctx.data.product = PRODUCT;
      ctx.onBuyNow();
      expect(wx.navigateTo).toHaveBeenCalledWith(
        expect.objectContaining({ url: expect.stringContaining('/pages-sub/user/login/login') })
      );
      expect(mockAddItem).not.toHaveBeenCalled();
    });

    it('已登录 + 有库存:不再加购,直接带编码后的 items 跳订单确认页(source=direct_buy)', () => {
      ctx.data.product = PRODUCT;
      ctx.data.quantity = 2;
      ctx.onBuyNow();
      expect(mockAddItem).not.toHaveBeenCalled();
      const expectedItems = encodeURIComponent(JSON.stringify([{ productId: 'p-1', quantity: 2 }]));
      expect(wx.navigateTo).toHaveBeenCalledWith(
        expect.objectContaining({
          url: `/pages-sub/order/order-confirm/order-confirm?source=direct_buy&items=${expectedItems}`,
        })
      );
      expect(wx.switchTab).not.toHaveBeenCalled();
      expect(mockRecordPurchase).toHaveBeenCalledWith(PRODUCT);
    });

    it('quantity 缺失时兜底为 1', () => {
      ctx.data.product = PRODUCT;
      ctx.data.quantity = 0;
      ctx.onBuyNow();
      const expectedItems = encodeURIComponent(JSON.stringify([{ productId: 'p-1', quantity: 1 }]));
      expect(wx.navigateTo).toHaveBeenCalledWith(
        expect.objectContaining({
          url: `/pages-sub/order/order-confirm/order-confirm?source=direct_buy&items=${expectedItems}`,
        })
      );
    });

    it('库存为 0 时展示"已售罄" toast,不加购/不跳转', () => {
      ctx.data.product = { ...PRODUCT, stock: 0 };
      ctx.onBuyNow();
      expect(wx.showToast).toHaveBeenCalledWith(
        expect.objectContaining({ title: expect.stringContaining('售罄') })
      );
      expect(mockAddItem).not.toHaveBeenCalled();
      expect(wx.navigateTo).not.toHaveBeenCalledWith(
        expect.objectContaining({ url: expect.stringContaining('order-confirm') })
      );
    });

    it('商品未加载时不抛错、不跳转', () => {
      ctx.data.product = null;
      expect(() => ctx.onBuyNow()).not.toThrow();
      expect(mockAddItem).not.toHaveBeenCalled();
      expect(wx.navigateTo).not.toHaveBeenCalled();
    });
  });

  describe('既有逻辑(未改动,补测防止首次进覆盖率统计拖累全局阈值)', () => {
    it('onLoad 带 id 时拉取商品详情', () => {
      ctx.onLoad({ id: 'p-1' });
      expect(mockGetById).toHaveBeenCalledWith('p-1');
    });

    it('onLoad 不带 id 时不拉取', () => {
      ctx.onLoad({});
      expect(mockGetById).not.toHaveBeenCalled();
    });

    it('fetchProductDetail 成功后写入 product 并拉推荐', async () => {
      ctx.fetchProductDetail('p-1');
      await new Promise((r) => setTimeout(r, 0));
      expect(ctx.data.product).toEqual(PRODUCT);
      expect(ctx.data.isLoading).toBe(false);
      expect(mockGetProductRecommendations).toHaveBeenCalledWith(PRODUCT);
    });

    it('fetchProductDetail 失败(非 401)时提示 toast 并置 isError', async () => {
      mockGetById.mockRejectedValueOnce(new Error('挂了'));
      ctx.fetchProductDetail('bad-id');
      await new Promise((r) => setTimeout(r, 0));
      expect(ctx.data.isError).toBe(true);
      expect(ctx.data.errorMessage).toBe('挂了');
      expect(wx.showToast).toHaveBeenCalledWith(
        expect.objectContaining({ title: '加载商品失败' })
      );
    });

    it('fetchProductDetail 失败(401)时不弹 toast(交给鉴权守卫处理)', async () => {
      const err = new Error('未授权');
      err.statusCode = 401;
      mockGetById.mockRejectedValueOnce(err);
      ctx.fetchProductDetail('p-1');
      await new Promise((r) => setTimeout(r, 0));
      expect(ctx.data.isError).toBe(true);
      expect(wx.showToast).not.toHaveBeenCalled();
    });

    it('fetchRecommendations 只保留 products 非空的分组', async () => {
      mockGetProductRecommendations.mockResolvedValueOnce([
        { products: [{ id: 'a' }] },
        { products: [] },
      ]);
      ctx.fetchRecommendations(PRODUCT);
      await new Promise((r) => setTimeout(r, 0));
      expect(ctx.data.recommendations).toHaveLength(1);
    });

    it('fetchRecommendations 失败时静默(best-effort,不抛错)', async () => {
      mockGetProductRecommendations.mockRejectedValueOnce(new Error('net'));
      expect(() => ctx.fetchRecommendations(PRODUCT)).not.toThrow();
      await new Promise((r) => setTimeout(r, 0));
    });

    it('onAddToCart 无商品或已售罄时不加购', () => {
      ctx.data.product = { ...PRODUCT, stock: 0 };
      ctx.onAddToCart();
      expect(mockAddItem).not.toHaveBeenCalled();
    });

    it('onAddToCart 重复点击时(isAdding)不重复加购', () => {
      ctx.data.product = PRODUCT;
      ctx.data.isAdding = true;
      ctx.onAddToCart();
      expect(mockAddItem).not.toHaveBeenCalled();
    });

    it('onAddToCart 成功后 toast 反馈 + 记录购买', async () => {
      ctx.data.product = PRODUCT;
      ctx.data.quantity = 3;
      ctx.onAddToCart();
      await new Promise((r) => setTimeout(r, 0));
      expect(mockAddItem).toHaveBeenCalledWith('p-1', 3);
      expect(wx.showToast).toHaveBeenCalledWith(
        expect.objectContaining({ title: '已加入购物车' })
      );
      expect(mockRecordPurchase).toHaveBeenCalledWith(PRODUCT);
    });

    it('onAddToCart 失败时 toast 展示错误信息', async () => {
      mockAddItem.mockRejectedValueOnce(new Error('库存不足'));
      ctx.data.product = PRODUCT;
      ctx.onAddToCart();
      await new Promise((r) => setTimeout(r, 0));
      expect(wx.showToast).toHaveBeenCalledWith(
        expect.objectContaining({ title: '库存不足' })
      );
      expect(ctx.data.isAdding).toBe(false);
    });

    it('onCustomerService 展示客服 modal', () => {
      ctx.onCustomerService();
      expect(wx.showModal).toHaveBeenCalled();
    });

    it('onToggleFavorite 切换 favorited 并 toast(与顶栏 onFavoriteTap 独立)', () => {
      ctx.data.favorited = false;
      ctx.onToggleFavorite();
      expect(ctx.data.favorited).toBe(true);
      expect(wx.showToast).toHaveBeenCalledWith(
        expect.objectContaining({ title: '已收藏' })
      );
    });

    it('goToHome 切到首页 tab', () => {
      ctx.goToHome();
      expect(wx.switchTab).toHaveBeenCalledWith(
        expect.objectContaining({ url: '/pages/index/index' })
      );
    });

    it('goToCart 切到购物车 tab', () => {
      ctx.goToCart();
      expect(wx.switchTab).toHaveBeenCalledWith(
        expect.objectContaining({ url: '/pages/cart/cart' })
      );
    });

    it('onGoToProductDetail 用 dataset id 导航到详情页', () => {
      ctx.onGoToProductDetail({ currentTarget: { dataset: { id: 'p-2' } } });
      expect(wx.navigateTo).toHaveBeenCalledWith(
        expect.objectContaining({
          url: '/pages-sub/product/product-detail/product-detail?id=p-2',
        })
      );
    });
  });
});
