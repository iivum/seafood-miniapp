Page({
  goToDashboard: function() {
    wx.navigateTo({
      url: '/pages-sub/merchant/dashboard/dashboard'
    });
  },

  goToProductManage: function() {
    wx.navigateTo({
      url: '/pages-sub/merchant/product-manage/product-manage'
    });
  },

  goToOrderManage: function() {
    wx.navigateTo({
      url: '/pages-sub/merchant/order-manage/order-manage'
    });
  },

  goToUserManage: function() {
    wx.navigateTo({
      url: '/pages-sub/merchant/user-manage/user-manage'
    });
  }
})
