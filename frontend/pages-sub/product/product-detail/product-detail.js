const request = require('../../utils/request.js');
const cartUtil = require('../../utils/cart.js');

Page({
  data: {
    product: {}
  },

  onLoad: function(options) {
    const id = options.id;
    this.fetchProductDetail(id);
  },

  fetchProductDetail: function(id) {
    request({ 
      url: `/products/${id}`,
      needAuth: true  // 需要认证
    })
    .then(res => {
      this.setData({ product: res });
    })
    .catch(err => {
      console.error('Fetch product detail failed', err);
      if (err.statusCode !== 401) { // 401错误已在request.js中处理
        wx.showToast({
          title: '加载商品失败',
          icon: 'none'
        });
      }
    });
  },

  onAddToCart: function() {
    cartUtil.addToCart(this.data.product);
    wx.showToast({ title: '已加入购物车', icon: 'success' });
  },

  onBuyNow: function() {
    const app = getApp();
    if (!app.globalData.userInfo) {
      // 未登录，跳转到登录页面
      wx.navigateTo({
        url: '/pages-sub/user/login/login'
      });
      return;
    }
    
    cartUtil.addToCart(this.data.product);
    wx.switchTab({ url: '/pages/cart/cart' });
  },

  goToHome: function() {
    wx.switchTab({ url: '/pages/index/index' });
  },

  goToCart: function() {
    wx.switchTab({ url: '/pages/cart/cart' });
  }
})
