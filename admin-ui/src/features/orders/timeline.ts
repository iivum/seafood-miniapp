/**
 * 路线图 4.4 — admin-ui / mp 共享 timeline 算法。
 *
 * <p>与 mp 端 `frontend/src/features/order/components/OrderTrackingTimeline/index.ts`
 * 的 {@link computeStages} / {@link shouldShow} 行为一致;两边都依赖
 * `Order.tracking` 字段(4.1 后端追踪),admin/mp 各自用自己 UI 框架渲染。
 *
 * <p>为什么不抽到 shared 包?
 * <ul>
 *   <li>mp 端是 WXML + .ts,admin-ui 是 TSX,渲染部分无法共享</li>
 *   <li>算法本身只有 ~30 行,两份维护成本低于一个 shared 包 setup 成本</li>
 *   <li>如果未来订单状态机变更,Sprint 内同步两个文件是合理的</li>
 * </ul>
 *
 * <p>注:mp 端 helper 引用字符串 `tracking-timeline__node` 类名(给 WXML 渲染);
 * 这里返回纯数据 stages,UI 层用 Tailwind 状态 class 表达 done / pending。
 */
import type { OrderResponse } from '@/types/api';

export interface TimelineStage {
  at: string | null;
  done: boolean;
}

export interface TimelineStages {
  shipped: TimelineStage;
  inTransit: TimelineStage;
  delivered: TimelineStage;
}

/**
 * 4 节点:已下单(永远 done;Order.createdAt)/ 已发货(events[0].at)/
 * 运输中(events[1].at)/ 已签收(COMPLETED + 最后事件 at)。
 * 完整规则与 mp 端 4.3 同。
 */
export function computeTimelineStages(order: OrderResponse): TimelineStages {
  const events = order.tracking?.events ?? [];
  const isDelivered = order.status === 'COMPLETED';

  const shipped: TimelineStage = {
    at: events[0]?.at ?? null,
    done: events.length > 0,
  };
  const inTransit: TimelineStage = {
    at: events[1]?.at ?? null,
    done: events.length >= 2,
  };
  const delivered: TimelineStage = {
    at: isDelivered && events.length > 0 ? (events[events.length - 1]?.at ?? null) : null,
    done: isDelivered,
  };

  return { shipped, inTransit, delivered };
}

/** 可见性:仅 SHIPPED / COMPLETED / REFUNDING / REFUNDED 展示。 */
export function shouldShowTimeline(order: OrderResponse): boolean {
  return ['SHIPPED', 'COMPLETED', 'REFUNDING', 'REFUNDED'].includes(order.status);
}
