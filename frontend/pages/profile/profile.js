const app = getApp();

Page({
  data: {
    userInfo: null
  },

  onShow: function() {
    this.refreshUserInfo();
  },

  refreshUserInfo: function() {
    // 获取用户信息
    if (app.globalData.userInfo) {
      this.setData({ userInfo: app.globalData.userInfo });
    } else {
      this.setData({ userInfo: null });
    }
  },

  goToOrderList: function() {
    if (!app.globalData.userInfo) {
      wx.showToast({
        title: '请先登录',
        icon: 'none'
      });
      return;
    }
    wx.navigateTo({
      url: '/pages-sub/order/order-list/order-list'
    });
  },

  goToMerchant: function() {
    if (!app.globalData.userInfo) {
      wx.showToast({
        title: '请先登录',
        icon: 'none'
      });
      return;
    }
    wx.navigateTo({
      url: '/pages-sub/merchant/merchant/merchant'
    });
  },

  // 用户登录
  onLogin: function() {
    if (app.globalData.userInfo) {
      // 已登录，显示用户信息
      wx.showToast({
        title: '您已登录',
        icon: 'none'
      });
      return;
    }

    // 跳转到登录页面
    wx.navigateTo({
      url: '/pages-sub/user/login/login'
    });
  },

  // 用户登出
  onLogout: function() {
    if (!app.globalData.userInfo) {
      wx.showToast({
        title: '您尚未登录',
        icon: 'none'
      });
      return;
    }

    wx.showModal({
      title: '确认登出',
      content: '确定要退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          app.logout()
            .then(() => {
              this.refreshUserInfo();
              wx.showToast({
                title: '已退出登录',
                icon: 'success'
              });
            })
            .catch(err => {
              console.error('登出失败:', err);
              wx.showToast({
                title: '登出失败',
                icon: 'none'
              });
            });
        }
      }
    });
  }
})
