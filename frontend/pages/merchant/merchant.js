Page({
  goToDashboard: function() {
    wx.navigateTo({
      url: '/pages/merchant/dashboard/dashboard'
    });
  },

  goToProductManage: function() {
    wx.navigateTo({
      url: '/pages/merchant/product-manage/product-manage'
    });
  },

  goToOrderManage: function() {
    wx.navigateTo({
      url: '/pages/merchant/order-manage/order-manage'
    });
  },

  goToUserManage: function() {
    wx.navigateTo({
      url: '/pages/merchant/user-manage/user-manage'
    });
  }
})
