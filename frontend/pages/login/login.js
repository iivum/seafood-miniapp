const app = getApp();

Page({
  data: {
    activeTab: 'wechat', // wechat, phone
    phone: '',
    verifyCode: '',
    password: '',
    agreement: true,
    errorMsg: '',
    isLoading: false,
    isCounting: false,
    countDown: 0,
    loginMode: 'login' // login, register
  },

  // 生命周期函数
  onLoad: function(options) {
    // 检查是否已经登录
    if (app.globalData.userInfo) {
      wx.navigateBack();
      return;
    }
  },

  onShow: function() {
    // 页面显示时检查登录状态
    if (app.globalData.userInfo) {
      wx.navigateBack();
    }
  },

  // 切换登录方式
  switchTab: function(e) {
    const tab = e.currentTarget.dataset.tab;
    this.setData({
      activeTab: tab,
      errorMsg: ''
    });
  },

  // 微信输入处理
  onPhoneInput: function(e) {
    const phone = e.detail.value;
    this.setData({
      phone: phone,
      errorMsg: ''
    });
    this.validateForm();
  },

  // 验证码输入处理
  onVerifyCodeInput: function(e) {
    const verifyCode = e.detail.value;
    this.setData({
      verifyCode: verifyCode,
      errorMsg: ''
    });
    this.validateForm();
  },

  // 密码输入处理
  onPasswordInput: function(e) {
    const password = e.detail.value;
    this.setData({
      password: password,
      errorMsg: ''
    });
    this.validateForm();
  },

  // 切换协议同意
  toggleAgreement: function(e) {
    this.setData({
      agreement: e.detail.value
    });
    this.validateForm();
  },

  // 表单验证
  validateForm: function() {
    const { activeTab, phone, verifyCode, agreement, password, loginMode } = this.data;
    
    if (activeTab === 'wechat') {
      // 微信登录只需要同意协议
      return agreement;
    } else {
      // 手机号登录需要验证手机号和验证码
      const isPhoneValid = this.validatePhone(phone);
      const isVerifyCodeValid = /^\d{6}$/.test(verifyCode);
      const isAgreementValid = agreement;
      
      if (loginMode === 'register') {
        // 注册模式还需要验证密码
        const isPasswordValid = password && password.length >= 6;
        return isPhoneValid && isVerifyCodeValid && isPasswordValid && isAgreementValid;
      }
      
      return isPhoneValid && isVerifyCodeValid && isAgreementValid;
    }
  },

  // 验证手机号
  validatePhone: function(phone) {
    return /^1[3-9]\d{9}$/.test(phone);
  },

  // 获取验证码
  getVerifyCode: function() {
    const { phone } = this.data;
    
    if (!this.validatePhone(phone)) {
      this.setData({
        errorMsg: '请输入正确的手机号'
      });
      return;
    }

    // 开始倒计时
    this.startCountDown();
    
    // 调用获取验证码接口
    const request = require('../../utils/request.js');
    request({
      url: '/auth/verify-code',
      method: 'POST',
      data: {
        phone: phone
      }
    })
    .then(res => {
      wx.showToast({
        title: '验证码已发送',
        icon: 'success'
      });
    })
    .catch(err => {
      console.error('获取验证码失败:', err);
      wx.showToast({
        title: '获取验证码失败',
        icon: 'none'
      });
      // 获取验证码失败，重置倒计时
      this.resetCountDown();
    });
  },

  // 开始倒计时
  startCountDown: function() {
    let countDown = 60;
    this.setData({
      isCounting: true,
      countDown: countDown,
      countDownText: `${countDown}s后重新获取`
    });

    const timer = setInterval(() => {
      countDown--;
      if (countDown <= 0) {
        clearInterval(timer);
        this.resetCountDown();
      } else {
        this.setData({
          countDown: countDown,
          countDownText: `${countDown}s后重新获取`
        });
      }
    }, 1000);
  },

  // 重置倒计时
  resetCountDown: function() {
    this.setData({
      isCounting: false,
      countDown: 0,
      countDownText: '获取验证码'
    });
  },

  // 微信登录
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
        
        // 延迟返回上一页
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

  // 手机号登录/注册
  phoneLogin: function() {
    if (!this.validateForm()) {
      this.setData({
        errorMsg: '请填写完整的登录信息'
      });
      return;
    }

    this.setData({
      isLoading: true,
      errorMsg: ''
    });

    const request = require('../../utils/request.js');
    const { phone, verifyCode, password, loginMode } = this.data;
    
    // 调用手机号登录/注册接口
    request({
      url: '/auth/phone-login',
      method: 'POST',
      data: {
        phone: phone,
        verifyCode: verifyCode,
        password: password,
        loginMode: loginMode
      }
    })
    .then(res => {
      // 保存token和用户信息
      wx.setStorageSync('token', res.token);
      app.globalData.token = res.token;
      app.globalData.userInfo = {
        id: res.userId,
        nickname: res.nickname || '用户' + phone.slice(-4),
        avatarUrl: res.avatarUrl || '',
        phone: phone,
        role: res.role || 'USER'
      };

      this.setData({
        isLoading: false
      });
      
      wx.showToast({
        title: '登录成功',
        icon: 'success'
      });
      
      // 延迟返回上一页
      setTimeout(() => {
        wx.navigateBack();
      }, 1500);
    })
    .catch(err => {
      console.error('手机号登录失败:', err);
      this.setData({
        isLoading: false,
        errorMsg: err.message || '登录失败，请重试'
      });
    });
  },

  // 显示用户协议
  showAgreement: function() {
    wx.showModal({
      title: '用户协议',
      content: '用户协议内容将在这里显示...\n\n请阅读并同意用户协议以继续使用我们的服务。',
      showCancel: false,
      confirmText: '我知道了'
    });
  },

  // 显示隐私政策
  showPrivacy: function() {
    wx.showModal({
      title: '隐私政策',
      content: '隐私政策内容将在这里显示...\n\n我们重视您的隐私保护，请放心使用我们的服务。',
      showCancel: false,
      confirmText: '我知道了'
    });
  },

  // 页面返回
  onBack: function() {
    wx.navigateBack();
  },

  // 登录成功后的处理
  handleLoginSuccess: function(res) {
    // 保存token和用户信息
    wx.setStorageSync('token', res.token);
    this.globalData.token = res.token;
    this.globalData.userInfo = {
      id: res.userId,
      nickname: res.nickname || '用户',
      avatarUrl: res.avatarUrl || '',
      phone: res.phone || '',
      role: res.role || 'USER'
    };
  }
})