App({
  onLaunch: function () {
    // 微信登录
    wx.login({
      success: res => {
        // 发送 res.code 到后台换取 openId, sessionKey, unionId
        if (res.code) {
          // TODO: 微信登录接口
        }
      }
    })
  },
  globalData: {
    userInfo: null,
    baseUrl: 'http://localhost:8080/api' // 网关地址
  }
})
