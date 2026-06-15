/**
 * Order feature: types.
 *
 * Order aggregate root mirroring the backend `Order.java` model.
 *
 * 状态机升级历史:
 *   - Sprint 2 路线图 2.6:5 状态(PENDING / PAID / SHIPPED / COMPLETED / CANCELLED)
 *   - Sprint 3 路线图 4.1:tracking 字段(物流,SHIPPED 之后挂值)
 *   - Sprint 3 路线图 4.7:加 REFUNDING(mp 申请退款) + REFUNDED(admin 同意退款)
 */
export type OrderStatus =
  | 'PENDING'
  | 'PAID'
  | 'SHIPPED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'REFUNDING'
  | 'REFUNDED';

/** 订单允许用户申请退款的状态(后端 4.7 requestRefund 同款白名单)。 */
export const REFUNDABLE_STATUSES: ReadonlySet<OrderStatus> = new Set([
  'PAID',
  'SHIPPED',
  'COMPLETED',
]);

export interface OrderItem {
  productId: string;
  productName: string;
  unitPrice: number;
  quantity: number;
  imageUrl?: string;
}

export interface TrackingEvent {
  at: string;
  status: string;
  location: string;
  description: string;
}

export interface OrderTracking {
  carrier: string;
  trackingNumber: string;
  events: TrackingEvent[];
}

export interface Order {
  id: string;
  userId: string;
  items: OrderItem[];
  totalAmount: number;
  status: OrderStatus;
  cancelReason?: string;
  /** 物流信息(Sprint 3 4.1 后端字段;PENDING / PAID / CANCELLED 一定为 undefined)。 */
  tracking?: OrderTracking;
  createdAt: string;
  updatedAt: string;
}

export interface CreateOrderRequest {
  addressId: string;
  remark?: string;
}

/**
 * 4.7:退款申请请求体(对应后端 RefundRequest)。
 * 字段约束与后端 @DecimalMin / @Size 注解一致;前端 zod schema 二次校验(防输入绕过)。
 */
export interface RefundRequest {
  amount: number;
  reason: string;
}

/** 4.7:退款单响应(对应后端 RefundResponse)。 */
export interface RefundResponse {
  id: string;
  orderId: string;
  userId: string;
  amount: number;
  reason: string;
  status: 'REQUESTED' | 'APPROVED' | 'REJECTED';
  createdAt: string;
  updatedAt: string;
}
