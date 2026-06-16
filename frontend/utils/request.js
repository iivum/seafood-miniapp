// sprint-1-closure 8.x: 延迟 getApp() 到调用时,模块加载时 getApp() 返 undefined
// 导致 "Cannot read property 'globalData' of undefined" 错误
function getAppSafe() {
  try {
    return getApp();
  } catch (e) {
    return null;
  }
}

const request = (options) => {
  return new Promise((resolve, reject) => {
    const { url, method = 'GET', data = {}, header = {}, needAuth = false } = options;
    const app = getAppSafe();
    if (!app) {
      reject(new Error('App instance not ready'));
      return;
    }
    const baseUrl = app.globalData.baseUrl;

    // 构建请求头
    const requestHeader = {
      'Content-Type': 'application/json',
      ...header
    };

    // 如果需要认证且存在token，添加Authorization头
    if (needAuth && app.globalData.token) {
      requestHeader['Authorization'] = `Bearer ${app.globalData.token}`;
    }

    wx.request({
      url: baseUrl + url,
      method,
      data,
      header: requestHeader,
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data);
        } else if (res.statusCode === 401) {
          // Token过期或无效
          wx.showToast({
            title: '登录已过期，请重新登录',
            icon: 'none'
          });

          // 清除无效的token
          wx.removeStorageSync('token');
          if (app) {
            app.globalData.token = null;
            app.globalData.userInfo = null;
          }
          
          // 跳转到登录页面
          setTimeout(() => {
            wx.reLaunch({
              url: '/pages/index/index'
            });
          }, 1500);
          
          reject(res);
        } else {
          // 其他错误
          const errorMessage = res.data?.message || `请求失败: ${res.statusCode}`;
          wx.showToast({
            title: errorMessage,
            icon: 'none'
          });
          reject(res);
        }
      },
      fail: (err) => {
        console.error('网络请求失败:', err);
        wx.showToast({
          title: '网络连接失败，请检查网络',
          icon: 'none'
        });
        reject(err);
      }
    });
  });
};

// 专门用于需要认证的请求
const authRequest = (options) => {
  return request({
    ...options,
    needAuth: true
  });
};

module.exports = {
  request,
  authRequest
};
