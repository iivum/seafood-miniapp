/**
 * Profile page — uses the new `features/auth` store for logout
 * (OpenSpec §8.4).
 */
const { authStore } = require('../../src/features/auth/store');

Page({
  data: {
    userInfo: null,
  },

  onShow: function () {
    this.refreshUserInfo();
  },

  refreshUserInfo: function () {
    const state = authStore.getState();
    this.setData({ userInfo: state.user });
  },

  goToOrderList: function () {
    if (!authStore.getState().isAuthenticated) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    wx.navigateTo({ url: '/pages-sub/order/order-list/order-list' });
  },

  onLogin: function () {
    if (authStore.getState().isAuthenticated) {
      wx.showToast({ title: '您已登录', icon: 'none' });
      return;
    }
    wx.navigateTo({ url: '/pages-sub/user/login/login' });
  },

  onContactService: function () {
    wx.showToast({ title: '联系客服开发中', icon: 'none' });
  },

  onAboutUs: function () {
    wx.showToast({ title: '关于我们开发中', icon: 'none' });
  },

  onLogout: function () {
    if (!authStore.getState().isAuthenticated) {
      wx.showToast({ title: '您尚未登录', icon: 'none' });
      return;
    }
    wx.showModal({
      title: '确认登出',
      content: '确定要退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          authStore
            .logout()
            .then(() => {
              this.refreshUserInfo();
              wx.showToast({ title: '已退出登录', icon: 'success' });
            })
            .catch(() => {
              wx.showToast({ title: '登出失败', icon: 'none' });
            });
        }
      },
    });
  },
});
