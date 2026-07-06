/**
 * frontend/utils/order-actions.js —— 共享 order-action 分发(mp-cross-screen-cleanup
 * design.md D7)。
 *
 * 背景:order-list.js/order-detail.js 各自独立实现了约 120 行近乎相同的 action 分发
 * 逻辑(pay/cancelOrder/remindShip/reorder/deleteOrder/申请退款 + 409/403/404 错误
 * toast),这正是 mp-backend-contract-gaps 那次 bug 的根因——一份改了 err.status →
 * err.statusCode || err.status,另一份没同步改,直到诊断时才发现两边早就分叉。
 *
 * 临界发现(design 研究阶段修正,不是可选项):两边"申请退款"实现其实都是错的。
 * 后端 RefundRequest DTO(backend/src/main/java/com/seafood/order/api/dto/
 * RefundRequest.java)要求 amount(@NotNull @DecimalMin("0.01"))和 reason 都必填,
 * 但 order-detail.js#applyRefund() 从未传 amount,对真实后端会 400——只是被
 * order-detail.test.js 里那个不校验 request body 内容的 mock 掩盖了(mock 只判
 * url/method)。order-list.js#openRefundSheet 更干脆,是个"开发中"占位,压根不调
 * API。这里统一改走 orderStore.requestRefund(id, amount, reason)——该方法已存在、
 * 已在 store.test.ts 测试过,自带乐观更新(status=REFUNDING)+ 失败回滚,且正确把
 * amount 转发给后端。amount 取 order.totalAmount(全额退款;两个页面目前都没有让
 * 用户自填退款金额的 UI,建这个 UI 不在本次任务范围内)。
 *
 * 覆盖每个 action 分支;每个成功路径都验证调用了传入的 refresh 回调,每个
 * "用户取消确认弹窗"路径都验证不调用任何 API。
 */

global.wx = {
  showLoading: jest.fn(),
  hideLoading: jest.fn(),
  showToast: jest.fn(),
  showModal: jest.fn(),
  switchTab: jest.fn(),
};

const mockPay = jest.fn();
const mockRemindShip = jest.fn();
const mockRebuy = jest.fn();
const mockConfirmReceive = jest.fn();
jest.mock('../../src/features/order/api', () => ({
  OrderAPI: {
    pay: (...a) => mockPay(...a),
    remindShip: (...a) => mockRemindShip(...a),
    rebuy: (...a) => mockRebuy(...a),
    confirmReceive: (...a) => mockConfirmReceive(...a),
  },
}));

const mockCancel = jest.fn();
const mockRequestRefund = jest.fn();
jest.mock('../../src/features/order/store', () => ({
  orderStore: {
    cancel: (...a) => mockCancel(...a),
    requestRefund: (...a) => mockRequestRefund(...a),
  },
}));

const mockCartAdd = jest.fn();
jest.mock('../../src/features/cart/store', () => ({
  cartStore: { add: (...a) => mockCartAdd(...a) },
}));

const { dispatchOrderAction } = require('../order-actions.js');

/** 让 wx.showModal 按 confirm 布尔值走 success 回调(confirmThenXxx 系列的调用惯例)。 */
function mockShowModal(confirm) {
  wx.showModal.mockImplementation((opts) => {
    if (opts && typeof opts.success === 'function') opts.success({ confirm });
  });
}

const order = { id: 'order-123', status: 'PAID', totalAmount: 128.5 };

describe('utils/order-actions.js', () => {
  let mockRefresh;

  beforeEach(() => {
    jest.clearAllMocks();
    mockPay.mockResolvedValue({});
    mockRemindShip.mockResolvedValue({});
    mockRebuy.mockResolvedValue([{ productId: 'p1', quantity: 1 }]);
    mockConfirmReceive.mockResolvedValue({});
    mockCancel.mockResolvedValue({});
    mockRequestRefund.mockResolvedValue({ orderStatus: 'REFUNDING', updatedAt: '2026-07-06T00:00:00Z' });
    mockCartAdd.mockResolvedValue({});
    mockRefresh = jest.fn().mockResolvedValue(undefined);
  });

  describe('pay', () => {
    it('调用 OrderAPI.pay,成功后 toast 已支付 + 调用 refresh', async () => {
      await dispatchOrderAction('pay', order, mockRefresh);
      expect(mockPay).toHaveBeenCalledWith('order-123');
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '已支付' }));
      expect(mockRefresh).toHaveBeenCalled();
    });
  });

  describe('cancelOrder', () => {
    it('二次确认后调用 orderStore.cancel + refresh', async () => {
      mockShowModal(true);
      await dispatchOrderAction('cancelOrder', order, mockRefresh);
      expect(mockCancel).toHaveBeenCalledWith('order-123', expect.any(String));
      expect(mockRefresh).toHaveBeenCalled();
    });

    it('用户取消确认弹窗:不调用 orderStore.cancel', async () => {
      mockShowModal(false);
      await dispatchOrderAction('cancelOrder', order, mockRefresh);
      expect(mockCancel).not.toHaveBeenCalled();
    });
  });

  describe('remindShip', () => {
    it('调用 OrderAPI.remindShip,toast 提醒成功 + refresh', async () => {
      await dispatchOrderAction('remindShip', order, mockRefresh);
      expect(mockRemindShip).toHaveBeenCalledWith('order-123');
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '已提醒商家发货' }));
      expect(mockRefresh).toHaveBeenCalled();
    });
  });

  describe('reorder', () => {
    it('调用 OrderAPI.rebuy,对返回的每个 item 调用 cartStore.add,跳购物车', async () => {
      jest.useFakeTimers();
      mockRebuy.mockResolvedValue([
        { productId: 'p1', quantity: 2 },
        { productId: 'p2', quantity: 1 },
      ]);
      const p = dispatchOrderAction('reorder', order, mockRefresh);
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();
      expect(mockRebuy).toHaveBeenCalledWith('order-123');
      expect(mockCartAdd).toHaveBeenCalledWith('p1', 2);
      expect(mockCartAdd).toHaveBeenCalledWith('p2', 1);
      jest.runAllTimers();
      await p;
      expect(wx.switchTab).toHaveBeenCalledWith(expect.objectContaining({ url: '/pages/cart/cart' }));
      jest.useRealTimers();
    });
  });

  describe('deleteOrder', () => {
    it('二次确认后复用 orderStore.cancel(用户删除订单) + refresh', async () => {
      mockShowModal(true);
      await dispatchOrderAction('deleteOrder', order, mockRefresh);
      expect(mockCancel).toHaveBeenCalledWith('order-123', expect.any(String));
      expect(mockRefresh).toHaveBeenCalled();
    });

    it('用户取消确认弹窗:不调用 orderStore.cancel', async () => {
      mockShowModal(false);
      await dispatchOrderAction('deleteOrder', order, mockRefresh);
      expect(mockCancel).not.toHaveBeenCalled();
    });
  });

  describe('confirmReceipt', () => {
    it('二次确认后调用 OrderAPI.confirmReceive + refresh', async () => {
      mockShowModal(true);
      await dispatchOrderAction('confirmReceipt', order, mockRefresh);
      expect(mockConfirmReceive).toHaveBeenCalledWith('order-123');
      expect(mockRefresh).toHaveBeenCalled();
    });

    it('用户取消确认弹窗:不调用 OrderAPI.confirmReceive', async () => {
      mockShowModal(false);
      await dispatchOrderAction('confirmReceipt', order, mockRefresh);
      expect(mockConfirmReceive).not.toHaveBeenCalled();
    });
  });

  describe('requestRefund / afterSale(关键修复:两者都必须走 orderStore.requestRefund,带 amount)', () => {
    it('requestRefund:二次确认后调用 orderStore.requestRefund(id, order.totalAmount, reason) + refresh', async () => {
      mockShowModal(true);
      await dispatchOrderAction('requestRefund', order, mockRefresh);
      expect(mockRequestRefund).toHaveBeenCalledWith('order-123', 128.5, '用户主动申请');
      expect(mockRefresh).toHaveBeenCalled();
    });

    it('afterSale:同 requestRefund,走同一行为(同一 orderStore.requestRefund 调用)', async () => {
      mockShowModal(true);
      await dispatchOrderAction('afterSale', order, mockRefresh);
      expect(mockRequestRefund).toHaveBeenCalledWith('order-123', 128.5, '用户主动申请');
      expect(mockRefresh).toHaveBeenCalled();
    });

    it('用户取消确认弹窗:不调用 orderStore.requestRefund,也不调用裸 request/OrderAPI.requestRefund', async () => {
      mockShowModal(false);
      await dispatchOrderAction('requestRefund', order, mockRefresh);
      expect(mockRequestRefund).not.toHaveBeenCalled();
    });

    it('金额永远来自 order.totalAmount,不是硬编码或裸 OrderAPI 调用', async () => {
      mockShowModal(true);
      const bigOrder = { id: 'order-999', status: 'COMPLETED', totalAmount: 999.99 };
      await dispatchOrderAction('afterSale', bigOrder, mockRefresh);
      expect(mockRequestRefund).toHaveBeenCalledWith('order-999', 999.99, '用户主动申请');
    });
  });

  describe('withdrawRefund / review(无后端支持,占位 toast,行为不变)', () => {
    it('withdrawRefund 显示开发中占位', async () => {
      await dispatchOrderAction('withdrawRefund', order, mockRefresh);
      expect(wx.showToast).toHaveBeenCalledWith(
        expect.objectContaining({ title: expect.stringContaining('开发中') }),
      );
    });

    it('review 显示开发中占位', async () => {
      await dispatchOrderAction('review', order, mockRefresh);
      expect(wx.showToast).toHaveBeenCalledWith(
        expect.objectContaining({ title: expect.stringContaining('开发中') }),
      );
    });
  });

  describe('未知 action', () => {
    it('toast 未知操作', async () => {
      await dispatchOrderAction('someTotallyUnknownAction', order, mockRefresh);
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '未知操作' }));
    });
  });

  describe('错误分支(409/403/404/其它)', () => {
    it('409 冲突:toast 订单状态已变更 + 调用 refresh', async () => {
      mockPay.mockRejectedValueOnce({ statusCode: 409, message: 'conflict' });
      await dispatchOrderAction('pay', order, mockRefresh);
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '订单状态已变更' }));
      expect(mockRefresh).toHaveBeenCalled();
    });

    it('403:toast 无权限,不调用 refresh', async () => {
      mockPay.mockRejectedValueOnce({ statusCode: 403, message: 'forbidden' });
      await dispatchOrderAction('pay', order, mockRefresh);
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '订单不存在或无权限' }));
      expect(mockRefresh).not.toHaveBeenCalled();
    });

    it('404:toast 无权限,不调用 refresh', async () => {
      mockPay.mockRejectedValueOnce({ statusCode: 404, message: 'not found' });
      await dispatchOrderAction('pay', order, mockRefresh);
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '订单不存在或无权限' }));
      expect(mockRefresh).not.toHaveBeenCalled();
    });

    it('其它未分类错误:toast 错误信息本身,不调用 refresh', async () => {
      mockPay.mockRejectedValueOnce({ message: '网络异常' });
      await dispatchOrderAction('pay', order, mockRefresh);
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '网络异常' }));
      expect(mockRefresh).not.toHaveBeenCalled();
    });
  });

  it('全程 showLoading/hideLoading 配对', async () => {
    await dispatchOrderAction('pay', order, mockRefresh);
    expect(wx.showLoading).toHaveBeenCalledWith(expect.objectContaining({ title: '处理中...' }));
    expect(wx.hideLoading).toHaveBeenCalled();
  });
});
