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
const { OrderAPI } = require('../../../src/features/order/api');
const { cartStore } = require('../../../src/features/cart/store');
const { wx } = require('../../../src/shared/wx');

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

  onShow: function () {
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
      url: '/pages-sub/order/order-confirm/order-confirm?id=' + encodeURIComponent(id),
    });
  },

  onActionTap: function (e) {
    const { id, action } = e.detail;
    this.handleAction(action, id);
  },

  /**
   * 7.3:action 按钮统一入口。
   * 成功 → refresh;409 → toast「订单状态已变更」+ refresh;403/404 → toast「订单不存在或无权限」。
   */
  handleAction: async function (action, orderId) {
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
        case 'confirmReceipt':
          await this.confirmThenReceive(orderId);
          break;
        case 'reorder':
          await this.handleRebuy(orderId);
          return; // rebuy 自行 refresh
        case 'requestRefund':
        case 'afterSale':
          wx.hideLoading();
          this.openRefundSheet(orderId);
          return;
        case 'withdrawRefund':
          wx.showToast({ title: '撤回功能开发中', icon: 'none' });
          break;
        case 'viewTracking':
          wx.hideLoading();
          wx.navigateTo({
            url: '/pages-sub/order/order-detail/order-detail?id=' + encodeURIComponent(orderId),
          });
          return;
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
      await this.fetchOrders();
    } catch (err) {
      wx.hideLoading();
      const status = err && err.status;
      if (status === 409) {
        wx.showToast({ title: '订单状态已变更', icon: 'none' });
        await this.fetchOrders();
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

  confirmThenReceive(orderId) {
    return new Promise((resolve, reject) => {
      wx.showModal({
        title: '确认收货',
        content: '请确认已收到商品,确认后无法再申请退款',
        success: (res) => {
          if (res.confirm) {
            OrderAPI.confirmReceive(orderId).then(resolve).catch(reject);
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
            // 删除 = 后端 cancel + 隐藏(本迭代没 delete 端点,走 cancel 替代)
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
      // 把 rebuy 返回的 cart items 加到 cart store
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

  openRefundSheet(_orderId) {
    // 简化版:弹 modal 让用户填金额 + 原因
    wx.showModal({
      title: '申请退款',
      content: '退款功能开发中,Sprint 3 上线',
      showCancel: false,
    });
  },

  onRetry: function () {
    this.fetchOrders();
  },

  onGoShopping: function () {
    wx.switchTab({ url: '/pages/index/index' });
  },

  noop: function () {},
});
