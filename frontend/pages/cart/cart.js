const cartUtil = require('../../utils/cart.js');

Page({
  data: {
    cartItems: [],
    totalPrice: 0,
    selectedPrice: 0,
    selectedItems: [],
    selectedAddress: null
  },

  onShow: function() {
    this.refreshCart();

    const pages = getCurrentPages();
    const currentPage = pages[pages.length - 1];

    if (currentPage.selectedAddressFromList) {
      this.setData({
        selectedAddress: currentPage.selectedAddressFromList
      });
      currentPage.selectedAddressFromList = null;
    }
  },

  refreshCart: function() {
    const items = cartUtil.getCart();
    let total = 0;
    items.forEach(item => {
      total += item.price * item.quantity;
    });

    // Calculate selected items price
    const selectedIds = this.data.selectedItems;
    let selectedTotal = 0;
    items.forEach(item => {
      if (selectedIds.includes(item.id)) {
        selectedTotal += item.price * item.quantity;
      }
    });

    this.setData({
      cartItems: items,
      totalPrice: total.toFixed(2),
      selectedPrice: selectedTotal.toFixed(2)
    });
  },

  // Toggle item selection
  onToggleSelect: function(e) {
    const id = e.currentTarget.dataset.id;
    const selectedItems = [...this.data.selectedItems];

    const index = selectedItems.indexOf(id);
    if (index > -1) {
      selectedItems.splice(index, 1);
    } else {
      selectedItems.push(id);
    }

    this.setData({ selectedItems });
    this.refreshCart();
  },

  // Toggle select all
  onToggleSelectAll: function() {
    if (this.data.selectedItems.length === this.data.cartItems.length) {
      this.setData({ selectedItems: [] });
    } else {
      const allIds = this.data.cartItems.map(item => item.id);
      this.setData({ selectedItems: allIds });
    }
    this.refreshCart();
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

    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.navigateTo({
        url: '/pages/login/login?redirect=' + encodeURIComponent('/pages/cart/cart')
      });
      return;
    }

    // 跳转到订单确认页面
    wx.navigateTo({
      url: '/pages/order-confirm/order-confirm'
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

  goToIndex: function() {
    wx.switchTab({ url: '/pages/index/index' });
  }
})
