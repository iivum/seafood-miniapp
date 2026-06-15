import { Check, Circle, Truck } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { formatDateTime } from '@/lib/utils';
import type { OrderResponse } from '@/types/api';
import { computeTimelineStages, shouldShowTimeline } from './timeline';

const NODE_LABEL: Record<'shipped' | 'inTransit' | 'delivered', string> = {
  shipped: '已发货',
  inTransit: '运输中',
  delivered: '已签收',
};

/**
 * 路线图 4.4 — ad-06 订单详情时间线(admin 端)。
 *
 * <p>算法复用 mp 端 4.3 的 {@link computeTimelineStages} / {@link shouldShowTimeline}
 * (本文件目录 ./timeline.tsx)— 单仓单 seller 阶段不要维护两份算法。
 *
 * <p>相对 mp 端的增强:
 * <ul>
 *   <li>展示完整 events 列表(含 location / description)而非只 4 节点;</li>
 *   <li>admin 视角:每节点旁显示事件描述(配送员 / 仓库 / 备注);</li>
 *   <li>支持「录入物流」占位按钮(本期不接 — 4.18 详情页布局基础先打,后续 4.13/4.20
 *       录物流单号走 AdminOrderController 端点)</li>
 * </ul>
 */
export function OrderTrackingTimeline({ order }: { order: OrderResponse }) {
  if (!shouldShowTimeline(order)) return null;

  const stages = computeTimelineStages(order);
  const events = order.tracking?.events ?? [];

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="flex items-center gap-2">
            <Truck className="h-4 w-4" />
            物流时间线
          </CardTitle>
          {order.tracking ? (
            <span className="text-sm text-muted">
              {order.tracking.carrier} · 单号 {order.tracking.trackingNumber}
            </span>
          ) : null}
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        <ol className="space-y-3">
          {(['shipped', 'inTransit', 'delivered'] as const).map((k) => {
            const stage = stages[k];
            return (
              <li key={k} className="flex items-start gap-3">
                {stage.done ? (
                  <Check className="mt-0.5 h-4 w-4 shrink-0 text-success" />
                ) : (
                  <Circle className="mt-0.5 h-4 w-4 shrink-0 text-muted" />
                )}
                <div className="flex-1">
                  <div className="text-sm font-medium">
                    {NODE_LABEL[k]}
                    {stage.at ? (
                      <span className="ml-2 text-xs text-muted">
                        {formatDateTime(stage.at)}
                      </span>
                    ) : null}
                  </div>
                </div>
              </li>
            );
          })}
        </ol>

        {events.length > 0 ? (
          <details className="rounded-md border border-border bg-soft px-3 py-2 text-sm">
            <summary className="cursor-pointer text-muted">
              完整轨迹({events.length} 条)
            </summary>
            <ul className="mt-2 space-y-1 text-xs">
              {events.map((e, i) => (
                <li key={i} className="flex gap-2">
                  <span className="text-muted">{formatDateTime(e.at)}</span>
                  <span className="font-mono">{e.location}</span>
                  <span>{e.description}</span>
                </li>
              ))}
            </ul>
          </details>
        ) : null}
      </CardContent>
    </Card>
  );
}
