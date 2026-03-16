const app = getApp();

Page({
  data: {
    userInfo: {
      nickname: '海鲜商户',
      avatarUrl: '',
      role: 'MERCHANT' // 模拟商户角色
    }
  },

  onShow: function() {
    // 获取用户信息
    if (app.globalData.userInfo) {
      this.setData({ userInfo: app.globalData.userInfo });
    }
  },

  goToOrderList: function() {
    wx.navigateTo({
      url: '/pages/order-list/order-list'
    });
  },

  goToMerchant: function() {
    wx.navigateTo({
      url: '/pages/merchant/merchant'
    });
  }
})
