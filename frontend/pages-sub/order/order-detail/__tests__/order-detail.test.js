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
const mockRequestRefund = jest
  .fn()
  .mockResolvedValue({ orderStatus: 'REFUNDING', updatedAt: 'now' });
jest.mock('../../../../src/features/order/store', () => ({
  orderStore: {
    cancel: (...a) => mockCancel(...a),
    requestRefund: (...a) => mockRequestRefund(...a),
  },
}));

const mockCartAddItem = jest.fn().mockResolvedValue({});
jest.mock('../../../../src/features/cart/store', () => ({
  cartStore: { addItem: (...a) => mockCartAddItem(...a) },
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
    // mp-cross-screen-cleanup D7:handleAction 已删除,pay/cancelOrder/remindShip/
    // reorder/withdrawRefund/review/deleteOrder/requestRefund/afterSale 的分发搬进
    // 共享 utils/order-actions.js(dispatchOrderAction)。这里直接断言端到端效果:
    // e.detail.id 提供 action 类型,不需要 dataset(order-detail 页面本身持有
    // this.data.order,同 mp-08 order-list.js 修复后的事件契约保持一致)。
    it('从 e.detail.id 取 action 类型,直接用 this.data.order,不需要 dataset', async () => {
      ctx.data.order = { id: 'order-detail-1', status: 'PENDING', totalAmount: 88, items: [] };
      mockRequest.mockResolvedValueOnce({ id: 'order-detail-1', status: 'PAID' });
      const e = { detail: { id: 'pay' } };
      await ctx.onActionTap(e);
      expect(mockPay).toHaveBeenCalledWith('order-detail-1');
    });
  });

  describe('onActionTap 分发(共享 dispatchOrderAction 接线)', () => {
    beforeEach(() => {
      ctx.data.order = { id: 'order-detail-1', status: 'PENDING', totalAmount: 88, items: [] };
    });

    it('order 为空时直接返回,不抛错、不调用任何 API', () => {
      ctx.data.order = null;
      expect(() => ctx.onActionTap({ detail: { id: 'pay' } })).not.toThrow();
      expect(mockPay).not.toHaveBeenCalled();
    });

    it('confirmReceipt 复用 confirmReceive(action id 与方法名不一致,须映射;页面本地实现,不进共享 controller)', async () => {
      ctx.confirmReceive = jest.fn();
      await ctx.onActionTap({ detail: { id: 'confirmReceipt' } });
      expect(ctx.confirmReceive).toHaveBeenCalled();
    });

    it('viewTracking 复用 viewLogistics(页面本地实现,不进共享 controller)', async () => {
      ctx.viewLogistics = jest.fn();
      await ctx.onActionTap({ detail: { id: 'viewTracking' } });
      expect(ctx.viewLogistics).toHaveBeenCalled();
    });

    it('pay 触发 OrderAPI.pay,不落 default 分支', async () => {
      mockRequest.mockResolvedValueOnce({ id: 'order-detail-1', status: 'PAID' });
      await ctx.onActionTap({ detail: { id: 'pay' } });
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
      await ctx.onActionTap({ detail: { id: 'cancelOrder' } });
      expect(mockCancel).toHaveBeenCalledWith('order-detail-1', expect.any(String));
    });

    it('取消二次确认弹窗时不调用 orderStore.cancel', async () => {
      mockShowModal(false);
      await ctx.onActionTap({ detail: { id: 'cancelOrder' } });
      expect(mockCancel).not.toHaveBeenCalled();
    });

    it('remindShip 触发 OrderAPI.remindShip', async () => {
      mockRequest.mockResolvedValueOnce({ id: 'order-detail-1', status: 'PAID' });
      await ctx.onActionTap({ detail: { id: 'remindShip' } });
      expect(mockRemindShip).toHaveBeenCalledWith('order-detail-1');
    });

    it('reorder 触发 rebuy + 加购 + 跳购物车', async () => {
      jest.useFakeTimers();
      const p = ctx.onActionTap({ detail: { id: 'reorder' } });
      await Promise.resolve();
      await Promise.resolve();
      expect(mockRebuy).toHaveBeenCalledWith('order-detail-1');
      expect(mockCartAddItem).toHaveBeenCalledWith('p1', 2);
      jest.runAllTimers();
      await p;
      expect(wx.switchTab).toHaveBeenCalledWith(expect.objectContaining({ url: '/pages/cart/cart' }));
      jest.useRealTimers();
    });

    it('deleteOrder 二次确认后复用 orderStore.cancel', async () => {
      mockShowModal(true);
      mockRequest.mockResolvedValueOnce({ id: 'order-detail-1', status: 'CANCELLED' });
      await ctx.onActionTap({ detail: { id: 'deleteOrder' } });
      expect(mockCancel).toHaveBeenCalledWith('order-detail-1', expect.any(String));
    });

    it('withdrawRefund 显示开发中占位', async () => {
      mockRequest.mockResolvedValueOnce({ id: 'order-detail-1' });
      await ctx.onActionTap({ detail: { id: 'withdrawRefund' } });
      expect(wx.showToast).toHaveBeenCalledWith(
        expect.objectContaining({ title: expect.stringContaining('开发中') })
      );
    });

    it('review 显示开发中占位', async () => {
      mockRequest.mockResolvedValueOnce({ id: 'order-detail-1' });
      await ctx.onActionTap({ detail: { id: 'review' } });
      expect(wx.showToast).toHaveBeenCalledWith(
        expect.objectContaining({ title: expect.stringContaining('开发中') })
      );
    });

    it('真正未知的 action 才落 default「未知操作」', async () => {
      mockRequest.mockResolvedValueOnce({ id: 'order-detail-1' });
      await ctx.onActionTap({ detail: { id: 'someTotallyUnknownAction' } });
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '未知操作' }));
    });

    it('409 冲突:toast 状态已变更 + 刷新详情', async () => {
      mockPay.mockRejectedValueOnce({ statusCode: 409, message: 'conflict' });
      mockRequest.mockResolvedValueOnce({ id: 'order-detail-1', status: 'PAID' });
      await ctx.onActionTap({ detail: { id: 'pay' } });
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '订单状态已变更' }));
      expect(mockRequest).toHaveBeenCalled();
    });

    it('403/404:toast 无权限', async () => {
      mockPay.mockRejectedValueOnce({ statusCode: 404, message: 'not found' });
      await ctx.onActionTap({ detail: { id: 'pay' } });
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '订单不存在或无权限' }));
    });

    it('其它未分类错误:toast 错误信息本身', async () => {
      mockPay.mockRejectedValueOnce({ message: '网络异常' });
      await ctx.onActionTap({ detail: { id: 'pay' } });
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '网络异常' }));
    });

    // mp-cross-screen-cleanup D7 关键修复:requestRefund/afterSale 此前走
    // applyRefund() 的裸 request() 调用,从未传后端要求的 amount 字段,对真实后端
    // 会 400——只是被这个文件里不校验 request body 内容的 mock 掩盖了。这里改成
    // 断言 ACTUAL 转发参数:orderStore.requestRefund(id, order.totalAmount, reason)。
    it('requestRefund 二次确认后调用 orderStore.requestRefund(id, order.totalAmount, reason)——不是裸 request()', async () => {
      mockShowModal(true);
      await ctx.onActionTap({ detail: { id: 'requestRefund' } });
      expect(mockRequestRefund).toHaveBeenCalledWith('order-detail-1', 88, '用户主动申请');
      // 关键修复:确认不再是原来那个漏传 amount 的裸 request() 调用
      expect(mockRequest).not.toHaveBeenCalledWith(
        expect.objectContaining({ url: expect.stringContaining('/refund') })
      );
    });

    it('afterSale 和 requestRefund 走同一行为(同一 orderStore.requestRefund 调用)', async () => {
      mockShowModal(true);
      await ctx.onActionTap({ detail: { id: 'afterSale' } });
      expect(mockRequestRefund).toHaveBeenCalledWith('order-detail-1', 88, '用户主动申请');
    });

    it('requestRefund 取消确认弹窗时不调用 orderStore.requestRefund', async () => {
      mockShowModal(false);
      await ctx.onActionTap({ detail: { id: 'requestRefund' } });
      expect(mockRequestRefund).not.toHaveBeenCalled();
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

  // mp-cross-screen-cleanup D7:applyRefund() 已删除——原来的"确认后提交退款申请"
  // 测试断言只查了 url/method,从没校验 request body 内容,这正是 amount 字段
  // 缺失的生产 bug 能一直不被发现的原因。这块行为现在由共享 dispatchOrderAction
  // 的 requestRefund/afterSale 分支覆盖(见上面"onActionTap 分发"describe 块里
  // 断言 orderStore.requestRefund(id, order.totalAmount, reason) 的用例)。
  describe('confirmReceive / viewLogistics(既有方法,未改动逻辑)', () => {
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
