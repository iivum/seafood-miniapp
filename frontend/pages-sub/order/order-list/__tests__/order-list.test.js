/**
 * order-list.js tests(此前零覆盖 —— mp-od-prototype-alignment mp-08 诊断发现 bug 时补齐)。
 *
 * 核心回归锁：onActionTap 事件契约。OrderActionRow 组件 triggerEvent('action', {id})
 * 的 detail 只带 action 类型字符串(如 'cancelOrder'),订单 id 是 wxml 上
 * <order-action-row data-id="{{item.id}}"> 挂的,要用 e.currentTarget.dataset.id 读
 * (同 onOrderTap/onTabTap 既有惯例)。旧代码从 e.detail 解构 {id, action} 两者都取错
 * —— action 恒 undefined 落 default「未知操作」,id 其实是 action 类型字符串。
 * 组件展示层此前没渲染时这段代码从未被真实触发,渲染层修好后才暴露。
 */

global.wx = {
  showLoading: jest.fn(),
  hideLoading: jest.fn(),
  showToast: jest.fn(),
  showModal: jest.fn(),
  navigateTo: jest.fn(),
  navigateBack: jest.fn(),
  switchTab: jest.fn(),
  stopPullDownRefresh: jest.fn(),
  getStorageSync: jest.fn(() => 'token-abc'),
};

const mockRefresh = jest.fn().mockResolvedValue([]);
const mockCancel = jest.fn().mockResolvedValue({});
jest.mock('../../../../src/features/order/store', () => ({
  orderStore: { refresh: (...a) => mockRefresh(...a), cancel: (...a) => mockCancel(...a) },
}));

const mockPay = jest.fn().mockResolvedValue({});
const mockRemindShip = jest.fn().mockResolvedValue({});
const mockConfirmReceive = jest.fn().mockResolvedValue({});
const mockRebuy = jest.fn().mockResolvedValue([{ productId: 'p1', quantity: 1 }]);
jest.mock('../../../../src/features/order/api', () => ({
  OrderAPI: {
    pay: (...a) => mockPay(...a),
    remindShip: (...a) => mockRemindShip(...a),
    confirmReceive: (...a) => mockConfirmReceive(...a),
    rebuy: (...a) => mockRebuy(...a),
  },
}));

const mockCartAdd = jest.fn().mockResolvedValue({});
jest.mock('../../../../src/features/cart/store', () => ({
  cartStore: { add: (...a) => mockCartAdd(...a) },
}));

let pageConfig;
global.Page = (config) => { pageConfig = config; };
require('../order-list.js');

describe('order-list', () => {
  let ctx;

  beforeEach(() => {
    jest.clearAllMocks();
    wx.getStorageSync.mockReturnValue('token-abc');
    ctx = {
      data: JSON.parse(JSON.stringify(pageConfig.data)),
      setData: jest.fn(function (patch) { Object.assign(this.data, patch); }),
    };
    ctx.setData = ctx.setData.bind(ctx);
    for (const key of Object.keys(pageConfig)) {
      if (typeof pageConfig[key] === 'function') ctx[key] = pageConfig[key].bind(ctx);
    }
  });

  describe('onShow', () => {
    it('未登录时跳 login 带 redirect,不拉订单', () => {
      wx.getStorageSync.mockReturnValue('');
      ctx.onShow();
      expect(wx.navigateTo).toHaveBeenCalledWith(
        expect.objectContaining({ url: expect.stringContaining('/login?redirect=') }),
      );
      expect(mockRefresh).not.toHaveBeenCalled();
    });

    it('已登录时拉订单', () => {
      ctx.onShow();
      expect(mockRefresh).toHaveBeenCalled();
    });
  });

  describe('onActionTap(事件契约回归锁)', () => {
    it('从 e.detail.id 取 action 类型、从 dataset.id 取订单 id,不是反过来', async () => {
      ctx.handleAction = jest.fn();
      const e = { detail: { id: 'cancelOrder' }, currentTarget: { dataset: { id: 'order-123' } } };
      ctx.onActionTap(e);
      expect(ctx.handleAction).toHaveBeenCalledWith('cancelOrder', 'order-123');
    });

    it('端到端:cancelOrder 触发 confirmThenCancel → orderStore.cancel,不落 default 分支', async () => {
      wx.showModal.mockImplementation((opts) => opts.success({ confirm: true }));
      const e = { detail: { id: 'cancelOrder' }, currentTarget: { dataset: { id: 'order-123' } } };
      await ctx.onActionTap(e);
      expect(mockCancel).toHaveBeenCalledWith('order-123', expect.any(String));
      expect(wx.showToast).not.toHaveBeenCalledWith(expect.objectContaining({ title: '未知操作' }));
    });

    it('端到端:pay 触发 OrderAPI.pay,不落 default 分支', async () => {
      const e = { detail: { id: 'pay' }, currentTarget: { dataset: { id: 'order-123' } } };
      await ctx.onActionTap(e);
      expect(mockPay).toHaveBeenCalledWith('order-123');
      expect(wx.showToast).not.toHaveBeenCalledWith(expect.objectContaining({ title: '未知操作' }));
    });

    it('端到端:remindShip 触发 OrderAPI.remindShip', async () => {
      const e = { detail: { id: 'remindShip' }, currentTarget: { dataset: { id: 'order-123' } } };
      await ctx.onActionTap(e);
      expect(mockRemindShip).toHaveBeenCalledWith('order-123');
    });

    it('端到端:confirmReceipt 走二次确认后触发 OrderAPI.confirmReceive', async () => {
      wx.showModal.mockImplementation((opts) => opts.success({ confirm: true }));
      const e = { detail: { id: 'confirmReceipt' }, currentTarget: { dataset: { id: 'order-123' } } };
      await ctx.onActionTap(e);
      expect(mockConfirmReceive).toHaveBeenCalledWith('order-123');
    });

    it('真的未知的 action 类型才落 default「未知操作」', async () => {
      const e = { detail: { id: 'someTotallyUnknownAction' }, currentTarget: { dataset: { id: 'order-123' } } };
      await ctx.onActionTap(e);
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '未知操作' }));
    });
  });

  describe('onTabTap', () => {
    it('切 tab 更新 activeTab + 重新过滤', () => {
      ctx.data.orders = [{ id: 'o1', status: 'PENDING' }, { id: 'o2', status: 'PAID' }];
      const e = { currentTarget: { dataset: { tab: 'PENDING' } } };
      ctx.onTabTap(e);
      expect(ctx.data.activeTab).toBe('PENDING');
      expect(ctx.data.filteredOrders).toEqual([{ id: 'o1', status: 'PENDING' }]);
    });
  });

  describe('computeTabCounts / filterByTab', () => {
    it('按状态计数', () => {
      const orders = [{ status: 'PENDING' }, { status: 'PENDING' }, { status: 'PAID' }];
      expect(ctx.computeTabCounts(orders)).toEqual({ PENDING: 2, PAID: 1 });
    });

    it('ALL tab 返回全部', () => {
      const orders = [{ status: 'PENDING' }];
      expect(ctx.filterByTab(orders, 'ALL')).toEqual(orders);
    });
  });

  describe('其它交互', () => {
    it('onOrderTap 跳订单详情', () => {
      ctx.onOrderTap({ currentTarget: { dataset: { id: 'order-1' } } });
      expect(wx.navigateTo).toHaveBeenCalledWith(
        expect.objectContaining({ url: expect.stringContaining('order-1') }),
      );
    });

    it('onBack 返回上一页', () => {
      ctx.onBack();
      expect(wx.navigateBack).toHaveBeenCalled();
    });

    it('onSearch 显示开发中提示', () => {
      ctx.onSearch();
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: expect.stringContaining('开发中') }));
    });

    it('onRetry 重新拉取', () => {
      ctx.onRetry();
      expect(mockRefresh).toHaveBeenCalled();
    });

    it('onGoShopping 跳首页', () => {
      ctx.onGoShopping();
      expect(wx.switchTab).toHaveBeenCalledWith(expect.objectContaining({ url: '/pages/index/index' }));
    });

    it('onPullDownRefresh 拉取后停止下拉动画', async () => {
      // onPullDownRefresh 不 return promise chain(mp 生命周期回调惯例,不被运行时 await),
      // 调用本身的返回值是 undefined,要多 flush 几轮微任务队列等内部链路真正跑完。
      ctx.onPullDownRefresh();
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();
      expect(wx.stopPullDownRefresh).toHaveBeenCalled();
    });
  });

  describe('handleAction 分支', () => {
    it('reorder 触发 handleRebuy,加购后跳购物车', async () => {
      jest.useFakeTimers();
      const e = { detail: { id: 'reorder' }, currentTarget: { dataset: { id: 'order-123' } } };
      const p = ctx.onActionTap(e);
      await Promise.resolve();
      await Promise.resolve();
      expect(mockRebuy).toHaveBeenCalledWith('order-123');
      expect(mockCartAdd).toHaveBeenCalledWith('p1', 1);
      jest.runAllTimers();
      await p;
      expect(wx.switchTab).toHaveBeenCalledWith(expect.objectContaining({ url: '/pages/cart/cart' }));
      jest.useRealTimers();
    });

    it('requestRefund 打开退款 sheet(占位 modal),不调 API', async () => {
      const e = { detail: { id: 'requestRefund' }, currentTarget: { dataset: { id: 'order-123' } } };
      await ctx.onActionTap(e);
      expect(wx.showModal).toHaveBeenCalledWith(expect.objectContaining({ title: '申请退款' }));
    });

    it('409 冲突:toast 状态已变更 + 刷新', async () => {
      // OrderAPI(src/shared/api/request.js ApiError)真实只带 .statusCode,没有
      // .status——mock 用真实 shape,不是虚构的 { status: 409 }(那样会让 handleAction
      // 读错字段名的 bug 被测试掩盖过去,见 order-detail.js 的正确参照写法)。
      mockPay.mockRejectedValueOnce({ statusCode: 409, message: 'conflict' });
      const e = { detail: { id: 'pay' }, currentTarget: { dataset: { id: 'order-123' } } };
      await ctx.onActionTap(e);
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '订单状态已变更' }));
    });

    it('403/404:toast 无权限,不刷新', async () => {
      mockPay.mockRejectedValueOnce({ statusCode: 403, message: 'forbidden' });
      const e = { detail: { id: 'pay' }, currentTarget: { dataset: { id: 'order-123' } } };
      const callsBefore = mockRefresh.mock.calls.length;
      await ctx.onActionTap(e);
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '订单不存在或无权限' }));
      expect(mockRefresh.mock.calls.length).toBe(callsBefore);
    });

    it('取消确认弹窗时不调用 API', async () => {
      wx.showModal.mockImplementation((opts) => opts.success({ confirm: false }));
      const e = { detail: { id: 'cancelOrder' }, currentTarget: { dataset: { id: 'order-123' } } };
      await ctx.onActionTap(e);
      expect(mockCancel).not.toHaveBeenCalled();
    });

    it('deleteOrder 走二次确认后复用 orderStore.cancel', async () => {
      wx.showModal.mockImplementation((opts) => opts.success({ confirm: true }));
      const e = { detail: { id: 'deleteOrder' }, currentTarget: { dataset: { id: 'order-123' } } };
      await ctx.onActionTap(e);
      expect(mockCancel).toHaveBeenCalledWith('order-123', expect.any(String));
    });

    it('viewTracking 跳订单详情页(复用 onOrderTap 同款跳转)', async () => {
      const e = { detail: { id: 'viewTracking' }, currentTarget: { dataset: { id: 'order-123' } } };
      await ctx.onActionTap(e);
      expect(wx.navigateTo).toHaveBeenCalledWith(
        expect.objectContaining({ url: expect.stringContaining('order-123') }),
      );
    });

    it('review 显示评价开发中占位', async () => {
      const e = { detail: { id: 'review' }, currentTarget: { dataset: { id: 'order-123' } } };
      await ctx.onActionTap(e);
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: expect.stringContaining('开发中') }));
    });

    it('withdrawRefund 显示撤回开发中占位', async () => {
      const e = { detail: { id: 'withdrawRefund' }, currentTarget: { dataset: { id: 'order-123' } } };
      await ctx.onActionTap(e);
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: expect.stringContaining('开发中') }));
    });

    it('其它未分类错误(非 409/403/404):toast 错误信息本身', async () => {
      mockPay.mockRejectedValueOnce({ message: '网络异常' });
      const e = { detail: { id: 'pay' }, currentTarget: { dataset: { id: 'order-123' } } };
      await ctx.onActionTap(e);
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '网络异常' }));
    });
  });

  it('noop 不抛错', () => {
    expect(() => ctx.noop()).not.toThrow();
  });
});
