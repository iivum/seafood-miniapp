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

    // Calculate shipping fee based on selected address and amount
    // TODO: 后端应根据收货地址计算实际运费
    let shippingFee = FREIGHT_BASE;
    if (selectedTotal >= FREIGHT_FREE_THRESHOLD) {
      shippingFee = 0;  // 满99免运费
    }

    // Calculate total price (前端暂时计算，后续由后端计算)
    const totalPrice = (selectedTotal + shippingFee).toFixed(2);

    this.setData({
      cartItems: items,
      totalPrice: totalPrice,
      selectedPrice: selectedTotal.toFixed(2),
      shippingFee: shippingFee
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

    this.setData({ selectedItems: selectedItems });
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
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    const id = e.currentTarget.dataset.id;
    const item = this.data.cartItems.find(i => i.id === id);
    cartUtil.updateQuantity(id, item.quantity + 1);
    this.refreshCart();
  },

  onMinus: function(e) {
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    const id = e.currentTarget.dataset.id;
    const item = this.data.cartItems.find(i => i.id === id);
    if (item.quantity > 1) {
      cartUtil.updateQuantity(id, item.quantity - 1);
      this.refreshCart();
    }
  },

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
    if (this.data.cartItems.length === 0) return;

    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.navigateTo({
        url: '/pages-sub/user/login/login?redirect=' + encodeURIComponent('/pages/cart/cart')
      });
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
  },

  goToIndex: function() {
    wx.switchTab({ url: '/pages/index/index' });
  }
})
