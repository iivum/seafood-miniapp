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

  /**
   * 4.10:申请退款。乐观更新本地 Order.status = REFUNDING(后端同步会改,
   * 失败时回滚),再 await 后端响应后用真实状态覆盖。后端响应只返 Refund 单,
   * Order 状态需重新拉一次;这里用响应时间戳 + 一个合成 status 更新本地。
   *
   * mp-cross-screen-cleanup D7 诊断发现:store.ts 早就实现且测试过这个方法
   * (store.test.ts),但这份 mp 运行时真正加载的 .js shim 此前一直没同步补上——
   * 同 order/api.js 顶部注释记录的 "mp-08 状态机 5 操作端点漏同步" 是同一类
   * "ts 有、js shim 没有" 的 drift,这里补上,否则 order-actions.js 调用
   * orderStore.requestRefund 在真实 mp 运行时会直接 TypeError。
   */
  async requestRefund(id, amount, reason) {
    const prevOrders = this.state.orders;
    const prevCurrent = this.state.current;
    this._setState({
      orders: this.state.orders.map((o) => (o.id === id ? { ...o, status: 'REFUNDING' } : o)),
      current:
        this.state.current && this.state.current.id === id
          ? { ...this.state.current, status: 'REFUNDING' }
          : this.state.current,
    });
    try {
      const refund = await OrderAPI.requestRefund(id, { amount, reason });
      return { orderStatus: 'REFUNDING', updatedAt: refund.updatedAt };
    } catch (err) {
      this._setState({ orders: prevOrders, current: prevCurrent });
      throw err;
    }
  }

  filter(status) {
    return status ? this.state.orders.filter((o) => o.status === status) : this.state.orders;
  }
}

const orderStore = new OrderStore();
module.exports = { orderStore, OrderStore };
