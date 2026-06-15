/**
 * Order feature: API client.
 *
 * Authenticated (CUSTOMER for own, ADMIN for all) endpoints per backend contract:
 *   GET  /api/orders                       — list own orders
 *   GET  /api/orders/{id}                  — single order detail
 *   POST /api/orders                       — create from current cart
 *   POST /api/orders/{id}/cancel           — cancel a pending order
 *   POST /api/orders/{id}/ship             — (admin) mark as shipped
 *
 * mp-08 状态机 5 操作端点封装(路线图 2.9):
 *   POST /api/orders/{id}/cancel            — PENDING → CANCELLED(已存在,沿用)
 *   POST /api/orders/{id}/pay               — PENDING → PAID(mock,等 Sprint 3 接微信支付)
 *   POST /api/orders/{id}/remind-ship       — PAID → 仅发提醒通知,不动状态
 *   POST /api/orders/{id}/confirm-receive   — SHIPPED → COMPLETED(用户确认收货)
 *   POST /api/orders/{id}/rebuy             — COMPLETED → 返回可加入购物车的 items
 *
 * 注:后端 C-2 REFUNDING 流程(POST /refund / refund/approve / refund/reject)
 * 留到 Sprint 3 task 4.7-4.11;本迭代不引入。
 */
import { del, get, post } from '../../shared/api/request';
import type { CartItem } from '../cart/types';
import type { CreateOrderRequest, Order, RefundRequest, RefundResponse } from './types';

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

  /**
   * mp-08 付款(mock):PENDING → PAID。Sprint 3 接入微信支付前始终是 mock(后端 markPaid 直拨)。
   * 返回更新后的 Order;若后端未实现,Sprint 1 末此调用会 404,UI 需 fallback toast「开发中」。
   */
  pay(id: string, paymentMethod: 'wechat' = 'wechat'): Promise<Order> {
    return post<Order>(`/orders/${encodeURIComponent(id)}/pay`, { paymentMethod }, { needAuth: true });
  },

  /**
   * mp-08 提醒发货(PAID):不动状态,后端发模板消息/通知给商家。
   * 204 No Content;UI 期待 200/204 路径都视为成功。
   */
  remindShip(id: string): Promise<void> {
    return post<void>(`/orders/${encodeURIComponent(id)}/remind-ship`, undefined, { needAuth: true });
  },

  /**
   * mp-08 确认收货(SHIPPED → COMPLETED):用户主动点「确认收货」。
   * 流程上需要二次确认 sheet(防误触),UI 端点参见 OrderActionRow(task 2.8)。
   */
  confirmReceive(id: string): Promise<Order> {
    return post<Order>(`/orders/${encodeURIComponent(id)}/confirm-receive`, undefined, { needAuth: true });
  },

  /**
   * mp-08 再次购买(COMPLETED / CANCELLED):把订单里所有 line items
   * 装成 cart item 列表返回,UI 弹「已加入购物车 X 件」toast 后跳购物车。
   * 不直接落 cart,让用户有最后一次改动机会。
   */
  rebuy(id: string): Promise<CartItem[]> {
    return post<CartItem[]>(`/orders/${encodeURIComponent(id)}/rebuy`, undefined, { needAuth: true });
  },

  /**
   * 4.10 mp-08 申请退款:POST /api/orders/{id}/refund。
   * 鉴权由后端校验(订单主或 ADMIN),UI 端不需要预校验 owner。
   * 成功后 Order.status 会同步改为 REFUNDING,后端在响应中返回 Refund 单,
   * UI 用此响应乐观更新本地 Order。
   */
  requestRefund(id: string, body: RefundRequest): Promise<RefundResponse> {
    return post<RefundResponse>(`/orders/${encodeURIComponent(id)}/refund`, body, { needAuth: true });
  },
};

// Re-export to avoid unused-import lints if a feature tree-shakes del
void del;
