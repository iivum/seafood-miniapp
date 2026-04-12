const cartUtil = require('../../utils/cart.js');
const { OrderAPI } = require('../../src/api/order');

Page({
  data: {
    cartItems: [],
    totalPrice: 0,
    shippingFee: 0,
    orderTotal: 0,
    selectedAddress: null,
    isCreating: false
  },

  onLoad: function(options) {
    this.refreshOrderPreview();
  },

  onShow: function() {
    this.refreshOrderPreview();

    // Check if returning from address selection
    const pages = getCurrentPages();
    const currentPage = pages[pages.length - 1];

    if (currentPage.selectedAddressFromList) {
      this.setData({
        selectedAddress: currentPage.selectedAddressFromList
      });
      currentPage.selectedAddressFromList = null;
    }
  },

  refreshOrderPreview: function() {
    const items = cartUtil.getCart();
    let itemsTotal = 0;
    items.forEach(item => {
      itemsTotal += item.price * item.quantity;
    });

    // Shipping fee: free for orders over 99, otherwise 10
    const shippingFee = itemsTotal >= 99 ? 0 : 10;
    const orderTotal = itemsTotal + shippingFee;

    this.setData({
      cartItems: items,
      totalPrice: itemsTotal.toFixed(2),
      shippingFee: shippingFee,
      orderTotal: orderTotal.toFixed(2)
    });
  },

  selectAddress: function() {
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.showToast({
        title: '请先登录',
        icon: 'none'
      });
      return;
    }

    const selectedAddress = this.data.selectedAddress || null;
    wx.navigateTo({
      url: '/pages/address/address-list?selectMode=true&selectedAddress=' +
        encodeURIComponent(selectedAddress ? JSON.stringify(selectedAddress) : '')
    });
  },

  onSubmitOrder: function() {
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.navigateTo({
        url: '/pages/login/login?redirect=' + encodeURIComponent('/pages/order-confirm/order-confirm')
      });
      return;
    }

    if (this.data.cartItems.length === 0) {
      wx.showToast({
        title: '购物车为空',
        icon: 'none'
      });
      return;
    }

    this.setData({ isCreating: true });
    wx.showLoading({ title: '正在创建订单...' });

    const userId = app.globalData.userInfo.id;
    const cartId = 'cart_' + userId; // Cart ID format matches backend expectation

    OrderAPI.createOrder(userId, cartId)
      .then(order => {
        wx.hideLoading();
        cartUtil.clearCart();
        this.setData({ isCreating: false });

        wx.showToast({
          title: '订单创建成功',
          icon: 'success'
        });

        // Navigate to order list after short delay
        setTimeout(() => {
          wx.redirectTo({
            url: '/pages/order-list/order-list'
          });
        }, 1500);
      })
      .catch(err => {
        wx.hideLoading();
        this.setData({ isCreating: false });
        console.error('Create order failed', err);
        wx.showToast({
          title: '创建订单失败',
          icon: 'none'
        });
      });
  },

  goBack: function() {
    wx.navigateBack();
  }
})
