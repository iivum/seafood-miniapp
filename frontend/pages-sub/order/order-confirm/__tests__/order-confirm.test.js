/**
 * order-confirm.js tests —— mp-06 OD 对齐(brief
 * `.superpowers/sdd/mp-od-5-order-confirm-brief.md`)。
 *
 * TDD 覆盖两类真 bug:
 *  1. 金额浮点数精度(brief 优先级最高)—— recalcAmounts 全程裸浮点数运算,
 *     没有在任何环节四舍五入到 2 位小数,底部结算金额会显示
 *     "¥404.94000000000005" 这种精度尾巴。复现数字组合取自 brief 给出的真实
 *     场景(145.11 × 2 + 124.72,满 100 减 10 优惠):
 *       subtotal(raw) = 145.11*2 + 124.72 = 414.94000000000005
 *       discount      = 10(满 100 减 10,整数,本身无精度问题)
 *       orderTotal(raw) = subtotal - discount = 404.94000000000005
 *     与 brief 描述的线上真实 bug 数值完全一致。
 *  2. 默认收货地址从不自动选中(brief §2,同 mp-04 cart.js
 *     autoSelectDefaultAddress 同类问题第二次出现,按 brief 要求各自维护一份
 *     实现,不跨文件抽公共函数)—— onLoad 时 selectedAddress 仍是 null,只有
 *     用户手动跳地址选择页选完回来才有值。
 *
 * 其余用例(既有行为,补覆盖率 —— 本文件此前完全没有测试,首次补齐时顺带覆盖
 * 全量既有行为,同 cart.test.js 先例)。
 */

// Mock wx global
global.wx = {
  showToast: jest.fn(),
  showLoading: jest.fn(),
  hideLoading: jest.fn(),
  navigateTo: jest.fn(),
  navigateBack: jest.fn(),
  redirectTo: jest.fn(),
};

// Mock getApp / getCurrentPages
const mockApp = { globalData: { userInfo: { id: 'user-1', nickname: 'Test' } } };
global.getApp = jest.fn(() => mockApp);
global.getCurrentPages = jest.fn(() => [{}]);

// Mock request —— 与 cart.js / address-list.js 同款用法(§2 brief 要求"不要
// 重新发明")。
const mockRequest = jest.fn().mockResolvedValue([]);
jest.mock('../../../../utils/request.js', () => ({ request: mockRequest, authRequest: jest.fn() }));

// Mock orderStore / cartStore / paymentModule
const mockOrderStore = {
  loadById: jest.fn(),
  placeOrder: jest.fn(),
  placeDirectBuyOrder: jest.fn(),
};
jest.mock('../../../../src/features/order/store', () => ({ orderStore: mockOrderStore }));

const mockCartStore = {
  refresh: jest.fn(),
};
jest.mock('../../../../src/features/cart/store', () => ({ cartStore: mockCartStore }));

// mp-backend-contract-gaps D3b:mp-03 立即购买预览需要逐个 ProductAPI.getById
// 补 name/price/imageUrl。
const mockProductGetById = jest.fn();
jest.mock('../../../../src/features/product/api', () => ({
  ProductAPI: { getById: (...args) => mockProductGetById(...args) },
}));

const mockPaymentModule = {
  requestPayment: jest.fn(),
};
jest.mock('../../../../src/modules/payment/payment.js', () => ({ paymentModule: mockPaymentModule }));

// Capture Page config
let pageConfig;
global.Page = (config) => {
  pageConfig = config;
};

// onSubmitOrder/initiatePayment 内部 promise 链没有 return(既有实现,非本轮
// 改动范围),测试里用多次 microtask flush 等它们跑完,而不是链式 .then()
// 返回值(那是 undefined)。
function flushPromises(times = 5) {
  let p = Promise.resolve();
  for (let i = 0; i < times; i++) {
    p = p.then(() => Promise.resolve());
  }
  return p;
}

require('../order-confirm.js');

describe('order-confirm', () => {
  let ctx;

  const makeCtx = () => {
    const c = {
      data: JSON.parse(JSON.stringify(pageConfig.data)),
    };
    c.setData = jest.fn(function (patch) {
      Object.assign(c.data, patch);
    });
    Object.keys(pageConfig).forEach((key) => {
      if (typeof pageConfig[key] === 'function' && key !== 'data') {
        c[key] = pageConfig[key].bind(c);
      }
    });
    return c;
  };

  beforeEach(() => {
    jest.clearAllMocks();
    mockRequest.mockResolvedValue([]);
    mockCartStore.refresh.mockResolvedValue({ items: [] });
    mockApp.globalData.userInfo = { id: 'user-1', nickname: 'Test' };
    ctx = makeCtx();
  });

  // ===== mp-backend-contract-gaps D3b:mp-03 立即购买 → mp-06 直接建单 =====
  describe('onLoad(source=direct_buy,mp-backend-contract-gaps D3b)', () => {
    const encodedItems = encodeURIComponent(
      JSON.stringify([{ productId: 'p1', quantity: 2 }]),
    );

    it('从编码后的 items 拉商品详情渲染,不调用 cartStore.refresh()', () => {
      mockProductGetById.mockResolvedValue({ name: '龙虾', price: 100, imageUrl: 'x.jpg' });
      mockRequest.mockResolvedValue([]);

      ctx.onLoad({ source: 'direct_buy', items: encodedItems });

      return flushPromises().then(() => {
        expect(mockProductGetById).toHaveBeenCalledWith('p1');
        expect(mockCartStore.refresh).not.toHaveBeenCalled();
        expect(ctx.data.cartItems).toEqual([
          { id: 'p1', name: '龙虾', price: 100, quantity: 2, imageUrl: 'x.jpg' },
        ]);
        expect(ctx.data.order.items).toEqual([
          { productId: 'p1', productName: '龙虾', unitPrice: 100, quantity: 2 },
        ]);
        expect(ctx.data.directBuyItems).toEqual([{ productId: 'p1', quantity: 2 }]);
        // recalcAmounts 被调用:100 * 2 = 200(满 100 减 10,无运费)= 190
        expect(ctx.data.subtotal).toBe(200);
        expect(ctx.data.orderTotal).toBe(190);
        expect(ctx.data.itemCount).toBe(1);
      });
    });

    it('直接购买同样触发默认地址自动选中(与购物车无关)', () => {
      mockProductGetById.mockResolvedValue({ name: '龙虾', price: 100, imageUrl: 'x.jpg' });
      mockRequest.mockResolvedValue([
        { id: 'a1', name: '张三', isDefault: false },
        { id: 'a2', name: '李四', isDefault: true },
      ]);

      ctx.onLoad({ source: 'direct_buy', items: encodedItems });

      return flushPromises().then(() => {
        expect(mockRequest).toHaveBeenCalledWith({ url: '/addresses', needAuth: true });
        expect(ctx.data.selectedAddress).toEqual({ id: 'a2', name: '李四', isDefault: true });
      });
    });

    it('ProductAPI.getById 失败时 best-effort,order 保持 null,不抛异常', () => {
      mockProductGetById.mockRejectedValue(new Error('network down'));
      expect(() => ctx.onLoad({ source: 'direct_buy', items: encodedItems })).not.toThrow();
      return flushPromises().then(() => {
        expect(ctx.data.order).toBeNull();
      });
    });
  });

  describe('recalcAmounts 金额精度修复(brief 优先级最高的真 bug)', () => {
    it('裸浮点数运算(145.11×2 + 124.72,满 100 减 10)此前会产生精度尾巴,修复后 subtotal/orderTotal 精确到 2 位小数', () => {
      ctx.data.cartItems = [
        { id: 'p1', price: 145.11, quantity: 2 },
        { id: 'p2', price: 124.72, quantity: 1 },
      ];
      ctx.data.shippingMethod = 'FREE';

      ctx.recalcAmounts();

      // 裸浮点数运算下 145.11*2 + 124.72 === 414.94000000000005
      // 精度尾巴不应该漏到 data 里。
      expect(ctx.data.subtotal).toBe(414.94);
      expect(ctx.data.discount).toBe(10);
      // 与 brief 描述的线上真实 bug 数值(¥404.94000000000005)完全对应。
      expect(ctx.data.orderTotal).toBe(404.94);
      // 双重保险:toFixed(2) 后不应该出现多余小数位（防止 -0 / 精度残留）。
      expect(ctx.data.orderTotal.toFixed(2)).toBe('404.94');
      expect(ctx.data.subtotal.toFixed(2)).toBe('414.94');
    });

    it('叠加顺丰运费(12 元)后 orderTotal 仍精确到 2 位小数', () => {
      ctx.data.cartItems = [
        { id: 'p1', price: 145.11, quantity: 2 },
        { id: 'p2', price: 124.72, quantity: 1 },
      ];
      ctx.data.shippingMethod = 'SF';

      ctx.recalcAmounts();

      expect(ctx.data.subtotal).toBe(414.94);
      expect(ctx.data.shippingFee).toBe(12);
      expect(ctx.data.discount).toBe(10);
      expect(ctx.data.orderTotal).toBe(416.94);
    });

    it('购物车为空时金额全部归零,不抛异常', () => {
      ctx.data.cartItems = [];
      ctx.recalcAmounts();
      expect(ctx.data.subtotal).toBe(0);
      expect(ctx.data.discount).toBe(0);
      expect(ctx.data.orderTotal).toBe(0);
    });
  });

  describe('recalcAmounts 商品件数统计("共 N 件",真实数据)', () => {
    it('itemCount 对应商品种类数(items.length),不是数量总和', () => {
      ctx.data.cartItems = [
        { id: 'p1', price: 100, quantity: 1 },
        { id: 'p2', price: 50, quantity: 2 },
        { id: 'p3', price: 30, quantity: 1 },
      ];
      ctx.recalcAmounts();
      expect(ctx.data.itemCount).toBe(3);
    });

    it('购物车为空时 itemCount 为 0', () => {
      ctx.data.cartItems = [];
      ctx.recalcAmounts();
      expect(ctx.data.itemCount).toBe(0);
    });
  });

  describe('onLoad 默认地址自动选中(brief §2 真 bug)', () => {
    it('selectedAddress 为 null 时查地址列表,自动选中 isDefault:true 的一条', () => {
      mockRequest.mockResolvedValue([
        { id: 'a1', name: '张三', phone: '13800000000', isDefault: false },
        { id: 'a2', name: '李四', phone: '13900000000', isDefault: true },
      ]);
      mockCartStore.refresh.mockResolvedValue({ items: [] });

      ctx.onLoad({});

      return Promise.resolve()
        .then(() => Promise.resolve())
        .then(() => {
          expect(mockRequest).toHaveBeenCalledWith({ url: '/addresses', needAuth: true });
          expect(ctx.data.selectedAddress).toEqual({
            id: 'a2',
            name: '李四',
            phone: '13900000000',
            isDefault: true,
          });
        });
    });

    it('没有默认地址时 selectedAddress 保持 null(维持既有空态,不是错误)', () => {
      mockRequest.mockResolvedValue([{ id: 'a1', name: '张三', phone: '13800000000', isDefault: false }]);
      ctx.onLoad({});
      return Promise.resolve()
        .then(() => Promise.resolve())
        .then(() => {
          expect(ctx.data.selectedAddress).toBeNull();
        });
    });

    it('地址列表为空数组时 selectedAddress 保持 null', () => {
      mockRequest.mockResolvedValue([]);
      ctx.onLoad({});
      return Promise.resolve()
        .then(() => Promise.resolve())
        .then(() => {
          expect(ctx.data.selectedAddress).toBeNull();
        });
    });

    it('selectedAddress 已有值时不重新查询地址列表(不覆盖用户已选)', () => {
      ctx.data.selectedAddress = { id: 'a9', name: '已选地址' };
      ctx.autoSelectDefaultAddress();
      expect(mockRequest).not.toHaveBeenCalled();
      expect(ctx.data.selectedAddress).toEqual({ id: 'a9', name: '已选地址' });
    });

    it('查询地址列表失败时不抛异常,selectedAddress 保持 null', () => {
      mockRequest.mockRejectedValue(new Error('network error'));
      ctx.onLoad({});
      return Promise.resolve()
        .then(() => Promise.resolve())
        .then(() => {
          expect(ctx.data.selectedAddress).toBeNull();
        });
    });

    it('携带 options.id(查看已下单订单)时不查默认地址(应展示订单实际收货地址)', () => {
      mockOrderStore.loadById.mockResolvedValue({ id: 'o1', items: [] });
      ctx.onLoad({ id: 'o1' });
      expect(mockRequest).not.toHaveBeenCalled();
    });
  });

  describe('goBack(顶部标题栏返回按钮接线)', () => {
    it('调用 wx.navigateBack', () => {
      ctx.goBack();
      expect(wx.navigateBack).toHaveBeenCalled();
    });
  });

  describe('onShow(既有行为,补覆盖率)', () => {
    it('order 已存在时直接 return,不处理 selectedAddressFromList', () => {
      ctx.data.order = { id: 'o1', items: [] };
      const currentPage = { selectedAddressFromList: { id: 'a1' } };
      global.getCurrentPages = jest.fn(() => [currentPage]);
      ctx.onShow();
      expect(ctx.data.selectedAddress).toBeNull();
      expect(currentPage.selectedAddressFromList).toEqual({ id: 'a1' });
    });

    it('order 不存在且有 selectedAddressFromList 回传时,setData 并清空回传字段', () => {
      ctx.data.order = null;
      const currentPage = { selectedAddressFromList: { id: 'a1', name: '张三' } };
      global.getCurrentPages = jest.fn(() => [currentPage]);
      ctx.onShow();
      expect(ctx.data.selectedAddress).toEqual({ id: 'a1', name: '张三' });
      expect(currentPage.selectedAddressFromList).toBeNull();
    });

    it('order 不存在且无 selectedAddressFromList 回传时,不做任何改动', () => {
      ctx.data.order = null;
      global.getCurrentPages = jest.fn(() => [{}]);
      ctx.onShow();
      expect(ctx.data.selectedAddress).toBeNull();
    });
  });

  describe('loadExistingOrder(既有行为,补覆盖率)', () => {
    it('成功时 setData order', () => {
      mockOrderStore.loadById.mockResolvedValue({ id: 'o1', items: [{ productId: 'p1' }] });
      ctx.loadExistingOrder('o1');
      return flushPromises().then(() => {
        expect(ctx.data.order).toEqual({ id: 'o1', items: [{ productId: 'p1' }] });
      });
    });

    it('失败且有 message 时 setData errorMessage', () => {
      mockOrderStore.loadById.mockRejectedValue(new Error('订单不存在'));
      ctx.loadExistingOrder('o1');
      return flushPromises().then(() => {
        expect(ctx.data.errorMessage).toBe('订单不存在');
      });
    });

    it('失败且无 message 时使用默认文案', () => {
      mockOrderStore.loadById.mockRejectedValue('plain error');
      ctx.loadExistingOrder('o1');
      return flushPromises().then(() => {
        expect(ctx.data.errorMessage).toBe('加载订单失败');
      });
    });
  });

  describe('refreshCartPreview(既有行为,补覆盖率)', () => {
    it('成功时把 cart.items 映射为 order/cartItems 并触发 recalcAmounts', () => {
      mockCartStore.refresh.mockResolvedValue({
        items: [
          { productId: 'p1', productName: '龙虾', unitPrice: 100, quantity: 1, imageUrl: 'x.jpg' },
          { productId: 'p2', quantity: 2 },
        ],
      });
      ctx.refreshCartPreview();
      return flushPromises().then(() => {
        expect(ctx.data.cartItems).toEqual([
          { id: 'p1', name: '龙虾', price: 100, quantity: 1, imageUrl: 'x.jpg' },
          { id: 'p2', name: 'p2', price: 0, quantity: 2, imageUrl: '' },
        ]);
        expect(ctx.data.order.items).toEqual([
          { productId: 'p1', productName: '龙虾', unitPrice: 100, quantity: 1 },
          { productId: 'p2', productName: 'p2', unitPrice: 0, quantity: 2 },
        ]);
        expect(ctx.data.itemCount).toBe(2);
      });
    });

    it('失败时 best-effort 保留 order 为 null,不抛异常', () => {
      mockCartStore.refresh.mockRejectedValue(new Error('network down'));
      ctx.refreshCartPreview();
      return flushPromises().then(() => {
        expect(ctx.data.order).toBeNull();
      });
    });
  });

  describe('selectAddress(既有行为,补覆盖率)', () => {
    it('未登录时 toast 提示,不跳转', () => {
      mockApp.globalData.userInfo = null;
      ctx.selectAddress();
      expect(wx.showToast).toHaveBeenCalledWith({ title: '请先登录', icon: 'none' });
      expect(wx.navigateTo).not.toHaveBeenCalled();
    });

    it('已登录且未选地址时跳转带空 selectedAddress', () => {
      ctx.data.selectedAddress = null;
      ctx.selectAddress();
      expect(wx.navigateTo).toHaveBeenCalledWith({
        url: '/pages-sub/user/address/address-list?selectMode=true&selectedAddress=',
      });
    });

    it('已登录且已选地址时跳转带编码后的 selectedAddress', () => {
      ctx.data.selectedAddress = { id: 'a1' };
      ctx.selectAddress();
      expect(wx.navigateTo).toHaveBeenCalledWith({
        url:
          '/pages-sub/user/address/address-list?selectMode=true&selectedAddress=' +
          encodeURIComponent(JSON.stringify({ id: 'a1' })),
      });
    });
  });

  describe('onSelectShipping(既有行为,补覆盖率)', () => {
    it('切换配送方式后更新 shippingFee 并重新算金额', () => {
      ctx.data.cartItems = [{ id: 'p1', price: 100, quantity: 1 }];
      ctx.onSelectShipping({ currentTarget: { dataset: { method: 'SF' } } });
      expect(ctx.data.shippingMethod).toBe('SF');
      expect(ctx.data.shippingFee).toBe(12);
      // subtotal=100 → 满 100 减 10;orderTotal = 100 + 12 - 10 = 102
      expect(ctx.data.orderTotal).toBe(102);
    });

    it('dataset.method 缺失时兜底为 FREE', () => {
      ctx.onSelectShipping({ currentTarget: { dataset: {} } });
      expect(ctx.data.shippingMethod).toBe('FREE');
      expect(ctx.data.shippingFee).toBe(0);
    });
  });

  describe('onRemarkInput(既有行为,补覆盖率)', () => {
    it('更新 remark', () => {
      ctx.onRemarkInput({ detail: { value: '不要冰袋' } });
      expect(ctx.data.remark).toBe('不要冰袋');
    });

    it('value 缺失时兜底为空字符串', () => {
      ctx.onRemarkInput({ detail: {} });
      expect(ctx.data.remark).toBe('');
    });
  });

  describe('onSubmitOrder(既有行为,补覆盖率)', () => {
    it('未登录时跳登录页并带 redirect', () => {
      mockApp.globalData.userInfo = null;
      ctx.onSubmitOrder();
      expect(wx.navigateTo).toHaveBeenCalledWith({
        url:
          '/pages-sub/user/login/login?redirect=' +
          encodeURIComponent('/pages-sub/order/order-confirm/order-confirm'),
      });
    });

    it('购物车为空(order 无 items)时 toast 提示,不创建订单', () => {
      ctx.data.order = { items: [] };
      ctx.onSubmitOrder();
      expect(wx.showToast).toHaveBeenCalledWith({ title: '购物车为空', icon: 'none' });
      expect(mockOrderStore.placeOrder).not.toHaveBeenCalled();
    });

    it('order 为 null 时 toast 提示,不创建订单', () => {
      ctx.data.order = null;
      ctx.onSubmitOrder();
      expect(wx.showToast).toHaveBeenCalledWith({ title: '购物车为空', icon: 'none' });
    });

    it('未选地址时 toast 提示,不创建订单', () => {
      ctx.data.order = { items: [{ productId: 'p1' }] };
      ctx.data.selectedAddress = null;
      ctx.onSubmitOrder();
      expect(wx.showToast).toHaveBeenCalledWith({ title: '请选择收货地址', icon: 'none' });
      expect(mockOrderStore.placeOrder).not.toHaveBeenCalled();
    });

    it('创建订单成功时进入支付流程', () => {
      ctx.data.order = { items: [{ productId: 'p1' }] };
      ctx.data.selectedAddress = { id: 'addr-1' };
      ctx.data.remark = '轻拿轻放';
      mockOrderStore.placeOrder.mockResolvedValue({ id: 'order-1', totalAmount: 100 });
      mockPaymentModule.requestPayment.mockResolvedValue({
        isSuccess: () => true,
        isCancelled: () => false,
      });
      ctx.onSubmitOrder();
      return flushPromises().then(() => {
        expect(mockOrderStore.placeOrder).toHaveBeenCalledWith({ addressId: 'addr-1', remark: '轻拿轻放' });
        expect(ctx.data.isCreating).toBe(false);
        expect(mockPaymentModule.requestPayment).toHaveBeenCalledWith('order-1', 100);
      });
    });

    it('创建订单失败时 toast 错误信息', () => {
      ctx.data.order = { items: [{ productId: 'p1' }] };
      ctx.data.selectedAddress = { id: 'addr-1' };
      mockOrderStore.placeOrder.mockRejectedValue(new Error('库存不足'));
      ctx.onSubmitOrder();
      return flushPromises().then(() => {
        expect(ctx.data.isCreating).toBe(false);
        expect(wx.showToast).toHaveBeenCalledWith({ title: '库存不足', icon: 'none' });
      });
    });

    it('创建订单失败且无 message 时使用默认文案', () => {
      ctx.data.order = { items: [{ productId: 'p1' }] };
      ctx.data.selectedAddress = { id: 'addr-1' };
      mockOrderStore.placeOrder.mockRejectedValue('plain error');
      ctx.onSubmitOrder();
      return flushPromises().then(() => {
        expect(wx.showToast).toHaveBeenCalledWith({ title: '创建订单失败', icon: 'none' });
      });
    });

    // ===== mp-backend-contract-gaps D3b:direct-buy 模式改走 placeDirectBuyOrder =====
    it('directBuyItems 非空时调用 orderStore.placeDirectBuyOrder(带 items),不调用 placeOrder', () => {
      ctx.data.order = { items: [{ productId: 'p1', productName: '龙虾', unitPrice: 100, quantity: 2 }] };
      ctx.data.selectedAddress = { id: 'addr-1' };
      ctx.data.remark = '轻拿轻放';
      ctx.data.directBuyItems = [{ productId: 'p1', quantity: 2 }];
      mockOrderStore.placeDirectBuyOrder.mockResolvedValue({ id: 'order-1', totalAmount: 190 });
      mockPaymentModule.requestPayment.mockResolvedValue({
        isSuccess: () => true,
        isCancelled: () => false,
      });

      ctx.onSubmitOrder();

      return flushPromises().then(() => {
        expect(mockOrderStore.placeDirectBuyOrder).toHaveBeenCalledWith({
          items: [{ productId: 'p1', quantity: 2 }],
          addressId: 'addr-1',
          remark: '轻拿轻放',
        });
        expect(mockOrderStore.placeOrder).not.toHaveBeenCalled();
        expect(ctx.data.isCreating).toBe(false);
        expect(mockPaymentModule.requestPayment).toHaveBeenCalledWith('order-1', 190);
      });
    });

    it('directBuyItems 为空数组时仍走 placeOrder(购物车结算,未改动)', () => {
      ctx.data.order = { items: [{ productId: 'p1' }] };
      ctx.data.selectedAddress = { id: 'addr-1' };
      ctx.data.directBuyItems = [];
      mockOrderStore.placeOrder.mockResolvedValue({ id: 'order-1', totalAmount: 100 });
      mockPaymentModule.requestPayment.mockResolvedValue({
        isSuccess: () => true,
        isCancelled: () => false,
      });

      ctx.onSubmitOrder();

      return flushPromises().then(() => {
        expect(mockOrderStore.placeOrder).toHaveBeenCalledWith({ addressId: 'addr-1', remark: undefined });
        expect(mockOrderStore.placeDirectBuyOrder).not.toHaveBeenCalled();
      });
    });
  });

  describe('initiatePayment(既有行为,补覆盖率)', () => {
    beforeEach(() => {
      jest.useFakeTimers();
    });

    afterEach(() => {
      jest.useRealTimers();
    });

    it('支付成功时 toast 成功并延迟跳转订单列表', () => {
      mockPaymentModule.requestPayment.mockResolvedValue({
        isSuccess: () => true,
        isCancelled: () => false,
      });
      ctx.initiatePayment({ id: 'order-1', totalAmount: 100 });
      return flushPromises().then(() => {
        expect(ctx.data.isPaying).toBe(false);
        expect(wx.showToast).toHaveBeenCalledWith({ title: '支付成功', icon: 'success' });
        jest.advanceTimersByTime(1500);
        expect(wx.redirectTo).toHaveBeenCalledWith({ url: '/pages-sub/order/order-list/order-list' });
      });
    });

    it('取消支付时 toast 取消提示', () => {
      mockPaymentModule.requestPayment.mockResolvedValue({
        isSuccess: () => false,
        isCancelled: () => true,
      });
      ctx.initiatePayment({ id: 'order-1', totalAmount: 100 });
      return flushPromises().then(() => {
        expect(wx.showToast).toHaveBeenCalledWith({ title: '已取消支付', icon: 'none' });
      });
    });

    it('支付失败(result 非成功非取消)时 toast errorMessage', () => {
      mockPaymentModule.requestPayment.mockResolvedValue({
        isSuccess: () => false,
        isCancelled: () => false,
        errorMessage: '余额不足',
      });
      ctx.initiatePayment({ id: 'order-1', totalAmount: 100 });
      return flushPromises().then(() => {
        expect(wx.showToast).toHaveBeenCalledWith({ title: '余额不足', icon: 'none' });
      });
    });

    it('支付失败且无 errorMessage 时使用默认文案', () => {
      mockPaymentModule.requestPayment.mockResolvedValue({
        isSuccess: () => false,
        isCancelled: () => false,
      });
      ctx.initiatePayment({ id: 'order-1', totalAmount: 100 });
      return flushPromises().then(() => {
        expect(wx.showToast).toHaveBeenCalledWith({ title: '支付失败', icon: 'none' });
      });
    });

    it('requestPayment 抛异常时 toast 支付失败并延迟跳转', () => {
      mockPaymentModule.requestPayment.mockRejectedValue(new Error('network error'));
      ctx.initiatePayment({ id: 'order-1', totalAmount: 100 });
      return flushPromises().then(() => {
        expect(ctx.data.isPaying).toBe(false);
        expect(wx.showToast).toHaveBeenCalledWith({ title: '支付失败', icon: 'none' });
        jest.advanceTimersByTime(1500);
        expect(wx.redirectTo).toHaveBeenCalledWith({ url: '/pages-sub/order/order-list/order-list' });
      });
    });
  });
});
