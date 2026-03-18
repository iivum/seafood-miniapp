const request = require('../../utils/request.js');

Page({
  data: {
    orders: [],
    statusTextMap: {
      'PENDING_PAYMENT': '待付款',
      'PAID': '已支付',
      'SHIPPED': '已发货',
      'COMPLETED': '已完成',
      'CANCELLED': '已取消'
    }
  },

  onShow: function() {
    this.fetchOrders();
  },

  fetchOrders: function() {
    const app = getApp();
    if (!app.globalData.userInfo) {
      // 未登录，跳转到登录页面
      wx.navigateTo({
        url: '/pages/login/login'
      });
      return;
    }
    
    const userId = app.globalData.userInfo.id;
    request({ 
      url: `/orders/user/${userId}`,
      needAuth: true  // 需要认证
    })
    .then(res => {
      this.setData({ orders: res });
    })
    .catch(err => {
      console.error('Fetch orders failed', err);
      if (err.statusCode !== 401) { // 401错误已在request.js中处理
        wx.showToast({
          title: '加载订单失败',
          icon: 'none'
        });
      }
    });
  },

  onPay: function(e) {
    const id = e.currentTarget.dataset.id;
    wx.showLoading({ title: '正在发起支付...' });
    request({
      url: `/orders/${id}/pay`,
      method: 'POST'
    })
    .then(res => {
      wx.hideLoading();
      wx.showToast({ title: '支付成功', icon: 'success' });
      this.fetchOrders();
    })
    .catch(err => {
      wx.hideLoading();
    });
  },

  onCancel: function(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '确认取消订单',
      content: '确定要取消这个订单吗？',
      success: (res) => {
        if (res.confirm) {
          request({
            url: `/orders/${id}/cancel`,
            method: 'POST'
          })
          .then(res => {
            wx.showToast({ title: '已取消', icon: 'success' });
            this.fetchOrders();
          });
        }
      }
    });
  }
})
