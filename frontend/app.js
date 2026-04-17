App({
  onLaunch: function () {
    this.initPlatformInfo();
    this.checkLoginStatus();
  },

  /**
   * Initialize platform-specific settings
   * Detects PC vs mobile and sets up appropriate handlers
   */
  initPlatformInfo: function() {
    const systemInfo = wx.getSystemInfoSync();
    const platform = systemInfo.platform;
    const isPC = platform === 'windows' || platform === 'mac';

    this.globalData.platform = platform;
    this.globalData.isPC = isPC;
    this.globalData.windowWidth = systemInfo.windowWidth;
    this.globalData.windowHeight = systemInfo.windowHeight;

    // Set up window resize handler for PC
    if (isPC) {
      this.setupPCResizeHandler();
      this.setupPCKeyboardHandler();
    }

    console.log('Platform info:', platform, 'Is PC:', isPC, 'Window:', systemInfo.windowWidth, 'x', systemInfo.windowHeight);
  },

  /**
   * Set up window resize handler for PC adaptation
   * Dynamically adjusts layout based on window size
   */
  setupPCResizeHandler: function() {
    let debounceTimer = null;
    const debounceDelay = 200;

    wx.onWindowResize((res) => {
      // Debounce resize events to avoid excessive updates
      if (debounceTimer) {
        clearTimeout(debounceTimer);
      }

      debounceTimer = setTimeout(() => {
        const { windowWidth, windowHeight } = res.size;
        this.globalData.windowWidth = windowWidth;
        this.globalData.windowHeight = windowHeight;

        // Determine display mode based on aspect ratio / width
        const isWideScreen = windowWidth > 768;
        const displayMode = isWideScreen ? '双栏' : '单栏';

        console.log('Window resized:', windowWidth, 'x', windowHeight, 'Mode:', displayMode);

        // Broadcast resize event for pages to handle
        const eventChannel = this.eventChannel;
        if (eventChannel) {
          eventChannel.emit && eventChannel.emit('windowResize', {
            windowWidth,
            windowHeight,
            isWideScreen,
            displayMode
          });
        }
      }, debounceDelay);
    });
  },

  /**
   * Set up PC keyboard event handlers
   * Supports keyboard navigation and shortcuts
   */
  setupPCKeyboardHandler: function() {
    // Track modifier keys state
    this.globalData.keyState = {
      ctrl: false,
      shift: false,
      alt: false
    };

    // Key up handler
    wx.onKeyUp((res) => {
      const { keyCode, key } = res;
      console.log('Key up:', key, keyCode);

      // Update modifier key state
      if (keyCode === 17) this.globalData.keyState.ctrl = false;
      if (keyCode === 16) this.globalData.keyState.shift = false;
      if (keyCode === 18) this.globalData.keyState.alt = false;

      // Broadcast keyboard event
      const eventChannel = this.globalData.eventChannel;
      if (eventChannel && eventChannel.emit) {
        eventChannel.emit('keyUp', { keyCode, key });
      }
    });

    // Key down handler
    wx.onKeyDown((res) => {
      const { keyCode, key } = res;
      console.log('Key down:', key, keyCode);

      // Update modifier key state
      if (keyCode === 17) this.globalData.keyState.ctrl = true;
      if (keyCode === 16) this.globalData.keyState.shift = true;
      if (keyCode === 18) this.globalData.keyState.alt = true;

      // Broadcast keyboard event
      const eventChannel = this.globalData.eventChannel;
      if (eventChannel && eventChannel.emit) {
        eventChannel.emit('keyDown', { keyCode, key });
      }
    });
  },

  /**
   * Get platform info
   * @returns {object} Platform information
   */
  getPlatformInfo: function() {
    return {
      platform: this.globalData.platform,
      isPC: this.globalData.isPC,
      windowWidth: this.globalData.windowWidth,
      windowHeight: this.globalData.windowHeight
    };
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
      url: '/api/auth/me',
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
      url: '/api/auth/wx-login',
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
      url: '/api/auth/logout',
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
    baseUrl: 'http://localhost:8080/api', // 网关地址
    // Platform info
    platform: 'unknown',
    isPC: false,
    windowWidth: 375,
    windowHeight: 667,
    // Event channel for cross-page communication
    eventChannel: null
  }
})
