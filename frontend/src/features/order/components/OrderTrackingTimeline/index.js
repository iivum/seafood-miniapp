/**
 * 路线图 4.3 — OrderTrackingTimeline(Wechat Mini Program Component)。
 *
 * <p>行为:接受 {@code order} 属性,内部 observers 触发 {@link computeStages}
 * 重算 stages(已下单 / 已发货 / 运输中 / 已签收)。无 tracking 的早期订单
 * (PENDING / PAID)通过 {@link shouldShow} 不渲染。
 */
const { computeStages, shouldShow } = require('./index.ts-helpers');

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
