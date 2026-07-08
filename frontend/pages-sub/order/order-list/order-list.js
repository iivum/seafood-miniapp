/**
 * Order list page — sprint-1-closure 7.2/7.3/7.4/7.5/7.6
 *
 * 7.2 调 OrderAPI 的 6 个状态机端点
 * 7.3 按钮点击 → loading → onSuccess 200 refresh, on 409 toast「订单状态已变更」+ refresh,
 *                on 403/404 toast「订单不存在或无权限」
 * 7.4 卡片布局对齐 OD 原型(status badge / items / action row)
 * 7.5 v2 tokens 全量
 * 7.6 walk PENDING → cancel / PENDING → pay,verify action row updates — 留 E2E
 */
const { orderStore } = require('../../../src/features/order/store');
const { dispatchOrderAction } = require('../../../utils/order-actions');
// wx 是 mp 运行时全局,直接用即可。原 `require('../../../src/shared/wx')` 指向不存在的
// 模块(从未建),导致页面加载即抛 "module 'src/shared/wx.js' is not defined" → 白屏。
// C5 感知层 mp-08 截到全白即此 bug;删除该 dangling require 修复。
//
// mp-cross-screen-cleanup D7:pay/cancelOrder/remindShip/reorder/deleteOrder/
// requestRefund/afterSale/confirmReceipt 的分发逻辑(含 409/403/404 错误 toast)
// 已抽到 utils/order-actions.js,和 order-detail.js 共用一份实现——不再各自维护
// 一份近乎相同的 handleAction/confirmThenCancel/confirmThenDelete/handleRebuy/
// openRefundSheet(这份重复正是 mp-backend-contract-gaps 那次 "一边修了
// err.status,另一边漏改" bug 的根因)。同时修复了一个此前生产环境会真实 400 的
// bug:"申请退款"此前在这里只是"开发中"占位,压根没调 API——现在统一改走
// orderStore.requestRefund(id, order.totalAmount, reason)。

const STATUS_LABEL = {
  PENDING: '待付款',
  PAID: '待发货',
  SHIPPED: '待收货',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REFUNDING: '退款中',
  REFUNDED: '已退款',
};

const TABS = [
  { key: 'ALL', label: '全部' },
  { key: 'PENDING', label: '待付款' },
  { key: 'PAID', label: '待发货' },
  { key: 'SHIPPED', label: '待收货' },
  { key: 'COMPLETED', label: '已完成' },
  { key: 'CANCELLED', label: '已取消' },
];

Page({
  data: {
    orders: [],
    filteredOrders: [],
    tabs: TABS,
    activeTab: 'ALL',
    statusTextMap: STATUS_LABEL,
    isLoading: false,
    isError: false,
    errorMessage: '',
  },

  /**
   * P1 鉴权守卫 — 未登录直接 GET /api/orders 返 403,既挡 403 错误又挡用户误解。
   * 顶部加 token 检查,未登录跳 login 带 redirect;已登录继续原 fetchOrders。
   */
  onShow() {
    const token = wx.getStorageSync('accessToken');
    if (!token) {
      wx.navigateTo({
        url: '/pages-sub/user/login/login?redirect=/pages-sub/order/order-list/order-list',
      });
      return;
    }
    this.fetchOrders();
  },

  onPullDownRefresh: function () {
    this.fetchOrders().then(() => wx.stopPullDownRefresh());
  },

  fetchOrders: function () {
    this.setData({ isLoading: true, isError: false });
    return orderStore
      .refresh()
      .then((orders) => this.applyOrders(orders))
      .catch((err) => {
        const message = (err && err.message) || '加载订单失败';
        this.setData({ isLoading: false, isError: true, errorMessage: message });
        wx.showToast({ title: message, icon: 'none' });
      });
  },

  applyOrders(orders) {
    const tabCounts = this.computeTabCounts(orders);
    const tabs = TABS.map((t) =>
      t.key === 'ALL' ? { ...t, count: orders.length } : { ...t, count: tabCounts[t.key] || 0 }
    );
    this.setData({
      orders,
      tabs,
      filteredOrders: this.filterByTab(orders, this.data.activeTab),
      isLoading: false,
    });
  },

  computeTabCounts(orders) {
    const counts = {};
    for (const o of orders) {
      counts[o.status] = (counts[o.status] || 0) + 1;
    }
    return counts;
  },

  filterByTab(orders, tab) {
    if (tab === 'ALL') return orders;
    return orders.filter((o) => o.status === tab);
  },

  onTabTap: function (e) {
    const tab = e.currentTarget.dataset.tab;
    this.setData({
      activeTab: tab,
      filteredOrders: this.filterByTab(this.data.orders, tab),
    });
  },

  onBack: function () {
    wx.navigateBack({ delta: 1 });
  },

  onSearch: function () {
    // 路线图 4.5:跳订单搜索页(占位 toast)
    wx.showToast({ title: '搜索开发中', icon: 'none' });
  },

  onOrderTap: function (e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: '/pages-sub/order/order-detail/order-detail?id=' + encodeURIComponent(id),
    });
  },

  // mp-od-prototype-alignment mp-08 诊断发现:OrderActionRow 组件 triggerEvent('action', {id})
  // 的 detail 只带 action 类型(如 'cancelOrder'),订单 id 是 wxml 上 data-id 挂的
  // (order-action-row 标签本身,同 onOrderTap/onTabTap 惯例)。旧代码从 e.detail 解构
  // {id, action} 两者都取错——action 恒 undefined 落进 default「未知操作」,
  // id 其实是 action 类型字符串。此前 OrderActionRow 组件未渲染时这段代码从未被真实调用过,
  // 组件渲染层修好后才暴露(点任何按钮都只会弹"未知操作",不做真实操作)。
  //
  // mp-cross-screen-cleanup D7:共享 dispatchOrderAction(action, order, refresh) 的
  // order 参数要求完整订单对象(退款分支需要 order.totalAmount),但这里卡片列表的
  // wxml 只挂了 data-id(订单 id 字符串),所以要先按 id 从 this.data.orders /
  // filteredOrders 里查出完整对象,再传给共享分发——这是 design.md D7 明确要求的
  // "一行 .find()",不是 wxml 改动。viewTracking(order-list 没有内联物流展示,只能
  // 跳详情页——跟 order-detail.js 的 viewLogistics 剪贴板复制合理地不同)在调用共享
  // 分发前自行短路,不进入共享 switch。
  onActionTap: function (e) {
    const action = e.detail.id;
    const orderId = e.currentTarget.dataset.id;
    const order =
      (this.data.orders || []).find((o) => o.id === orderId) ||
      (this.data.filteredOrders || []).find((o) => o.id === orderId);
    // task-6 review 发现:order 可能因列表在点击和查找之间被刷新而查不到(与
    // order-detail.js:106 的 `if (!order) return;` 同款防御——那边订单必然已加载,
    // 这里则是"卡片列表可能已过期"的真实可能性,此前少了这道守卫,会让
    // dispatchOrderAction 内部对 order.id/order.totalAmount 的裸解引用抛出未捕获
    // TypeError,而不是走其它分支已有的"操作失败" toast)。
    if (!order) return;
    if (action === 'viewTracking') {
      wx.navigateTo({
        url: '/pages-sub/order/order-detail/order-detail?id=' + encodeURIComponent(orderId),
      });
      return;
    }
    return dispatchOrderAction(action, order, this.fetchOrders.bind(this));
  },

  onRetry: function () {
    this.fetchOrders();
  },

  onGoShopping: function () {
    wx.switchTab({ url: '/pages/index/index' });
  },

  noop: function () {},
});
