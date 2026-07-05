/**
 * Runtime shim for features/order/store.ts.
 */
const { OrderAPI } = require('./api');
const { cartStore } = require('../cart/store');

class OrderStore {
  constructor() {
    this.state = {
      orders: [],
      current: null,
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
      const orders = await OrderAPI.list();
      this._setState({ orders, isLoading: false });
      return orders;
    } catch (err) {
      const message = err && err.message ? err.message : 'Failed to load orders';
      this._setState({ isLoading: false, isError: true, errorMessage: message });
      throw err;
    }
  }

  async loadById(id) {
    const order = await OrderAPI.getById(id);
    this._setState({ current: order });
    return order;
  }

  async placeOrder(body) {
    const order = await OrderAPI.create(body);
    this._setState({
      orders: [order, ...this.state.orders],
      current: order,
    });
    try {
      await cartStore.clear();
    } catch {
      /* best-effort */
    }
    return order;
  }

  // D3b(mp-backend-contract-gaps Gap 2):mp-03 立即购买直接建单,绕开购物车。
  // 与 placeOrder 唯一区别是不调用 cartStore.clear() —— 这条路径从未写
  // 购物车,没有东西需要清。
  async placeDirectBuyOrder(body) {
    const order = await OrderAPI.create(body);
    this._setState({
      orders: [order, ...this.state.orders],
      current: order,
    });
    return order;
  }

  async cancel(id, reason) {
    const order = await OrderAPI.cancel(id, reason);
    this._setState({
      orders: this.state.orders.map((o) => (o.id === id ? order : o)),
      current: this.state.current && this.state.current.id === id ? order : this.state.current,
    });
    return order;
  }

  filter(status) {
    return status ? this.state.orders.filter((o) => o.status === status) : this.state.orders;
  }
}

const orderStore = new OrderStore();
module.exports = { orderStore, OrderStore };
