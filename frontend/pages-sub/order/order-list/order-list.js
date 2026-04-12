const { OrderAPI, ORDER_STATUS_TEXT } = require('../../src/api/order');

Page({
  data: {
    orders: [],
    statusTextMap: ORDER_STATUS_TEXT
  },

  onShow: function() {
    this.fetchOrders();
  },

  fetchOrders: function() {
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.navigateTo({
        url: '/pages-sub/user/login/login'
      });
      return;
    }

    const userId = app.globalData.userInfo.id;
    OrderAPI.getOrdersByUser(userId)
      .then(orders => {
        this.setData({ orders });
      })
      .catch(err => {
        console.error('Fetch orders failed', err);
        wx.showToast({
          title: '加载订单失败',
          icon: 'none'
        });
      });
  },

  onPay: function(e) {
    const id = e.currentTarget.dataset.id;
    wx.showLoading({ title: '正在发起支付...' });

    // For now, process payment with mock data
    // In production, this would integrate with WeChat Pay
    OrderAPI.processPayment(id, 'WECHAT_PAY', 0)
      .then(() => {
        wx.hideLoading();
        wx.showToast({ title: '支付成功', icon: 'success' });
        this.fetchOrders();
      })
      .catch(err => {
        wx.hideLoading();
        console.error('Payment failed', err);
      });
  },

  onCancel: function(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '确认取消订单',
      content: '确定要取消这个订单吗？',
      success: (res) => {
        if (res.confirm) {
          OrderAPI.cancelOrder(id, '用户取消订单')
            .then(() => {
              wx.showToast({ title: '已取消', icon: 'success' });
              this.fetchOrders();
            })
            .catch(err => {
              console.error('Cancel order failed', err);
            });
        }
      }
    });
  }
})
