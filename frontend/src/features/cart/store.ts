/**
 * Cart feature: store.
 *
 * Lightweight in-memory cart state that:
 *   - Caches the most recent server response
 *   - Subscribes to network refreshes
 *   - Exposes imperative actions (add/remove/update/toggle/refresh/clear)
 *   - Falls back to local storage for offline add-to-cart
 *
 * The pages bind to `cartStore.getState()` in their `onShow` and call
 * `cartStore.refresh()` to pull the latest server-side cart. The
 * `addItem` action optimistically updates the badge via the listener.
 */
import { CartAPI } from './api';
import type { Cart, CartItem } from './types';

type Listener = (state: CartState) => void;

export interface CartState {
  cart: Cart;
  isLoading: boolean;
  isError: boolean;
  errorMessage: string | null;
}

const EMPTY_CART: Cart = {
  id: '',
  userId: '',
  items: [],
  totalQuantity: 0,
  totalSelectedQuantity: 0,
  selectedAmount: 0,
  updatedAt: '',
};

class CartStore {
  private state: CartState = {
    cart: EMPTY_CART,
    isLoading: false,
    isError: false,
    errorMessage: null,
  };
  private listeners = new Set<Listener>();

  getState(): CartState {
    return this.state;
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private setState(patch: Partial<CartState>): void {
    this.state = { ...this.state, ...patch };
    this.listeners.forEach((l) => l(this.state));
  }

  /** Pull the latest cart from the server. */
  async refresh(): Promise<Cart> {
    this.setState({ isLoading: true, isError: false, errorMessage: null });
    try {
      const cart = await CartAPI.get();
      this.setState({ cart, isLoading: false });
      return cart;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load cart';
      this.setState({ isLoading: false, isError: true, errorMessage: message });
      throw err;
    }
  }

  async addItem(productId: string, quantity: number): Promise<Cart> {
    const cart = await CartAPI.addItem({ productId, quantity });
    this.setState({ cart });
    return cart;
  }

  async updateItem(productId: string, quantity: number): Promise<Cart> {
    const cart = await CartAPI.updateItem(productId, { productId, quantity });
    this.setState({ cart });
    return cart;
  }

  async removeItem(productId: string): Promise<Cart> {
    const cart = await CartAPI.removeItem(productId);
    this.setState({ cart });
    return cart;
  }

  async toggleItem(productId: string): Promise<Cart> {
    const cart = await CartAPI.toggleItem(productId);
    this.setState({ cart });
    return cart;
  }

  async clear(): Promise<Cart> {
    const cart = await CartAPI.clear();
    this.setState({ cart });
    return cart;
  }

  /** Optimistic badge helper for "X items in cart" UI elements. */
  getItemCount(): number {
    return this.state.cart.totalQuantity;
  }

  /**
   * Local-only add (for the index page that pre-fills before login).
   * Persists into `wx.setStorageSync('localCart', items)` so the cart
   * page can render a meaningful placeholder when offline / pre-login.
   */
  addLocal(item: CartItem): void {
    const key = 'localCart';
    let list: CartItem[] = [];
    if (typeof wx !== 'undefined' && wx.getStorageSync) {
      const v = wx.getStorageSync(key);
      if (Array.isArray(v)) list = v as CartItem[];
    }
    const existing = list.find((i) => i.productId === item.productId);
    if (existing) {
      existing.quantity += item.quantity;
    } else {
      list.push(item);
    }
    if (typeof wx !== 'undefined' && wx.setStorageSync) {
      wx.setStorageSync(key, list);
    }
  }
}

export const cartStore = new CartStore();
export { CartStore };
