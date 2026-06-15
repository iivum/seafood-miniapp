/**
 * 路线图 4.3 — OrderTrackingTimeline 组件类型与计算 helper。
 *
 * <p>4 个时间节点:已下单 / 已发货 / 运输中 / 已签收。
 * <ul>
 *   <li>已下单 — 永远 done(Order.createdAt)</li>
 *   <li>已发货 — tracking.events[0].at(SHIPPED 起始)</li>
 *   <li>运输中 — tracking.events 中间节点 at(若有)</li>
 *   <li>已签收 — Order.status === COMPLETED 且 tracking.events 末节点 at</li>
 * </ul>
 * 计算函数 {@link computeStages} 是纯函数,父组件用 observer/watch 触发,
 * 不直接读 mp 运行时 state(便于单测)。
 */
import type { Order, OrderTracking, TrackingEvent } from '../../types';

export interface TimelineStages {
  shippedAt: string | null;
  shippedClass: 'tracking-timeline__node' | 'tracking-timeline__node tracking-timeline__node--done';
  inTransitAt: string | null;
  inTransitClass: 'tracking-timeline__node' | 'tracking-timeline__node tracking-timeline__node--done';
  deliveredAt: string | null;
  deliveredClass: 'tracking-timeline__node' | 'tracking-timeline__node tracking-timeline__node--done';
}

function fmtTime(iso: string | null | undefined): string | null {
  if (!iso) return null;
  // mp 端时间格式:2026-06-13 10:30
  // 显式锁定 Asia/Shanghai(单仓在 UTC CI 跑也一致,用户面向也始终是北京时间)
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(d);
  const v = (t: string) => parts.find((p) => p.type === t)?.value ?? '';
  return `${v('year')}-${v('month')}-${v('day')} ${v('hour')}:${v('minute')}`;
}

/**
 * 从 tracking.events + Order.status 推算 4 节点。
 * 无 tracking → 全部未 done(只 "已下单" done);
 * 有 tracking 但未 COMPLETED → "已发货" + "运输中" done(取最后 1 个非 SHIPPED 事件);
 * COMPLETED → 全 4 节点 done(取最后事件为"已签收")。
 */
export function computeStages(order: Order): TimelineStages {
  const tracking: OrderTracking | null = order.tracking ?? null;
  const events: TrackingEvent[] = tracking?.events ?? [];
  const hasTracking = events.length > 0;
  const isDelivered = order.status === 'COMPLETED';

  let shippedAt: string | null = null;
  let inTransitAt: string | null = null;
  let deliveredAt: string | null = null;

  if (hasTracking) {
    // 第一个 event 是 SHIPPED 起始(参见 OrderService.batchShip / 4.1 OrderTracking 构造约定)
    const first = events[0];
    shippedAt = fmtTime(first.at);
    if (events.length >= 2) {
      // 第二个 event 起算"运输中"
      inTransitAt = fmtTime(events[1].at);
    }
    if (isDelivered) {
      deliveredAt = fmtTime(events[events.length - 1].at);
    }
  }

  const done = 'tracking-timeline__node tracking-timeline__node--done' as const;
  const pending = 'tracking-timeline__node' as const;
  return {
    shippedAt,
    shippedClass: hasTracking ? done : pending,
    inTransitAt,
    inTransitClass: inTransitAt ? done : pending,
    deliveredAt,
    deliveredClass: deliveredAt ? done : pending,
  };
}

export function shouldShow(order: Order): boolean {
  // PENDING / PAID / CANCELLED 不显示时间线(没意义)
  return ['SHIPPED', 'COMPLETED', 'REFUNDING', 'REFUNDED'].includes(order.status);
}
