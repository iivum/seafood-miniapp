const app = getApp();

Page({
  data: {
    agreement: true,
    errorMsg: '',
    isLoading: false
  },

  onLoad: function(options) {
    if (app.globalData.userInfo) {
      wx.navigateBack();
      return;
    }
  },

  onShow: function() {
    if (app.globalData.userInfo) {
      wx.navigateBack();
    }
  },

  toggleAgreement: function(e) {
    this.setData({
      agreement: e.detail.value
    });
  },

  wechatLogin: function() {
    if (!this.data.agreement) {
      this.setData({
        errorMsg: '请同意用户协议和隐私政策'
      });
      return;
    }

    this.setData({
      isLoading: true,
      errorMsg: ''
    });

    app.wxLogin()
      .then(res => {
        this.setData({
          isLoading: false
        });

        wx.showToast({
          title: '登录成功',
          icon: 'success'
        });

        setTimeout(() => {
          wx.navigateBack();
        }, 1500);
      })
      .catch(err => {
        console.error('微信登录失败:', err);
        this.setData({
          isLoading: false,
          errorMsg: err.message || '登录失败，请重试'
        });
      });
  },

  onGetPhoneNumber: function(e) {
    if (!this.data.agreement) {
      wx.showToast({
        title: '请同意用户协议和隐私政策',
        icon: 'none'
      });
      return;
    }

    if (e.detail.code) {
      // 用户同意授权手机号
      this.setData({
        isLoading: true,
        errorMsg: ''
      });

      this.wxPhoneLogin(e.detail.code, e.detail.encryptedData, e.detail.iv);
    } else {
      // 用户拒绝
      this.setData({
        errorMsg: '需要授权手机号才能登录'
      });
    }
  },

  wxPhoneLogin: function(code, encryptedData, iv) {
    const request = require('../../utils/request.js');

    request.request({
      url: '/auth/wx-phone-login',
      method: 'POST',
      data: {
        code: code,
        encryptedData: encryptedData,
        iv: iv
      }
    }).then(res => {
      if (res.success && res.data) {
        const app = getApp();
        wx.setStorageSync('token', res.data.token);
        app.globalData.token = res.data.token;
        app.globalData.userInfo = res.data;

        wx.showToast({
          title: '登录成功',
          icon: 'success'
        });

        setTimeout(() => {
          wx.navigateBack();
        }, 1500);
      } else {
        this.setData({
          isLoading: false,
          errorMsg: res.error || '登录失败'
        });
      }
    }).catch(err => {
      console.error('微信登录失败:', err);
      this.setData({
        isLoading: false,
        errorMsg: err.message || '登录失败，请重试'
      });
    });
  },

  showAgreement: function() {
    wx.showModal({
      title: '用户协议',
      content: '用户协议内容将在这里显示...\n\n请阅读并同意用户协议以继续使用我们的服务。',
      showCancel: false,
      confirmText: '我知道了'
    });
  },

  showPrivacy: function() {
    wx.showModal({
      title: '隐私政策',
      content: '隐私政策内容将在这里显示...\n\n我们重视您的隐私保护，请放心使用我们的服务。',
      showCancel: false,
      confirmText: '我知道了'
    });
  },

  onBack: function() {
    wx.navigateBack();
  }
});
