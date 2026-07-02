/**
 * 路线图 4.3 — OrderTrackingTimeline(Wechat Mini Program Component)。
 *
 * <p>行为:接受 {@code order} 属性,内部 observers 触发 {@link computeStages}
 * 重算 stages(已下单 / 已发货 / 运输中 / 已签收)。无 tracking 的早期订单
 * (PENDING / PAID)通过 {@link shouldShow} 不渲染。
 *
 * mp-od-prototype-alignment mp-09(brief `.superpowers/sdd/mp-od-8-order-detail-brief.md`)
 * 接线时发现:此前这里 `require('./index.ts-helpers')` 指向一个从未存在过的文件——
 * mp 运行时无 TS 编译,`computeStages`/`shouldShow` 的实现只在 `index.ts` 里(`index.test.ts`
 * 已 100% 覆盖测试),从未被真正抽出成独立 `.js` 文件。这个组件此前没有任何页面在
 * usingComponents 里接线,这条 require 从未被真实执行到;接进 order-detail.wxml 后
 * mp 运行时才会真正加载这个 `.js`,require 一个不存在的模块会直接崩掉整个组件
 * (同 OrderActionRow"渲染层修好后才暴露"的休眠 bug 模式,第 3 次出现)。
 * 修复:JS 版直接内联 computeStages/shouldShow(逻辑同 index.ts,仅去掉类型标注),
 * 同 OrderActionRow/index.js 用 JS 复刻 .ts MAP 的既有惯例——mp 运行时无 TS 编译,
 * 这里必须手写 JS 副本;.ts 端逻辑由 index.test.ts 覆盖,.js 副本由 mp 实机验证 +
 * 本组件目录下的 runtime 回归测试覆盖。
 */
function fmtTime(iso) {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  // mp 端时间格式:2026-06-13 10:30,显式锁定 Asia/Shanghai(同 index.ts fmtTime)。
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(d);
  const v = (t) => {
    const found = parts.find((p) => p.type === t);
    return found ? found.value : '';
  };
  return `${v('year')}-${v('month')}-${v('day')} ${v('hour')}:${v('minute')}`;
}

function computeStages(order) {
  const tracking = (order && order.tracking) || null;
  const events = (tracking && tracking.events) || [];
  const hasTracking = events.length > 0;
  const isDelivered = !!order && order.status === 'COMPLETED';

  let shippedAt = null;
  let inTransitAt = null;
  let deliveredAt = null;

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

  const done = 'tracking-timeline__node tracking-timeline__node--done';
  const pending = 'tracking-timeline__node';
  return {
    shippedAt,
    shippedClass: hasTracking ? done : pending,
    inTransitAt,
    inTransitClass: inTransitAt ? done : pending,
    deliveredAt,
    deliveredClass: deliveredAt ? done : pending,
  };
}

function shouldShow(order) {
  // PENDING / PAID / CANCELLED 不显示时间线(没意义)
  return !!order && ['SHIPPED', 'COMPLETED', 'REFUNDING', 'REFUNDED'].includes(order.status);
}

Component({
  properties: {
    order: { type: Object, value: null },
  },

  data: {
    visible: false,
    stages: null,
    createdAtText: '',
    trackingNumber: '',
    carrier: '',
  },

  observers: {
    order: function (order) {
      if (!order) {
        this.setData({ visible: false });
        return;
      }
      const visible = shouldShow(order);
      const stages = visible ? computeStages(order) : null;
      const tracking = order.tracking;
      this.setData({
        visible,
        stages: stages ?? {
          shippedAt: null, shippedClass: 'tracking-timeline__node',
          inTransitAt: null, inTransitClass: 'tracking-timeline__node',
          deliveredAt: null, deliveredClass: 'tracking-timeline__node',
        },
        createdAtText: formatTime(order.createdAt),
        trackingNumber: tracking?.trackingNumber ?? '',
        carrier: tracking?.carrier ?? '',
      });
    },
  },
});

function formatTime(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
