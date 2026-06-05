/**
 * Order feature: API client.
 *
 * Authenticated (CUSTOMER for own, ADMIN for all) endpoints per backend contract:
 *   GET  /api/orders            — list own orders
 *   GET  /api/orders/{id}       — single order detail
 *   POST /api/orders            — create from current cart
 *   POST /api/orders/{id}/cancel — cancel a pending order
 *   POST /api/orders/{id}/ship  — (admin) mark as shipped
 */
import { del, get, post } from '../../shared/api/request';
import type { CreateOrderRequest, Order } from './types';

export const OrderAPI = {
  list(): Promise<Order[]> {
    return get<Order[]>('/orders', { needAuth: true });
  },

  getById(id: string): Promise<Order> {
    return get<Order>(`/orders/${encodeURIComponent(id)}`, { needAuth: true });
  },

  create(body: CreateOrderRequest): Promise<Order> {
    return post<Order>('/orders', body, { needAuth: true });
  },

  cancel(id: string, reason: string): Promise<Order> {
    return post<Order>(`/orders/${encodeURIComponent(id)}/cancel`, { reason }, { needAuth: true });
  },

  ship(id: string): Promise<Order> {
    return post<Order>(`/orders/${encodeURIComponent(id)}/ship`, undefined, { needAuth: true });
  },
};

// Re-export to avoid unused-import lints if a feature tree-shakes del
void del;
