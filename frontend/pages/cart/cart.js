const cartUtil = require('../../utils/cart.js');

// Mock coupon codes for demo
const COUPON_CODES = {
  'DISCOUNT10': { type: 'percentage', value: 10, minAmount: 100, description: '满100减10%' },
  'DISCOUNT20': { type: 'percentage', value: 20, minAmount: 200, description: '满200减20%' },
  'SAVE5': { type: 'fixed', value: 5, minAmount: 50, description: '满50减5元' },
  'SAVE20': { type: 'fixed', value: 20, minAmount: 100, description: '满100减20元' },
  'FREESHIP': { type: 'shipping', value: 0, minAmount: 0, description: '免运费' }
};

Page({
  data: {
    cartItems: [],
    totalPrice: 0,
    selectedPrice: 0,
    selectedItems: [],
    selectedAddress: null,
    couponCode: '',
    couponDiscount: 0,
    couponApplied: null,
    shippingFee: 0
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

    // Calculate shipping fee (free over 99)
    const shippingFee = selectedTotal >= 99 ? 0 : 10;

    // Calculate final price with coupon discount
    const discount = this.calculateDiscount(selectedTotal);
    const finalPrice = selectedTotal + shippingFee - discount;

    this.setData({
      cartItems: items,
      totalPrice: total.toFixed(2),
      selectedPrice: selectedTotal.toFixed(2),
      shippingFee: shippingFee,
      couponDiscount: discount
    });
  },

  // Calculate discount based on coupon
  calculateDiscount: function(subtotal) {
    const coupon = this.data.couponApplied;
    if (!coupon) return 0;

    if (subtotal < coupon.minAmount) return 0;

    if (coupon.type === 'percentage') {
      return Math.floor(subtotal * coupon.value / 100);
    } else if (coupon.type === 'fixed') {
      return coupon.value;
    }
    return 0;
  },

  // Apply coupon
  onApplyCoupon: function() {
    const code = this.data.couponCode.trim().toUpperCase();
    if (!code) {
      wx.showToast({ title: '请输入优惠券码', icon: 'none' });
      return;
    }

    const coupon = COUPON_CODES[code];
    if (!coupon) {
      wx.showToast({ title: '优惠券码无效', icon: 'none' });
      this.setData({ couponApplied: null, couponDiscount: 0 });
      this.refreshCart();
      return;
    }

    this.setData({ couponApplied: coupon });
    wx.showToast({ title: `已应用: ${coupon.description}`, icon: 'success' });
    this.refreshCart();
  },

  // Remove coupon
  onRemoveCoupon: function() {
    this.setData({
      couponCode: '',
      couponApplied: null,
      couponDiscount: 0
    });
    this.refreshCart();
    wx.showToast({ title: '已取消优惠券', icon: 'none' });
  },

  // Coupon code input
  onCouponInput: function(e) {
    this.setData({
      couponCode: e.detail.value
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
