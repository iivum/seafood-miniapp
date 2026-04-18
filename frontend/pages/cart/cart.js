const cartUtil = require('../../utils/cart.js');

// TODO: 运费计算应移至后端，根据收货地址计算
// 当前前端暂时计算运费，后续需要调用后端API获取准确运费
const FREIGHT_BASE = 10;  // 基础运费
const FREIGHT_FREE_THRESHOLD = 99;  // 满99免运费（暂时由前端计算）

Page({
  data: {
    cartItems: [],
    totalPrice: 0,
    selectedPrice: 0,
    selectedItems: [],
    selectedAddress: null,
    shippingFee: FREIGHT_BASE
  },

  onShow: function() {
    // Check login status before allowing cart access
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.showToast({
        title: '请先登录',
        icon: 'none'
      });
      wx.navigateTo({
        url: '/pages-sub/user/login/login?redirect=' + encodeURIComponent('/pages/cart/cart')
      });
      return;
    }

    this.refreshCart();

    const pages = getCurrentPages();
    const currentPage = pages[pages.length - 1];

    if (currentPage.selectedAddressFromList) {
      this.setData({
        selectedAddress: currentPage.selectedAddressFromList
      });
      // TODO: 后端应根据收货地址计算运费
      currentPage.selectedAddressFromList = null;
    }
  },

  refreshCart: function() {
    const items = cartUtil.getCart();

    // Filter out stale selected items that no longer exist in cart (zombie bug fix)
    const validIds = items.map(item => item.id);
    const selectedItems = this.data.selectedItems.filter(id => validIds.includes(id));

    // Calculate selected items price
    let selectedTotal = 0;
    items.forEach(item => {
      if (selectedItems.includes(item.id)) {
        selectedTotal += item.price * item.quantity;
      }
    });

    // Calculate shipping fee based on selected address and amount
    // TODO: 后端应根据收货地址计算实际运费
    let shippingFee = FREIGHT_BASE;
    if (selectedTotal >= FREIGHT_FREE_THRESHOLD) {
      shippingFee = 0;  // 满99免运费
    }

    // Calculate total price (前端暂时计算，后续由后端计算)
    const totalPrice = parseFloat((selectedTotal + shippingFee).toFixed(2));

    this.setData({
      cartItems: items,
      totalPrice: totalPrice,
      selectedPrice: parseFloat(selectedTotal.toFixed(2)),
      selectedItems: selectedItems,
      shippingFee: shippingFee
    });
  },

  // Handle checkbox group change from native checkbox-group component
  onCheckboxChange: function(e) {
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    this.setData({ selectedItems: e.detail.value });
    this.refreshCart();
  },

  // Handle select all checkbox change
  onSelectAll: function(e) {
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    const allSelected = e.detail.checked;
    if (allSelected) {
      this.setData({ selectedItems: this.data.cartItems.map(item => item.id) });
    } else {
      this.setData({ selectedItems: [] });
    }
    this.refreshCart();
  },

  // Handle quantity change from native input type="number"
  onQuantityChange: function(e) {
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    const id = e.currentTarget.dataset.id;
    let quantity = parseInt(e.detail.value, 10) || 1;
    quantity = Math.max(1, quantity); // Enforce minimum of 1
    cartUtil.updateQuantity(id, quantity);
    this.refreshCart();
  },

  // Handle remove item
  onRemove: function(e) {
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    const id = e.currentTarget.dataset.id;
    cartUtil.removeFromCart(id);
    this.refreshCart();
  },

  onCheckout: function() {
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.navigateTo({
        url: '/pages-sub/user/login/login?redirect=' + encodeURIComponent('/pages/cart/cart')
      });
      return;
    }

    if (this.data.selectedItems.length === 0) {
      wx.showToast({ title: '请先选择商品', icon: 'none' });
      return;
    }

    // 跳转到订单确认页面
    wx.navigateTo({
      url: '/pages-sub/order/order-confirm/order-confirm'
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
  }
})
