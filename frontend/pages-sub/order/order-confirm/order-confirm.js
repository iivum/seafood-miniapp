const cartUtil = require('../../utils/cart.js');
const { OrderAPI } = require('../../src/api/order.js');
const { paymentModule } = require('../../src/modules/payment/payment.js');

Page({
  data: {
    cartItems: [],
    totalPrice: 0,
    shippingFee: 0,
    orderTotal: 0,
    selectedAddress: null,
    isCreating: false,
    isPaying: false,
    currentOrder: null
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
      url: '/pages-sub/user/address/address-list?selectMode=true&selectedAddress=' +
        encodeURIComponent(selectedAddress ? JSON.stringify(selectedAddress) : '')
    });
  },

  onSubmitOrder: function() {
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.navigateTo({
        url: '/pages-sub/user/login/login?redirect=' + encodeURIComponent('/pages-sub/order/order-confirm/order-confirm')
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

    if (!this.data.selectedAddress) {
      wx.showToast({
        title: '请选择收货地址',
        icon: 'none'
      });
      return;
    }

    this.setData({ isCreating: true });
    wx.showLoading({ title: '正在创建订单...' });

    const userId = app.globalData.userInfo.id;
    const cartId = 'cart_' + userId;

    OrderAPI.createOrder(userId, cartId)
      .then(order => {
        // Clear cart after order created
        cartUtil.clearCart();
        this.setData({
          isCreating: false,
          currentOrder: order
        });

        // Initiate payment
        this.initiatePayment(order);
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

  /**
   * Initiate WeChat Pay payment
   */
  initiatePayment: function(order) {
    this.setData({ isPaying: true });
    wx.showLoading({ title: '正在发起支付...' });

    const totalAmount = parseFloat(order.totalPrice);

    paymentModule.requestPayment(order.id, totalAmount)
      .then(result => {
        wx.hideLoading();
        this.setData({ isPaying: false });

        if (result.isSuccess()) {
          // Payment successful
          wx.showToast({
            title: '支付成功',
            icon: 'success'
          });

          // Navigate to order list after short delay
          setTimeout(() => {
            wx.redirectTo({
              url: '/pages-sub/order/order-list/order-list'
            });
          }, 1500);
        } else if (result.isCancelled()) {
          // User cancelled payment
          wx.showToast({
            title: '已取消支付',
            icon: 'none'
          });

          // Order is created but not paid, stay on order list
          setTimeout(() => {
            wx.redirectTo({
              url: '/pages-sub/order/order-list/order-list'
            });
          }, 1500);
        } else {
          // Payment failed
          wx.showToast({
            title: result.errorMessage || '支付失败',
            icon: 'none'
          });

          // Navigate to order list to show unpaid order
          setTimeout(() => {
            wx.redirectTo({
              url: '/pages-sub/order/order-list/order-list'
            });
          }, 1500);
        }
      })
      .catch(err => {
        wx.hideLoading();
        this.setData({ isPaying: false });
        console.error('Payment failed', err);
        wx.showToast({
          title: '支付失败',
          icon: 'none'
        });

        setTimeout(() => {
          wx.redirectTo({
            url: '/pages-sub/order/order-list/order-list'
          });
        }, 1500);
      });
  },

  goBack: function() {
    wx.navigateBack();
  }
})
