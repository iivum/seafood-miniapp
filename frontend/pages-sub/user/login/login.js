/**
 * Login page — wired to the new `features/auth` store per OpenSpec §8.4.
 * `authStore.login()` runs `wx.login` and exchanges the `code` for
 * an access/refresh token pair.
 */
const { authStore } = require('../../../src/features/auth/store');

Page({
  data: {
    agreement: true,
    errorMsg: '',
    isLoading: false,
  },

  onLoad: function () {
    if (authStore.getState().isAuthenticated) {
      wx.navigateBack();
    }
  },

  onShow: function () {
    if (authStore.getState().isAuthenticated) {
      wx.navigateBack();
    }
  },

  toggleAgreement: function (e) {
    this.setData({ agreement: e.detail.value });
  },

  wechatLogin: function () {
    if (!this.data.agreement) {
      this.setData({ errorMsg: '请同意用户协议和隐私政策' });
      return;
    }
    this.setData({ isLoading: true, errorMsg: '' });
    authStore
      .login()
      .then(() => {
        this.setData({ isLoading: false });
        wx.showToast({ title: '登录成功', icon: 'success' });
        setTimeout(() => wx.navigateBack(), 1500);
      })
      .catch((err) => {
        this.setData({
          isLoading: false,
          errorMsg: (err && err.message) || '登录失败，请重试',
        });
      });
  },

  onGetPhoneNumber: function (e) {
    if (!this.data.agreement) {
      wx.showToast({ title: '请同意用户协议和隐私政策', icon: 'none' });
      return;
    }
    if (e.detail.code) {
      this.setData({ isLoading: true, errorMsg: '' });
      // Phone-number flow is intentionally separate from the
      // wechat-code flow. The backend endpoint is /auth/wx-phone-login
      // (existing contract). Pages can opt into it later; for now we
      // delegate to the same wechatLogin() to keep the demo working.
      this.wechatLogin();
    } else {
      this.setData({ errorMsg: '需要授权手机号才能登录' });
    }
  },

  showAgreement: function () {
    wx.showModal({
      title: '用户协议',
      content: '用户协议内容将在这里显示...',
      showCancel: false,
      confirmText: '我知道了',
    });
  },

  showPrivacy: function () {
    wx.showModal({
      title: '隐私政策',
      content: '隐私政策内容将在这里显示...',
      showCancel: false,
      confirmText: '我知道了',
    });
  },

  onBack: function () {
    wx.navigateBack();
  },
});
