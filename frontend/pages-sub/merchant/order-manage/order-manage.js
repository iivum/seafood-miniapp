const app = getApp();
const request = require('../../../utils/request.js');
const { OrderAPI } = require('../../../api/order.js');

Page({
  data: {
    orders: [],
    isLoading: false,
    isEmpty: false,
    selectedStatus: 'ALL',
    statusOptions: [
      { value: 'ALL', label: '全部' },
      { value: 'PENDING_PAYMENT', label: '待付款' },
      { value: 'PAID', label: '已付款' },
      { value: 'SHIPPED', label: '已发货' },
      { value: 'DELIVERED', label: '已收货' },
      { value: 'CANCELLED', label: '已取消' }
    ]
  },

  onLoad: function() {
    this.loadOrders();
  },

  onShow: function() {
    this.loadOrders();
  },

  onStatusChange: function(e) {
    const status = e.currentTarget.dataset.status;
    this.setData({ selectedStatus: status });
    this.loadOrders();
  },

  loadOrders: function() {
    const self = this;
    self.setData({ isLoading: true });

    const status = self.data.selectedStatus !== 'ALL' ? self.data.selectedStatus : undefined;

    OrderAPI.getOrdersByUser(app.globalData.userInfo.id, status)
    .then(orders => {
      self.setData({
        orders: orders || [],
        isLoading: false,
        isEmpty: !orders || orders.length === 0
      });
    })
    .catch(err => {
      console.error('Load orders failed', err);
      self.setData({ isLoading: false, isEmpty: true });
      wx.showToast({ title: '加载失败', icon: 'none' });
    });
  },

  onShipOrder: function(e) {
    const order = e.currentTarget.dataset.order;
    const trackingNumber = '-tracking-' + Date.now();

    wx.showModal({
      title: '确认发货',
      content: `订单号: ${order.orderNumber}\n是否确认发货？`,
      success: (res) => {
        if (res.confirm) {
          this.updateOrderStatus(order.id, 'ship', trackingNumber);
        }
      }
    });
  },

  onCompleteOrder: function(e) {
    const order = e.currentTarget.dataset.order;

    wx.showModal({
      title: '确认完成',
      content: `订单号: ${order.orderNumber}\n是否确认完成订单？`,
      success: (res) => {
        if (res.confirm) {
          this.updateOrderStatus(order.id, 'complete');
        }
      }
    });
  },

  onCancelOrder: function(e) {
    const order = e.currentTarget.dataset.order;

    wx.showModal({
      title: '确认取消',
      content: `订单号: ${order.orderNumber}\n是否取消订单？`,
      success: (res) => {
        if (res.confirm) {
          this.updateOrderStatus(order.id, 'cancel', 'admin cancelled');
        }
      }
    });
  },

  updateOrderStatus: function(orderId, action, param) {
    const self = this;
    let promise;

    if (action === 'ship') {
      promise = OrderAPI.shipOrder(orderId, param || '');
    } else if (action === 'cancel') {
      promise = OrderAPI.cancelOrder(orderId, param || '');
    } else if (action === 'complete') {
      promise = OrderAPI.completeOrder(orderId);
    } else {
      return;
    }

    promise
    .then(() => {
      wx.showToast({ title: '操作成功', icon: 'success' });
      self.loadOrders();
    })
    .catch(err => {
      console.error('Update order failed', err);
      wx.showToast({ title: '操作失败', icon: 'none' });
    });
  },

  getStatusText: function(status) {
    const statusMap = {
      'PENDING_PAYMENT': '待付款',
      'PAID': '已付款',
      'SHIPPED': '已发货',
      'DELIVERED': '已收货',
      'COMPLETED': '已完成',
      'CANCELLED': '已取消',
      'REFUNDED': '已退款'
    };
    return statusMap[status] || status;
  },

  getStatusClass: function(status) {
    const classMap = {
      'PENDING_PAYMENT': 'status-pending',
      'PAID': 'status-paid',
      'SHIPPED': 'status-shipped',
      'DELIVERED': 'status-delivered',
      'COMPLETED': 'status-completed',
      'CANCELLED': 'status-cancelled',
      'REFUNDED': 'status-refunded'
    };
    return classMap[status] || '';
  },

  goBack: function() {
    wx.navigateBack();
  }
})
