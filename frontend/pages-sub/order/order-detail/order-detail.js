// pages-sub/order/order-detail/order-detail.js
// sprint-1-closure mp-09 订单详情页 — OD 设计稿
// 5 段:状态 banner / 物流时间线 / 收货地址 / 商品清单 / 金额明细 / 订单信息 + 底部 sticky action bar。
//
// mp-od-prototype-alignment mp-09(brief `.superpowers/sdd/mp-od-8-order-detail-brief.md`):
// 接线 order-tracking-timeline 组件后,timeline 节点由组件内部从 order.tracking.events
// 推算,页面不再自己维护 timeline 字段/调用 derive.deriveTimeline()。deriveBanner 仍在用。
const derive = require('../../../utils/order-detail-derive.js');
const { request } = require('../../../utils/request.js');
const { dispatchOrderAction } = require('../../../utils/order-actions');
// mp-cross-screen-cleanup D7:pay/cancelOrder/remindShip/reorder/deleteOrder/
// requestRefund/afterSale 的分发逻辑(含 409/403/404 错误 toast)已抽到
// utils/order-actions.js,和 order-list.js 共用一份实现——不再各自维护一份近乎
// 相同的 handleAction/confirmThenCancel/confirmThenDelete/handleRebuy/applyRefund
// (这份重复正是 mp-backend-contract-gaps 那次 "一边修了 err.status,另一边漏改"
// bug 的根因)。同时修复了一个此前生产环境会真实 400 的 bug:原 applyRefund() 从
// 未传后端要求的 amount 字段,现在统一改走
// orderStore.requestRefund(id, order.totalAmount, reason)。

Page({
  data: {
    order: null,
    statusBanner: null,
    isLoading: true,
    isError: false,
    errorMessage: '',
  },

  onLoad(options) {
    const id = options && options.id;
    if (!id) {
      this.handleError({ message: '订单 ID 缺失', statusCode: 400 });
      return;
    }
    this.loadOrder(id);
  },

  async loadOrder(id) {
    this.setData({ isLoading: true, isError: false });
    try {
      // baseUrl 已含 /api(app.globalData.baseUrl=…/api),故 url 不再带 /api 前缀,
      // 否则双 /api → 404。鉴权端点需 needAuth(否则无 Authorization → 401)。C5 mp-09 实证。
      const order = await request({ url: `/orders/${id}`, method: 'GET', needAuth: true });
      this.setData({
        order,
        statusBanner: derive.deriveBanner(order),
        isLoading: false,
      });
    } catch (err) {
      this.handleError(err);
    }
  },

  handleError(err) {
    const status = err && err.statusCode;
    if (status === 404) {
      wx.showModal({
        title: '订单不存在',
        content: '该订单可能已被删除',
        showCancel: false,
        success: () => wx.navigateBack(),
      });
      return;
    }
    if (status === 401 || status === 403) {
      wx.showModal({
        title: '请先登录',
        content: '登录后可查看订单详情',
        showCancel: false,
        success: () => wx.navigateTo({ url: '/pages-sub/user/login/login' }),
      });
      return;
    }
    this.setData({
      isLoading: false,
      isError: true,
      errorMessage: (err && err.message) || '加载失败,请重试',
    });
  },

  onRetry() {
    const id = this.data.order && this.data.order.id;
    if (id) this.loadOrder(id);
  },

  onBack() {
    wx.navigateBack();
  },

  // mp-09 接线 order-action-row —— 事件契约同 mp-08 order-list.js 修复后的正确写法:
  // OrderActionRow triggerEvent('action', { id }),detail 只带 action 类型字符串
  // (如 'cancelOrder'),不需要像 order-list 卡片列表那样从 wxml data-id 再取订单 id ——
  // order-detail 页面本身就持有当前订单,this.data.order.id 就是订单 id。
  // (mp-08 诊断记录:旧代码曾从 e.detail 解构 { id, action } 两者都取错,
  // action 恒 undefined 落 default「未知操作」——这里从一开始就写对,不重蹈覆辙。)
  //
  // mp-cross-screen-cleanup D7:pay/cancelOrder/remindShip/reorder/withdrawRefund/
  // review/deleteOrder/requestRefund/afterSale 的分发(含 409/403/404 错误 toast)
  // 已抽到共享 utils/order-actions.js(dispatchOrderAction),和 order-list.js 共用
  // 一份实现。confirmReceipt(confirmReceive,剪贴板无关,只是这个页面已有的实现跟
  // OrderAPI.confirmReceive 之外还直接 setData 刷新——保留本地不搬,design.md 未强制
  // 要求)/viewTracking(viewLogistics,剪贴板复制,页面特有 UI)在调用共享分发前
  // 自行短路,不进共享 switch。
  onActionTap(e) {
    const action = e.detail.id;
    const order = this.data.order;
    if (!order) return;
    if (action === 'confirmReceipt') return this.confirmReceive();
    if (action === 'viewTracking') return this.viewLogistics();
    return dispatchOrderAction(action, order, this.refreshOrder.bind(this));
  },

  // 状态机分支(pay/cancelOrder/remindShip/deleteOrder)操作成功后重拉当前订单详情,
  // 刷新 statusBanner。操作本身已经 await 成功,刷新失败不该覆盖已完成的操作结果,
  // 故内部吞掉异常静默返回(同 store.js placeOrder 里 cartStore.clear() 的 best-effort 惯例)。
  async refreshOrder() {
    const order = this.data.order;
    if (!order) return;
    try {
      const fresh = await request({ url: `/orders/${order.id}`, method: 'GET', needAuth: true });
      this.setData({ order: fresh, statusBanner: derive.deriveBanner(fresh) });
    } catch {
      /* best-effort */
    }
  },

  async confirmReceive() {
    const order = this.data.order;
    if (!order) return;
    const res = await wx.showModal({
      title: '确认收货',
      content: '请检查海鲜鲜度后再确认',
    });
    if (!res.confirm) return;

    try {
      wx.showLoading({ title: '确认中...' });
      const newOrder = await request({
        url: `/orders/${order.id}/confirm-receive`,
        method: 'POST',
        needAuth: true,
      });
      this.setData({
        order: newOrder,
        statusBanner: derive.deriveBanner(newOrder),
      });
      wx.showToast({ title: '已确认收货', icon: 'success' });
    } catch (err) {
      wx.showToast({ title: (err && err.message) || '确认失败,请重试', icon: 'none' });
    } finally {
      wx.hideLoading();
    }
  },

  viewLogistics() {
    const order = this.data.order;
    if (!order) return;
    if (order.status !== 'SHIPPED' && order.status !== 'COMPLETED') {
      wx.showToast({ title: '订单尚未发货', icon: 'none' });
      return;
    }
    if (!order.tracking || !order.tracking.trackingNumber) {
      wx.showToast({ title: '物流单号暂未生成', icon: 'none' });
      return;
    }
    wx.setClipboardData({
      data: order.tracking.trackingNumber,
      success: () =>
        wx.showToast({
          title: '物流单号已复制,请到顺丰/京东小程序查询',
          icon: 'none',
        }),
    });
  },
});
