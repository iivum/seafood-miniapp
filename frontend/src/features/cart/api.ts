/**
 * Cart feature: API client.
 *
 * Authenticated (CUSTOMER) endpoints per backend contract:
 *   GET    /api/cart                   — current cart
 *   POST   /api/cart/items             — add {productId, quantity}
 *   PUT    /api/cart/items/{productId} — update {quantity}
 *   DELETE /api/cart/items/{productId} — remove
 *   PATCH  /api/cart/items/{productId} — toggle selected
 *   DELETE /api/cart                   — clear
 */
import { del, get, patch, post, put } from '../../shared/api/request';
import type {
  AddToCartRequest,
  Cart,
  UpdateCartItemRequest,
} from './types';

export const CartAPI = {
  get(): Promise<Cart> {
    return get<Cart>('/cart', { needAuth: true });
  },

  addItem(body: AddToCartRequest): Promise<Cart> {
    return post<Cart>('/cart/items', body, { needAuth: true });
  },

  updateItem(productId: string, body: UpdateCartItemRequest): Promise<Cart> {
    return put<Cart>(`/cart/items/${encodeURIComponent(productId)}`, body, { needAuth: true });
  },

  removeItem(productId: string): Promise<Cart> {
    return del<Cart>(`/cart/items/${encodeURIComponent(productId)}`, { needAuth: true });
  },

  toggleItem(productId: string): Promise<Cart> {
    return patch<Cart>(`/cart/items/${encodeURIComponent(productId)}`, undefined, { needAuth: true });
  },

  clear(): Promise<Cart> {
    return del<Cart>('/cart', { needAuth: true });
  },
};
