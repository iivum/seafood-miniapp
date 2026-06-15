/**
 * 路线图 4.3 — OrderTrackingTimeline helper 单测。
 */
import { computeStages, shouldShow } from './index';
import type { Order } from '../../types';

const baseOrder: Order = {
  id: 'o1', userId: 'u1',
  items: [{ productId: 'p1', productName: 'X', unitPrice: 10, quantity: 1 }],
  totalAmount: 10, status: 'PENDING',
  createdAt: '2026-06-01T10:00:00Z', updatedAt: '2026-06-01T10:00:00Z',
};

describe('OrderTrackingTimeline.computeStages', () => {
  it('PENDING order: no events, all nodes pending', () => {
    const s = computeStages(baseOrder);
    expect(s.shippedAt).toBeNull();
    expect(s.inTransitAt).toBeNull();
    expect(s.deliveredAt).toBeNull();
    expect(s.shippedClass).toContain('tracking-timeline__node');
    expect(s.shippedClass).not.toContain('--done');
  });

  it('SHIPPED order with 1 event: shipped done, others pending', () => {
    const o: Order = {
      ...baseOrder, status: 'SHIPPED',
      tracking: {
        carrier: '顺丰', trackingNumber: 'SF123',
        events: [{ at: '2026-06-02T10:00:00Z', status: 'SHIPPED', location: '上海', description: '已发货' }],
      },
    };
    const s = computeStages(o);
    expect(s.shippedAt).toBe('2026-06-02 18:00'); // UTC+8 转换(本机时区决定)
    expect(s.shippedClass).toContain('--done');
    expect(s.inTransitAt).toBeNull();
    expect(s.deliveredAt).toBeNull();
  });

  it('SHIPPED order with 2 events: shipped + inTransit done', () => {
    const o: Order = {
      ...baseOrder, status: 'SHIPPED',
      tracking: {
        carrier: '顺丰', trackingNumber: 'SF123',
        events: [
          { at: '2026-06-02T10:00:00Z', status: 'SHIPPED', location: '上海', description: '已发货' },
          { at: '2026-06-02T18:00:00Z', status: 'IN_TRANSIT', location: '杭州', description: '运输中' },
        ],
      },
    };
    const s = computeStages(o);
    expect(s.shippedAt).not.toBeNull();
    expect(s.inTransitAt).not.toBeNull();
    expect(s.inTransitClass).toContain('--done');
  });

  it('COMPLETED order with 3 events: all 4 nodes done', () => {
    const o: Order = {
      ...baseOrder, status: 'COMPLETED',
      tracking: {
        carrier: '顺丰', trackingNumber: 'SF123',
        events: [
          { at: '2026-06-02T10:00:00Z', status: 'SHIPPED', location: '上海', description: '已发货' },
          { at: '2026-06-02T18:00:00Z', status: 'IN_TRANSIT', location: '杭州', description: '运输中' },
          { at: '2026-06-03T10:00:00Z', status: 'DELIVERED', location: '北京', description: '已签收' },
        ],
      },
    };
    const s = computeStages(o);
    expect(s.shippedClass).toContain('--done');
    expect(s.inTransitClass).toContain('--done');
    expect(s.deliveredClass).toContain('--done');
    expect(s.deliveredAt).not.toBeNull();
  });
});

describe('OrderTrackingTimeline.shouldShow', () => {
  it('shows for SHIPPED / COMPLETED / REFUNDING / REFUNDED', () => {
    expect(shouldShow({ ...baseOrder, status: 'SHIPPED' })).toBe(true);
    expect(shouldShow({ ...baseOrder, status: 'COMPLETED' })).toBe(true);
    expect(shouldShow({ ...baseOrder, status: 'REFUNDING' })).toBe(true);
    expect(shouldShow({ ...baseOrder, status: 'REFUNDED' })).toBe(true);
  });
  it('hides for PENDING / PAID / CANCELLED', () => {
    expect(shouldShow({ ...baseOrder, status: 'PENDING' })).toBe(false);
    expect(shouldShow({ ...baseOrder, status: 'PAID' })).toBe(false);
    expect(shouldShow({ ...baseOrder, status: 'CANCELLED' })).toBe(false);
  });
});
