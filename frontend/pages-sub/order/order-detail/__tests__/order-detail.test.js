/**
 * order-detail.js 测试(此前零覆盖 —— mp-od-prototype-alignment mp-09 接线
 * OrderActionRow/OrderTrackingTimeline 时补齐,brief
 * `.superpowers/sdd/mp-od-8-order-detail-brief.md`)。
 *
 * 核心回归锁:onActionTap 事件契约。OrderActionRow 组件 triggerEvent('action', {id})
 * 的 detail 只带 action 类型字符串(如 'cancelOrder')。order-detail 页面本身持有
 * 当前订单(this.data.order.id),不需要像 order-list 卡片列表那样从 wxml data-id
 * 再取订单 id ——同 mp-08 order-list.js 修复后的正确写法,这次从一开始就写对
 * (不重蹈"从 e.detail 解构 {id, action} 两者都取错"的覆辙)。
 */

global.wx = {
  showLoading: jest.fn(),
  hideLoading: jest.fn(),
  showToast: jest.fn(),
  showModal: jest.fn(),
  navigateTo: jest.fn(),
  navigateBack: jest.fn(),
  switchTab: jest.fn(),
  setClipboardData: jest.fn(),
};

const mockRequest = jest.fn();
jest.mock('../../../../utils/request.js', () => ({
  request: (...a) => mockRequest(...a),
}));

const mockPay = jest.fn().mockResolvedValue({});
const mockRemindShip = jest.fn().mockResolvedValue({});
const mockRebuy = jest.fn().mockResolvedValue([{ productId: 'p1', quantity: 2 }]);
jest.mock('../../../../src/features/order/api', () => ({
  OrderAPI: {
    pay: (...a) => mockPay(...a),
    remindShip: (...a) => mockRemindShip(...a),
    rebuy: (...a) => mockRebuy(...a),
  },
}));

const mockCancel = jest.fn().mockResolvedValue({ id: 'order-detail-1', status: 'CANCELLED' });
jest.mock('../../../../src/features/order/store', () => ({
  orderStore: { cancel: (...a) => mockCancel(...a) },
}));

const mockCartAdd = jest.fn().mockResolvedValue({});
jest.mock('../../../../src/features/cart/store', () => ({
  cartStore: { add: (...a) => mockCartAdd(...a) },
}));

let pageConfig;
global.Page = (config) => {
  pageConfig = config;
};
require('../order-detail.js');

/** helper:showModal 同时支持 order-list 惯例的 callback 风格(confirmThenCancel/
 * confirmThenDelete)和 order-detail 原有的 promise 风格(applyRefund/confirmReceive)。 */
function mockShowModal(confirm) {
  wx.showModal.mockImplementation((opts) => {
    if (opts && typeof opts.success === 'function') opts.success({ confirm });
    return Promise.resolve({ confirm });
  });
}

describe('order-detail', () => {
  let ctx;

  beforeEach(() => {
    jest.clearAllMocks();
    mockRequest.mockReset();
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

  describe('onLoad / loadOrder', () => {
    it('无 id:走 handleError(400),不发请求', () => {
      ctx.handleError = jest.fn();
      ctx.onLoad({});
      expect(ctx.handleError).toHaveBeenCalledWith(
        expect.objectContaining({ statusCode: 400 })
      );
      expect(mockRequest).not.toHaveBeenCalled();
    });

    it('有 id:拉订单成功,写入 order + statusBanner,不再有 timeline 字段', async () => {
      const order = { id: 'o1', status: 'PENDING', items: [], createdAt: '2026-06-01T10:00:00Z' };
      mockRequest.mockResolvedValueOnce(order);
      await ctx.onLoad({ id: 'o1' });
      expect(ctx.data.order).toEqual(order);
      expect(ctx.data.statusBanner).toBeDefined();
      expect(ctx.data.timeline).toBeUndefined();
      expect(ctx.data.isLoading).toBe(false);
    });

    it('拉订单失败:走 handleError', async () => {
      mockRequest.mockRejectedValueOnce({ statusCode: 500, message: '服务器错误' });
      await ctx.onLoad({ id: 'o1' });
      expect(ctx.data.isError).toBe(true);
      expect(ctx.data.errorMessage).toBe('服务器错误');
    });
  });

  describe('handleError', () => {
    it('404:弹窗后返回上一页', () => {
      wx.showModal.mockImplementation((opts) => opts.success());
      ctx.handleError({ statusCode: 404 });
      expect(wx.showModal).toHaveBeenCalledWith(expect.objectContaining({ title: '订单不存在' }));
      expect(wx.navigateBack).toHaveBeenCalled();
    });

    it('401/403:弹窗后跳登录', () => {
      wx.showModal.mockImplementation((opts) => opts.success());
      ctx.handleError({ statusCode: 401 });
      expect(wx.navigateTo).toHaveBeenCalledWith(
        expect.objectContaining({ url: '/pages-sub/user/login/login' })
      );
    });

    it('其它错误:setData 错误态', () => {
      ctx.handleError({ message: '未知错误' });
      expect(ctx.data.isError).toBe(true);
      expect(ctx.data.errorMessage).toBe('未知错误');
    });
  });

  describe('onRetry / onBack', () => {
    it('有 order 时 onRetry 重新拉取', () => {
      ctx.data.order = { id: 'o1' };
      ctx.loadOrder = jest.fn();
      ctx.onRetry();
      expect(ctx.loadOrder).toHaveBeenCalledWith('o1');
    });

    it('无 order 时 onRetry 不拉取', () => {
      ctx.data.order = null;
      ctx.loadOrder = jest.fn();
      ctx.onRetry();
      expect(ctx.loadOrder).not.toHaveBeenCalled();
    });

    it('onBack 返回上一页', () => {
      ctx.onBack();
      expect(wx.navigateBack).toHaveBeenCalled();
    });
  });

  describe('onActionTap(事件契约回归锁)', () => {
    it('从 e.detail.id 取 action 类型,直接用 this.data.order.id,不需要 dataset', () => {
      ctx.data.order = { id: 'order-detail-1', status: 'PENDING' };
      ctx.handleAction = jest.fn();
      const e = { detail: { id: 'cancelOrder' } };
      ctx.onActionTap(e);
      expect(ctx.handleAction).toHaveBeenCalledWith('cancelOrder');
    });
  });

  describe('handleAction 分发', () => {
    beforeEach(() => {
      ctx.data.order = { id: 'order-detail-1', status: 'PENDING', items: [] };
    });

    it('order 为空时直接返回,不抛错、不调用任何 API', async () => {
      ctx.data.order = null;
      await expect(ctx.handleAction('pay')).resolves.toBeUndefined();
      expect(mockPay).not.toHaveBeenCalled();
    });

    it('confirmReceipt 复用 confirmReceive(action id 与方法名不一致,须映射)', async () => {
      ctx.confirmReceive = jest.fn();
      await ctx.handleAction('confirmReceipt');
      expect(ctx.confirmReceive).toHaveBeenCalled();
    });

    it('requestRefund 和 afterSale 都复用 applyRefund', async () => {
      ctx.applyRefund = jest.fn();
      await ctx.handleAction('requestRefund');
      await ctx.handleAction('afterSale');
      expect(ctx.applyRefund).toHaveBeenCalledTimes(2);
    });

    it('viewTracking 复用 viewLogistics', async () => {
      ctx.viewLogistics = jest.fn();
      await ctx.handleAction('viewTracking');
      expect(ctx.viewLogistics).toHaveBeenCalled();
    });

    it('pay 触发 OrderAPI.pay,不落 default 分支', async () => {
      mockRequest.mockResolvedValueOnce({ id: 'order-detail-1', status: 'PAID' });
      await ctx.handleAction('pay');
      expect(mockPay).toHaveBeenCalledWith('order-detail-1');
      expect(wx.showToast).not.toHaveBeenCalledWith(expect.objectContaining({ title: '未知操作' }));
      // 成功后刷新详情(refreshOrder 内部走 utils/request.js)
      expect(mockRequest).toHaveBeenCalledWith(
        expect.objectContaining({ url: '/orders/order-detail-1' })
      );
    });

    it('cancelOrder 二次确认后触发 orderStore.cancel', async () => {
      mockShowModal(true);
      mockRequest.mockResolvedValueOnce({ id: 'order-detail-1', status: 'CANCELLED' });
      await ctx.handleAction('cancelOrder');
      expect(mockCancel).toHaveBeenCalledWith('order-detail-1', expect.any(String));
    });

    it('取消二次确认弹窗时不调用 orderStore.cancel', async () => {
      mockShowModal(false);
      await ctx.handleAction('cancelOrder');
      expect(mockCancel).not.toHaveBeenCalled();
    });

    it('remindShip 触发 OrderAPI.remindShip', async () => {
      mockRequest.mockResolvedValueOnce({ id: 'order-detail-1', status: 'PAID' });
      await ctx.handleAction('remindShip');
      expect(mockRemindShip).toHaveBeenCalledWith('order-detail-1');
    });

    it('reorder 触发 rebuy + 加购 + 跳购物车', async () => {
      jest.useFakeTimers();
      const p = ctx.handleAction('reorder');
      await Promise.resolve();
      await Promise.resolve();
      expect(mockRebuy).toHaveBeenCalledWith('order-detail-1');
      expect(mockCartAdd).toHaveBeenCalledWith('p1', 2);
      jest.runAllTimers();
      await p;
      expect(wx.switchTab).toHaveBeenCalledWith(expect.objectContaining({ url: '/pages/cart/cart' }));
      jest.useRealTimers();
    });

    it('deleteOrder 二次确认后复用 orderStore.cancel', async () => {
      mockShowModal(true);
      mockRequest.mockResolvedValueOnce({ id: 'order-detail-1', status: 'CANCELLED' });
      await ctx.handleAction('deleteOrder');
      expect(mockCancel).toHaveBeenCalledWith('order-detail-1', expect.any(String));
    });

    it('withdrawRefund 显示开发中占位', async () => {
      mockRequest.mockResolvedValueOnce({ id: 'order-detail-1' });
      await ctx.handleAction('withdrawRefund');
      expect(wx.showToast).toHaveBeenCalledWith(
        expect.objectContaining({ title: expect.stringContaining('开发中') })
      );
    });

    it('review 显示开发中占位', async () => {
      mockRequest.mockResolvedValueOnce({ id: 'order-detail-1' });
      await ctx.handleAction('review');
      expect(wx.showToast).toHaveBeenCalledWith(
        expect.objectContaining({ title: expect.stringContaining('开发中') })
      );
    });

    it('真正未知的 action 才落 default「未知操作」', async () => {
      mockRequest.mockResolvedValueOnce({ id: 'order-detail-1' });
      await ctx.handleAction('someTotallyUnknownAction');
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '未知操作' }));
    });

    it('409 冲突:toast 状态已变更 + 刷新详情', async () => {
      mockPay.mockRejectedValueOnce({ statusCode: 409, message: 'conflict' });
      mockRequest.mockResolvedValueOnce({ id: 'order-detail-1', status: 'PAID' });
      await ctx.handleAction('pay');
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '订单状态已变更' }));
      expect(mockRequest).toHaveBeenCalled();
    });

    it('403/404:toast 无权限', async () => {
      mockPay.mockRejectedValueOnce({ statusCode: 404, message: 'not found' });
      await ctx.handleAction('pay');
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '订单不存在或无权限' }));
    });

    it('其它未分类错误:toast 错误信息本身', async () => {
      mockPay.mockRejectedValueOnce({ message: '网络异常' });
      await ctx.handleAction('pay');
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '网络异常' }));
    });
  });

  describe('refreshOrder', () => {
    it('无 order 时直接返回', async () => {
      ctx.data.order = null;
      await ctx.refreshOrder();
      expect(mockRequest).not.toHaveBeenCalled();
    });

    it('刷新失败静默吞掉,不抛错', async () => {
      ctx.data.order = { id: 'order-detail-1' };
      mockRequest.mockRejectedValueOnce(new Error('network'));
      await expect(ctx.refreshOrder()).resolves.toBeUndefined();
    });
  });

  describe('applyRefund / confirmReceive / viewLogistics(既有方法,未改动逻辑)', () => {
    it('applyRefund:取消弹窗不发请求', async () => {
      ctx.data.order = { id: 'order-detail-1' };
      mockShowModal(false);
      await ctx.applyRefund();
      expect(mockRequest).not.toHaveBeenCalled();
    });

    it('applyRefund:确认后提交退款申请', async () => {
      ctx.data.order = { id: 'order-detail-1' };
      mockShowModal(true);
      mockRequest.mockResolvedValueOnce({ id: 'order-detail-1', status: 'REFUNDING' });
      await ctx.applyRefund();
      expect(mockRequest).toHaveBeenCalledWith(
        expect.objectContaining({ url: '/orders/order-detail-1/refund', method: 'POST' })
      );
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '退款申请已提交' }));
    });

    it('confirmReceive:确认后提交确认收货,不再写 timeline 字段', async () => {
      ctx.data.order = { id: 'order-detail-1' };
      mockShowModal(true);
      mockRequest.mockResolvedValueOnce({ id: 'order-detail-1', status: 'COMPLETED' });
      await ctx.confirmReceive();
      expect(ctx.data.order.status).toBe('COMPLETED');
      expect(ctx.data.timeline).toBeUndefined();
    });

    it('viewLogistics:未发货时 toast 提示,不复制剪贴板', () => {
      ctx.data.order = { id: 'order-detail-1', status: 'PENDING' };
      ctx.viewLogistics();
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '订单尚未发货' }));
      expect(wx.setClipboardData).not.toHaveBeenCalled();
    });

    it('viewLogistics:已发货且有单号时复制到剪贴板', () => {
      ctx.data.order = {
        id: 'order-detail-1',
        status: 'SHIPPED',
        tracking: { trackingNumber: 'SF123', carrier: '顺丰' },
      };
      wx.setClipboardData.mockImplementation((opts) => opts.success());
      ctx.viewLogistics();
      expect(wx.setClipboardData).toHaveBeenCalledWith(expect.objectContaining({ data: 'SF123' }));
    });
  });
});
