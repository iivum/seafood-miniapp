/**
 * Runtime shim for features/cart/store.ts.
 */
const { CartAPI } = require('./api');

const EMPTY_CART = {
  id: '',
  userId: '',
  items: [],
  totalQuantity: 0,
  totalSelectedQuantity: 0,
  selectedAmount: 0,
  updatedAt: '',
};

class CartStore {
  constructor() {
    this.state = {
      cart: EMPTY_CART,
      isLoading: false,
      isError: false,
      errorMessage: null,
    };
    this.listeners = new Set();
  }

  getState() {
    return this.state;
  }

  subscribe(listener) {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  _setState(patch) {
    this.state = { ...this.state, ...patch };
    this.listeners.forEach((l) => l(this.state));
  }

  async refresh() {
    this._setState({ isLoading: true, isError: false, errorMessage: null });
    try {
      const cart = await CartAPI.get();
      this._setState({ cart, isLoading: false });
      return cart;
    } catch (err) {
      const message = err && err.message ? err.message : 'Failed to load cart';
      this._setState({ isLoading: false, isError: true, errorMessage: message });
      throw err;
    }
  }

  async addItem(productId, quantity) {
    const cart = await CartAPI.addItem({ productId, quantity });
    this._setState({ cart });
    return cart;
  }

  async updateItem(productId, quantity) {
    const cart = await CartAPI.updateItem(productId, { productId, quantity });
    this._setState({ cart });
    return cart;
  }

  async removeItem(productId) {
    const cart = await CartAPI.removeItem(productId);
    this._setState({ cart });
    return cart;
  }

  async toggleItem(productId) {
    const cart = await CartAPI.toggleItem(productId);
    this._setState({ cart });
    return cart;
  }

  async clear() {
    const cart = await CartAPI.clear();
    this._setState({ cart });
    return cart;
  }

  getItemCount() {
    return this.state.cart.totalQuantity;
  }

  addLocal(item) {
    const key = 'localCart';
    let list = [];
    if (typeof wx !== 'undefined' && wx.getStorageSync) {
      const v = wx.getStorageSync(key);
      if (Array.isArray(v)) list = v;
    }
    const existing = list.find((i) => i.productId === item.productId);
    if (existing) existing.quantity += item.quantity;
    else list.push(item);
    if (typeof wx !== 'undefined' && wx.setStorageSync) {
      wx.setStorageSync(key, list);
    }
  }
}

const cartStore = new CartStore();
module.exports = { cartStore, CartStore };
