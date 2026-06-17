// pages-sub/order/order-detail/order-detail.js
// sprint-1-closure mp-09 订单详情页 — OD 设计稿
// 5 段:状态 banner / 物流时间线 / 收货地址 / 商品清单 / 金额明细 / 订单信息 + 底部 sticky action bar。
const derive = require('../../../utils/order-detail-derive.js');
const { request } = require('../../../utils/request.js');

Page({
  data: {
    order: null,
    statusBanner: null,
    timeline: [],
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
      const order = await request({ url: `/api/orders/${id}`, method: 'GET' });
      this.setData({
        order,
        statusBanner: derive.deriveBanner(order),
        timeline: derive.deriveTimeline(order),
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
        url: `/api/orders/${order.id}/refund`,
        method: 'POST',
        data: { reason: '用户主动申请' },
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
        url: `/api/orders/${order.id}/confirm`,
        method: 'POST',
      });
      this.setData({
        order: newOrder,
        statusBanner: derive.deriveBanner(newOrder),
        timeline: derive.deriveTimeline(newOrder),
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
