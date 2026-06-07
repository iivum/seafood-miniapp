/**
 * Cart page — wired to the new `features/cart` store + API per
 * OpenSpec §8.5. The store pulls from the server-side cart endpoints
 * (GET /api/cart, POST /api/cart/items, etc.) and exposes imperative
 * actions for add/remove/update/toggle/clear. The page binds the
 * store state to its `data` and forwards user actions to the store.
 */
const { cartStore } = require('../../src/features/cart/store');
const cartUtil = require('../../utils/cart.js');

Page({
  data: {
    cartItems: [],
    totalPrice: '0.00',
    selectedPrice: '0.00',
    selectedItems: [],
    selectedAddress: null,
    shippingFee: 0,
    isLoading: false,
    isError: false,
    errorMessage: '',
  },

  onShow: function () {
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      wx.navigateTo({
        url: '/pages-sub/user/login/login?redirect=' + encodeURIComponent('/pages/cart/cart'),
      });
      return;
    }
    this.refreshCart();
  },

  refreshCart: function () {
    this.setData({ isLoading: true, isError: false });
    cartStore
      .refresh()
      .then((cart) => this.renderCart(cart))
      .catch((err) => {
        // Fall back to local cache so the UI doesn't go blank when
        // the user is offline.
        const local = cartUtil.getCart();
        this.setData({
          cartItems: local,
          isError: true,
          errorMessage: err && err.message ? err.message : '加载购物车失败',
        });
        this.computeTotals(local);
        this.setData({ isLoading: false });
      });
  },

  renderCart: function (cart) {
    // Translate server-side cart items into the shape the WXML
    // expects: {id, name, price, imageUrl, quantity, selected}.
    const items = (cart.items || []).map((it) => ({
      id: it.productId,
      productId: it.productId,
      name: it.productName || it.name || it.productId,
      price: it.unitPrice || it.price || 0,
      imageUrl: it.imageUrl || '',
      quantity: it.quantity,
      selected: !!it.selected,
    }));
    this.computeTotals(items);
    this.setData({
      cartItems: items,
      isLoading: false,
      isError: false,
      errorMessage: '',
    });
  },

  computeTotals: function (items) {
    let total = 0;
    let selected = 0;
    const selectedIds = this.data.selectedItems || [];
    items.forEach((item) => {
      total += item.price * item.quantity;
      if (selectedIds.includes(item.id)) {
        selected += item.price * item.quantity;
      }
    });
    const shippingFee = selected >= 99 ? 0 : 10;
    this.setData({
      totalPrice: (selected + shippingFee).toFixed(2),
      selectedPrice: selected.toFixed(2),
      shippingFee: shippingFee,
    });
  },

  onCheckboxChange: function (e) {
    const selectedItems = e.detail.value;
    this.setData({ selectedItems });
    this.computeTotals(this.data.cartItems);
  },

  onSelectAll: function (e) {
    const ids = e.detail.checked ? this.data.cartItems.map((i) => i.id) : [];
    this.setData({ selectedItems: ids });
    this.computeTotals(this.data.cartItems);
  },

  onQuantityChange: function (e) {
    const id = e.currentTarget.dataset.id;
    const quantity = Math.max(1, parseInt(e.detail.value) || 1);
    this.callStore('updateItem', id, quantity);
  },

  onMinus: function (e) {
    const id = e.currentTarget.dataset.id;
    const item = this.data.cartItems.find((i) => i.id === id);
    if (item && item.quantity > 1) this.callStore('updateItem', id, item.quantity - 1);
  },

  onPlus: function (e) {
    const id = e.currentTarget.dataset.id;
    const item = this.data.cartItems.find((i) => i.id === id);
    if (item) this.callStore('updateItem', id, item.quantity + 1);
  },

  onRemove: function (e) {
    const id = e.currentTarget.dataset.id;
    this.callStore('removeItem', id);
  },

  onToggleSelected: function (e) {
    const id = e.currentTarget.dataset.id;
    this.callStore('toggleItem', id);
  },

  callStore: function (action, productId, quantity) {
    const fn = cartStore[action];
    if (typeof fn !== 'function') return;
    const promise = quantity === undefined ? fn.call(cartStore, productId) : fn.call(cartStore, productId, quantity);
    promise
      .then((cart) => this.renderCart(cart))
      .catch((err) => {
        wx.showToast({ title: err && err.message ? err.message : '操作失败', icon: 'none' });
      });
  },

  onCheckout: function () {
    if (this.data.cartItems.length === 0) return;
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.navigateTo({
        url: '/pages-sub/user/login/login?redirect=' + encodeURIComponent('/pages/cart/cart'),
      });
      return;
    }
    wx.navigateTo({ url: '/pages-sub/order/order-confirm/order-confirm' });
  },

  selectAddress: function () {
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    const selectedAddress = this.data.selectedAddress || null;
    wx.navigateTo({
      url:
        '/pages-sub/user/address/address-list?selectMode=true&selectedAddress=' +
        encodeURIComponent(selectedAddress ? JSON.stringify(selectedAddress) : ''),
    });
  },
});
