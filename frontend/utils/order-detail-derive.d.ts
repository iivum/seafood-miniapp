// utils/order-detail-derive.d.ts
export interface OrderItem {
  productId: string;
  productName: string;
  unitPrice: number | string;
  quantity: number;
}

export interface OrderTracking {
  carrier?: string;
  trackingNumber?: string;
  deliveredAt?: string;
}

export interface OrderResponse {
  id: string;
  userId: string;
  items: OrderItem[];
  totalAmount: number | string;
  status: string;
  cancelReason: string | null;
  tracking: OrderTracking | null;
  refundId: string | null;
  estimatedDelivery: string | null;
  createdAt: string;
  updatedAt: string;
}

export type StatusColor = 'warning' | 'info' | 'success' | 'neutral' | 'error';

export interface StatusBanner {
  statusText: string;
  statusColor: StatusColor;
  estimatedText: string | null;
  trackingText: string | null;
}

export type TimelineState = 'done' | 'current' | 'future';

export interface TimelineNode {
  label: string;
  time: string;
  desc: string;
  state: TimelineState;
}

export function deriveBanner(order: OrderResponse | null | undefined): StatusBanner;
export function deriveTimeline(order: OrderResponse | null | undefined): TimelineNode[];
export function fmtDate(iso: string | null | undefined): string;
export function fmtTime(iso: string | null | undefined): string | null;
