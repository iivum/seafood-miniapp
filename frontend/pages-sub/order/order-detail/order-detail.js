// pages-sub/order/order-detail/order-detail.js
// sprint-1-closure mp-09 订单详情页 — OD 设计稿
// 5 段:状态 banner / 物流时间线 / 收货地址 / 商品清单 / 金额明细 / 订单信息 + 底部 sticky action bar。
//
// mp-od-prototype-alignment mp-09(brief `.superpowers/sdd/mp-od-8-order-detail-brief.md`):
// 接线 order-tracking-timeline 组件后,timeline 节点由组件内部从 order.tracking.events
// 推算,页面不再自己维护 timeline 字段/调用 derive.deriveTimeline()。deriveBanner 仍在用。
const derive = require('../../../utils/order-detail-derive.js');
const { request } = require('../../../utils/request.js');
const { OrderAPI } = require('../../../src/features/order/api');
const { orderStore } = require('../../../src/features/order/store');
const { cartStore } = require('../../../src/features/cart/store');

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
  onActionTap(e) {
    const action = e.detail.id;
    this.handleAction(action);
  },

  /**
   * action 按钮统一入口,状态矩阵见 OrderActionRow.getActionsFor()(7 状态 × 多按钮)。
   * 已实现的 3 个方法(applyRefund/confirmReceive/viewLogistics)直接复用——
   * action id 与方法名不完全一致,映射如下:
   *   requestRefund(PAID) / afterSale(COMPLETED) → applyRefund()
   *   confirmReceipt(SHIPPED)                    → confirmReceive()
   *   viewTracking(SHIPPED)                       → viewLogistics()
   * 其余分支(pay/cancelOrder/remindShip/reorder/withdrawRefund/review/deleteOrder)
   * 参照 order-list.js 的 handleAction 调 OrderAPI/orderStore,不重新发明。
   */
  async handleAction(action) {
    const order = this.data.order;
    if (!order) return;
    const orderId = order.id;

    switch (action) {
      case 'requestRefund':
      case 'afterSale':
        return this.applyRefund();
      case 'confirmReceipt':
        return this.confirmReceive();
      case 'viewTracking':
        return this.viewLogistics();
      default:
        break;
    }

    wx.showLoading({ title: '处理中...', mask: true });
    try {
      switch (action) {
        case 'pay':
          await OrderAPI.pay(orderId);
          wx.showToast({ title: '已支付', icon: 'success' });
          break;
        case 'cancelOrder':
          await this.confirmThenCancel(orderId);
          break;
        case 'remindShip':
          await OrderAPI.remindShip(orderId);
          wx.showToast({ title: '已提醒商家发货', icon: 'success' });
          break;
        case 'reorder':
          await this.handleRebuy(orderId);
          return; // handleRebuy 自行处理 loading/toast/跳转
        case 'withdrawRefund':
          wx.showToast({ title: '撤回功能开发中', icon: 'none' });
          break;
        case 'review':
          wx.showToast({ title: '评价功能开发中', icon: 'none' });
          break;
        case 'deleteOrder':
          await this.confirmThenDelete(orderId);
          break;
        default:
          wx.showToast({ title: '未知操作', icon: 'none' });
      }
      wx.hideLoading();
      await this.refreshOrder();
    } catch (err) {
      wx.hideLoading();
      // OrderAPI(src/shared/api/request.js ApiError)抛 .statusCode;utils/request.js
      // 的 reject(res) 也带 .statusCode——两条链路统一读 statusCode,兼容 .status 兜底。
      const status = err && (err.statusCode || err.status);
      if (status === 409) {
        wx.showToast({ title: '订单状态已变更', icon: 'none' });
        await this.refreshOrder();
      } else if (status === 403 || status === 404) {
        wx.showToast({ title: '订单不存在或无权限', icon: 'none' });
      } else {
        const msg = (err && err.message) || '操作失败';
        wx.showToast({ title: msg, icon: 'none' });
      }
    }
  },

  confirmThenCancel(orderId) {
    return new Promise((resolve, reject) => {
      wx.showModal({
        title: '确认取消订单',
        content: '取消后无法恢复,确定要取消吗?',
        success: (res) => {
          if (res.confirm) {
            orderStore.cancel(orderId, '用户取消订单').then(resolve).catch(reject);
          } else {
            reject({ message: '用户取消', cancelled: true });
          }
        },
        fail: reject,
      });
    });
  },

  confirmThenDelete(orderId) {
    return new Promise((resolve, reject) => {
      wx.showModal({
        title: '删除订单',
        content: '删除后无法恢复,确定删除吗?',
        success: (res) => {
          if (res.confirm) {
            // 删除 = 后端 cancel + 隐藏(本迭代没 delete 端点,走 cancel 替代,同 order-list.js 惯例)
            orderStore.cancel(orderId, '用户删除订单').then(resolve).catch(reject);
          } else {
            reject({ message: '用户取消', cancelled: true });
          }
        },
        fail: reject,
      });
    });
  },

  async handleRebuy(orderId) {
    wx.hideLoading();
    wx.showLoading({ title: '加入购物车...', mask: true });
    try {
      const items = await OrderAPI.rebuy(orderId);
      if (items && items.length) {
        for (const it of items) {
          await cartStore.add(it.productId, it.quantity);
        }
      }
      wx.hideLoading();
      wx.showToast({ title: `已加入 ${items.length} 件`, icon: 'success' });
      setTimeout(() => {
        wx.switchTab({ url: '/pages/cart/cart' });
      }, 800);
    } catch (err) {
      wx.hideLoading();
      wx.showToast({ title: (err && err.message) || '加入购物车失败', icon: 'none' });
    }
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

  async applyRefund() {
    const order = this.data.order;
    if (!order) return;
    const res = await wx.showModal({
      title: '申请退款',
      content: '确定要申请退款?商家将在 24 小时内处理',
    });
    if (!res.confirm) return;

    try {
      wx.showLoading({ title: '提交中...' });
      const newOrder = await request({
        url: `/orders/${order.id}/refund`,
        method: 'POST',
        data: { reason: '用户主动申请' },
        needAuth: true,
      });
      this.setData({
        order: newOrder,
        statusBanner: derive.deriveBanner(newOrder),
      });
      wx.showToast({ title: '退款申请已提交', icon: 'success' });
    } catch (err) {
      wx.showToast({ title: (err && err.message) || '申请失败,请重试', icon: 'none' });
    } finally {
      wx.hideLoading();
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
