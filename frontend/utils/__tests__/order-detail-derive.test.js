// utils/__tests__/order-detail-derive.test.js
//
// 用法:TZ=UTC npx jest utils/__tests__/order-detail-derive.test.js
// fmtTime 用 new Date().getHours() 受系统 TZ 影响 — 跑时锁 TZ=UTC
// (跟 e2e/mp-od-design / token-parity 约定一致)
const { deriveBanner, deriveTimeline } = require('../order-detail-derive.js');

const baseOrder = {
  id: 'ord-1',
  userId: 'u-1',
  items: [{ productId: 'p1', productName: '三文鱼', unitPrice: 99, quantity: 2 }],
  totalAmount: 198,
  status: 'PENDING',
  cancelReason: null,
  tracking: null,
  refundId: null,
  estimatedDelivery: null,
  createdAt: '2026-06-18T10:00:00Z',
  updatedAt: '2026-06-18T10:00:00Z',
};

describe('deriveBanner', () => {
  it.each([
    ['PENDING', '待支付', 'warning'],
    ['PAID', '待发货', 'info'],
    ['SHIPPED', '冷链在途', 'success'],
    ['COMPLETED', '已签收', 'neutral'],
    ['CANCELLED', '已取消', 'error'],
    ['REFUNDING', '退款中', 'error'],
    ['REFUNDED', '已退款', 'neutral'],
  ])('status=%s → text=%s color=%s', (status, expectedText, expectedColor) => {
    const banner = deriveBanner({ ...baseOrder, status });
    expect(banner.statusText).toBe(expectedText);
    expect(banner.statusColor).toBe(expectedColor);
  });

  it('estimatedDelivery null 时 estimatedText 为 null', () => {
    const banner = deriveBanner({ ...baseOrder, estimatedDelivery: null });
    expect(banner.estimatedText).toBeNull();
  });

  it('estimatedDelivery 有值时 estimatedText 含 HH:mm', () => {
    const banner = deriveBanner({ ...baseOrder, estimatedDelivery: '2026-06-18T14:30:00Z' });
    expect(banner.estimatedText).toMatch(/14:30/);
  });

  it('tracking 有值时 trackingText 显示 carrier + number', () => {
    const banner = deriveBanner({
      ...baseOrder,
      tracking: { carrier: '顺丰', trackingNumber: 'SF1024' },
    });
    expect(banner.trackingText).toBe('顺丰 SF1024');
  });

  it('null order 不抛错', () => {
    const banner = deriveBanner(null);
    expect(banner.statusText).toBe('待支付');
  });
});

describe('deriveTimeline', () => {
  it('严格返回 3 个节点', () => {
    expect(deriveTimeline(baseOrder)).toHaveLength(3);
  });

  it('PENDING 状态:第 1 节点 done,第 2 节点 current,第 3 节点 future', () => {
    const tl = deriveTimeline({ ...baseOrder, status: 'PENDING' });
    expect(tl[0].state).toBe('done');
    expect(tl[1].state).toBe('current');
    expect(tl[2].state).toBe('future');
  });

  it('SHIPPED 状态:第 1+2 节点 done,第 3 节点 future', () => {
    const tl = deriveTimeline({ ...baseOrder, status: 'SHIPPED' });
    expect(tl[0].state).toBe('done');
    expect(tl[1].state).toBe('done');
    expect(tl[2].state).toBe('future');
  });

  it('COMPLETED 状态:全部 done', () => {
    const tl = deriveTimeline({ ...baseOrder, status: 'COMPLETED' });
    expect(tl.every((n) => n.state === 'done')).toBe(true);
  });

  it('null order 返回空或基础 3 节点(不抛错)', () => {
    const tl = deriveTimeline(null);
    expect(Array.isArray(tl)).toBe(true);
    expect(tl).toHaveLength(3);
  });
});