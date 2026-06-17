// utils/order-detail-derive.js
// 纯函数:输入 OrderResponse 输出 UI 派生数据。
// 跟 order-detail.js 1:1 对应,无副作用无外部依赖,方便 unit test。

/**
 * @typedef {Object} OrderItem
 * @property {string} productId
 * @property {string} productName
 * @property {number|string} unitPrice
 * @property {number} quantity
 */

/**
 * @typedef {Object} OrderTracking
 * @property {string} carrier
 * @property {string} trackingNumber
 * @property {string} deliveredAt
 */

/**
 * @typedef {Object} OrderResponse
 * @property {string} id
 * @property {string} userId
 * @property {OrderItem[]} items
 * @property {number|string} totalAmount
 * @property {string} status
 * @property {string} cancelReason
 * @property {OrderTracking} tracking
 * @property {string} refundId
 * @property {string} estimatedDelivery
 * @property {string} createdAt
 * @property {string} updatedAt
 */

const STATUS_MAP = {
  PENDING: { text: '待支付', color: 'warning' },
  PAID: { text: '待发货', color: 'info' },
  SHIPPED: { text: '冷链在途', color: 'success' },
  COMPLETED: { text: '已签收', color: 'neutral' },
  CANCELLED: { text: '已取消', color: 'error' },
  REFUNDING: { text: '退款中', color: 'error' },
  REFUNDED: { text: '已退款', color: 'neutral' },
};

function pad2(n) { return String(n).padStart(2, '0'); }

function fmtDate(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return `${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
}

function fmtTime(iso) {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  return `${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
}

function deriveBanner(order) {
  const status = (order && order.status) || 'PENDING';
  const meta = STATUS_MAP[status] || STATUS_MAP.PENDING;
  let estimatedText = null;
  if (order && order.estimatedDelivery) {
    const t = fmtTime(order.estimatedDelivery);
    if (t) estimatedText = `预计 ${t} 前送达`;
  }
  let trackingText = null;
  if (order && order.tracking && order.tracking.trackingNumber) {
    const carrier = order.tracking.carrier || '物流';
    trackingText = `${carrier} ${order.tracking.trackingNumber}`;
  }
  return {
    statusText: meta.text,
    statusColor: meta.color,
    estimatedText,
    trackingText,
  };
}

function deriveTimeline(order) {
  const status = (order && order.status) || 'PENDING';
  const isShipped = status === 'SHIPPED' || status === 'COMPLETED';
  const isCompleted = status === 'COMPLETED';
  return [
    {
      label: '下单成功',
      time: fmtDate(order && order.createdAt),
      desc: '订单已提交',
      state: 'done',
    },
    {
      label: '商家拣货',
      time: isShipped ? fmtDate(order && order.updatedAt) : '处理中',
      desc: '商家已完成拣货',
      state: isShipped ? 'done' : 'current',
    },
    {
      label: '顺丰揽收',
      time: isCompleted && order.tracking && order.tracking.deliveredAt
        ? fmtDate(order.tracking.deliveredAt)
        : '—',
      desc: '冷链运输中',
      state: isCompleted ? 'done' : 'future',
    },
  ];
}

module.exports = { deriveBanner, deriveTimeline, fmtDate, fmtTime };
