App({
  onLaunch: function () {
    this.checkLoginStatus();
  },

  checkLoginStatus: function() {
    // 检查本地是否有token
    const token = wx.getStorageSync('token');
    if (token) {
      // 验证token有效性
      this.validateToken(token);
    }
  },

  validateToken: function(token) {
    const request = require('./utils/request.js');
    request({
      url: '/auth/me',
      header: {
        'Authorization': `Bearer ${token}`
      }
    })
    .then(res => {
      this.globalData.userInfo = res;
      this.globalData.token = token;
      console.log('Token验证成功:', res);
    })
    .catch(err => {
      console.error('Token验证失败:', err);
      // Token无效，清除本地存储
      wx.removeStorageSync('token');
      this.globalData.userInfo = null;
      this.globalData.token = null;
    });
  },

  wxLogin: function() {
    return new Promise((resolve, reject) => {
      wx.login({
        success: res => {
          if (res.code) {
            this.fetchWeChatToken(res.code)
              .then(data => {
                resolve(data);
              })
              .catch(err => {
                reject(err);
              });
          } else {
            reject(new Error('微信登录失败: ' + res.errMsg));
          }
        },
        fail: err => {
          reject(err);
        }
      });
    });
  },

  fetchWeChatToken: function(code) {
    const request = require('./utils/request.js');
    return request({
      url: '/auth/wx-login',
      method: 'POST',
      data: {
        code: code,
        // 可以从wx.getSetting获取用户信息
        nickname: '微信用户', // 实际应从用户授权获取
        avatarUrl: '' // 实际应从用户授权获取
      }
    })
    .then(res => {
      // 保存token和用户信息
      wx.setStorageSync('token', res.token);
      this.globalData.token = res.token;
      this.globalData.userInfo = {
        id: res.userId,
        nickname: res.nickname,
        avatarUrl: res.avatarUrl,
        role: res.role
      };
      return res;
    });
  },

  logout: function() {
    const request = require('./utils/request.js');
    const token = this.globalData.token;
    
    return request({
      url: '/auth/logout',
      method: 'POST',
      header: {
        'Authorization': `Bearer ${token}`
      }
    })
    .then(() => {
      // 清除本地存储
      wx.removeStorageSync('token');
      this.globalData.userInfo = null;
      this.globalData.token = null;
    });
  },

  globalData: {
    userInfo: null,
    token: null,
    baseUrl: 'http://localhost:8080/api' // 网关地址
  }
})
