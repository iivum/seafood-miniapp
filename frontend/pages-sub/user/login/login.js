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
