Page({
  goToProductManage: function() {
    wx.navigateTo({
      url: '/pages/merchant/product-manage/product-manage'
    });
  },

  goToOrderManage: function() {
    wx.navigateTo({
      url: '/pages/merchant/order-manage/order-manage'
    });
  }
})
