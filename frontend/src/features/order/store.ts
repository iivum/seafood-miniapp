/**
 * Order feature: store.
 *
 * Caches the user's order list and exposes imperative actions
 * (refresh, create, cancel). The `placeOrder` action is a thin
 * convenience wrapper that also clears the cart on success.
 */
import { cartStore } from '../cart/store';
import { OrderAPI } from './api';
import type { CreateOrderRequest, Order, OrderStatus } from './types';

type Listener = (state: OrderState) => void;

export interface OrderState {
  orders: Order[];
  current: Order | null;
  isLoading: boolean;
  isError: boolean;
  errorMessage: string | null;
}

class OrderStore {
  private state: OrderState = {
    orders: [],
    current: null,
    isLoading: false,
    isError: false,
    errorMessage: null,
  };
  private listeners = new Set<Listener>();

  getState(): OrderState {
    return this.state;
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private setState(patch: Partial<OrderState>): void {
    this.state = { ...this.state, ...patch };
    this.listeners.forEach((l) => l(this.state));
  }

  async refresh(): Promise<Order[]> {
    this.setState({ isLoading: true, isError: false, errorMessage: null });
    try {
      const orders = await OrderAPI.list();
      this.setState({ orders, isLoading: false });
      return orders;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load orders';
      this.setState({ isLoading: false, isError: true, errorMessage: message });
      throw err;
    }
  }

  async loadById(id: string): Promise<Order> {
    const order = await OrderAPI.getById(id);
    this.setState({ current: order });
    return order;
  }

  /**
   * Place a new order from the current cart. On success, also clears
   * the cart and refreshes it from the server so the badge updates.
   */
  async placeOrder(body: CreateOrderRequest): Promise<Order> {
    const order = await OrderAPI.create(body);
    this.setState({
      orders: [order, ...this.state.orders],
      current: order,
    });
    // best-effort cart clear so the badge resets
    try {
      await cartStore.clear();
    } catch {
      /* ignore — order succeeded, cart refresh can happen later */
    }
    return order;
  }

  async cancel(id: string, reason: string): Promise<Order> {
    const order = await OrderAPI.cancel(id, reason);
    this.setState({
      orders: this.state.orders.map((o) => (o.id === id ? order : o)),
      current: this.state.current?.id === id ? order : this.state.current,
    });
    return order;
  }

  /** Filter helper for the order list page. */
  filter(status?: OrderStatus): Order[] {
    return status ? this.state.orders.filter((o) => o.status === status) : this.state.orders;
  }
}

export const orderStore = new OrderStore();
export { OrderStore };
