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
    request({ url: `/products/${id}` })
      .then(res => {
        this.setData({ product: res });
      })
      .catch(err => {
        console.error('Fetch product detail failed', err);
      });
  },

  onAddToCart: function() {
    cartUtil.addToCart(this.data.product);
    wx.showToast({ title: '已加入购物车', icon: 'success' });
  },

  onBuyNow: function() {
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
