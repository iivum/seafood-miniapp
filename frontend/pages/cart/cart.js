const cartUtil = require('../../utils/cart.js');

Page({
  data: {
    cartItems: [],
    totalPrice: 0
  },

  onShow: function() {
    this.refreshCart();
  },

  refreshCart: function() {
    const items = cartUtil.getCart();
    let total = 0;
    items.forEach(item => {
      total += item.price * item.quantity;
    });
    this.setData({
      cartItems: items,
      totalPrice: total.toFixed(2)
    });
  },

  onPlus: function(e) {
    const id = e.currentTarget.dataset.id;
    const item = this.data.cartItems.find(i => i.id === id);
    cartUtil.updateQuantity(id, item.quantity + 1);
    this.refreshCart();
  },

  onMinus: function(e) {
    const id = e.currentTarget.dataset.id;
    const item = this.data.cartItems.find(i => i.id === id);
    if (item.quantity > 1) {
      cartUtil.updateQuantity(id, item.quantity - 1);
      this.refreshCart();
    }
  },

  onRemove: function(e) {
    const id = e.currentTarget.dataset.id;
    cartUtil.removeFromCart(id);
    this.refreshCart();
  },

  onCheckout: function() {
    if (this.data.cartItems.length === 0) return;

    wx.showLoading({ title: '正在提交订单...' });
    
    const orderData = {
      userId: 'mock-user-123', // 实际应从全局获取
      items: this.data.cartItems.map(item => ({
        productId: item.id,
        productName: item.name,
        price: item.price,
        quantity: item.quantity
      })),
      shippingAddress: '上海市浦东新区某某路123号' // 实际应由用户选择
    };

    request({
      url: '/orders',
      method: 'POST',
      data: orderData
    })
    .then(res => {
      wx.hideLoading();
      wx.showToast({ title: '下单成功', icon: 'success' });
      cartUtil.clearCart();
      setTimeout(() => {
        wx.navigateTo({
          url: '/pages/order-list/order-list'
        });
      }, 1500);
    })
    .catch(err => {
      wx.hideLoading();
      console.error('Checkout failed', err);
    });
  },

  goToIndex: function() {
    wx.switchTab({ url: '/pages/index/index' });
  }
})
