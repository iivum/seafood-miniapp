/**
 * 路线图 2.8 — 订单状态 → 操作按钮映射(7 状态 × 多按钮)。
 *
 * 设计要点:
 *   - 纯映射组件,无副作用;父(order-detail / order-list 卡片)监听 `action` 事件
 *   - 按钮顺序按"主操作在前,次操作在后"排
 *   - 主操作 accent 强调;次操作 outline;危险(error)操作放最右
 *   - REFUNDABLE_STATUSES(Paid/Shipped/Completed)显示「申请退款」入口
 *   - 单测在 OrderActionRow.test.tsx 覆盖 7 状态全分支
 */
import { OrderStatus } from '../../types';

export type OrderActionId =
  | 'pay'
  | 'cancelOrder'
  | 'viewTracking'
  | 'confirmReceipt'
  | 'requestRefund'
  | 'withdrawRefund'
  | 'deleteOrder'
  | 'reorder';

export interface OrderAction {
  id: OrderActionId;
  label: string;
  variant: 'primary' | 'secondary' | 'danger';
}

const MAP: Record<OrderStatus, OrderAction[]> = {
  PENDING: [
    { id: 'pay', label: '支付', variant: 'primary' },
    { id: 'cancelOrder', label: '取消订单', variant: 'secondary' },
  ],
  PAID: [
    { id: 'viewTracking', label: '查看物流', variant: 'secondary' },
    { id: 'requestRefund', label: '申请退款', variant: 'secondary' },
  ],
  SHIPPED: [
    { id: 'viewTracking', label: '查看物流', variant: 'secondary' },
    { id: 'confirmReceipt', label: '确认收货', variant: 'primary' },
    { id: 'requestRefund', label: '申请退款', variant: 'secondary' },
  ],
  COMPLETED: [
    { id: 'requestRefund', label: '申请退款', variant: 'secondary' },
    { id: 'reorder', label: '再次购买', variant: 'primary' },
    { id: 'deleteOrder', label: '删除订单', variant: 'danger' },
  ],
  CANCELLED: [
    { id: 'reorder', label: '再次购买', variant: 'primary' },
    { id: 'deleteOrder', label: '删除订单', variant: 'danger' },
  ],
  REFUNDING: [
    { id: 'viewTracking', label: '查看退款进度', variant: 'secondary' },
  ],
  REFUNDED: [
    { id: 'deleteOrder', label: '删除订单', variant: 'danger' },
  ],
};

/**
 * 纯逻辑:给定订单状态,返回该状态下的可见操作按钮列表。
 * 提取为纯函数便于单测 + wxml/wxs 复用。
 */
export function getActionsFor(status: OrderStatus): OrderAction[] {
  return MAP[status] ?? [];
}

Component({
  options: {
    addGlobalClass: true,
  },
  properties: {
    status: {
      type: String,
      value: 'PENDING',
    },
  },
  data: {
    actions: MAP.PENDING,
  },
  observers: {
    status(status: OrderStatus) {
      this.setData({ actions: getActionsFor(status) });
    },
  },
  methods: {
    onTap(e: WechatMiniprogram.TouchEvent) {
      const id = e.currentTarget.dataset.id as OrderActionId;
      this.triggerEvent('action', { id });
    },
  },
});
