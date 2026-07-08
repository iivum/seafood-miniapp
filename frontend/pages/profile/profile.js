/**
 * Profile page — uses the new `features/auth` store for logout
 * (OpenSpec §8.4)。收藏 + 浏览足迹:onShow 额外拉 UserAPI.me() 刷新
 * favoriteCount/viewCount(authStore 缓存的 user 只在登录时更新一次,收藏/
 * 取消收藏后返回本页不会自动变新,需要真实网络请求刷新)。
 */
const { authStore } = require('../../src/features/auth/store');
const { UserAPI } = require('../../src/features/user/api');

Page({
  data: {
    userInfo: null,
    favoriteCount: 0,
    viewCount: 0,
  },

  onShow: function () {
    this.refreshUserInfo();
  },

  refreshUserInfo: function () {
    const state = authStore.getState();
    this.setData({ userInfo: state.user });
    if (!state.isAuthenticated) return;
    UserAPI.me()
      .then((u) => {
        this.setData({ favoriteCount: (u && u.favoriteCount) || 0, viewCount: (u && u.viewCount) || 0 });
      })
      .catch(() => {
        // best-effort:附加请求失败不阻断页面其它渲染,静默降级为 0
      });
  },

  goToOrderList: function () {
    if (!authStore.getState().isAuthenticated) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    wx.navigateTo({ url: '/pages-sub/order/order-list/order-list' });
  },

  onGoFavorites: function () {
    if (!authStore.getState().isAuthenticated) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    wx.navigateTo({ url: '/pages-sub/user/favorites/favorites-list' });
  },

  onGoFootprints: function () {
    if (!authStore.getState().isAuthenticated) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    wx.navigateTo({ url: '/pages-sub/user/footprints/footprints-list' });
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
