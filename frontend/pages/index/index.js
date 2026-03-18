const request = require('../../utils/request.js');
const cartUtil = require('../../utils/cart.js');

Page({
  data: {
    categories: [
      { name: '鱼类', icon: '/images/icons/fish.png' },
      { name: '虾蟹', icon: '/images/icons/shrimp.png' },
      { name: '贝类', icon: '/images/icons/shell.png' },
      { name: '活鲜', icon: '/images/icons/live.png' }
    ],
    products: []
  },

  onLoad: function() {
    this.fetchProducts();
  },

  onShow: function() {
    // 页面显示时刷新购物车角标（如果需要的话）
  },

  fetchProducts: function() {
    request({ 
      url: '/products',
      needAuth: true  // 需要认证
    })
      .then(res => {
        this.setData({ products: res });
      })
      .catch(err => {
        console.error('Fetch products failed', err);
        if (err.statusCode !== 401) { // 401错误已在request.js中处理
          wx.showToast({
            title: '加载商品失败',
            icon: 'none'
          });
        }
      });
  },

  goToDetail: function(e) {
    const app = getApp();
    if (!app.globalData.userInfo) {
      // 未登录，跳转到登录页面
      wx.navigateTo({
        url: '/pages/login/login'
      });
      return;
    }
    
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: `/pages/product-detail/product-detail?id=${id}`
    });
  },

  // 添加商品到购物车
  addToCart: function(e) {
    const product = e.currentTarget.dataset.product;
    cartUtil.addToCart(product);
    wx.showToast({
      title: '已加入购物车',
      icon: 'success',
      duration: 1500
    });
  },

  // 搜索功能
  onSearch: function() {
    wx.showToast({
      title: '搜索功能开发中...',
      icon: 'none'
    });
  }
})
