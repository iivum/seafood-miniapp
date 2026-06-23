import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { OrderTrackingTimeline } from './OrderTrackingTimeline';
import { computeTimelineStages, shouldShowTimeline } from './timeline';
import type { OrderResponse } from '@/types/api';

const base: OrderResponse = {
  id: 'o1', userId: 'u1', totalAmount: '50.00', status: 'SHIPPED',
  items: [{ productId: 'p1', productName: 'X', unitPrice: '50', quantity: 1 }],
  cancelReason: null, createdAt: '', updatedAt: '',
};

describe('timeline.computeTimelineStages', () => {
  it('no events: all pending', () => {
    const s = computeTimelineStages({ ...base, status: 'SHIPPED' });
    expect(s.shipped.done).toBe(false);
    expect(s.inTransit.done).toBe(false);
    expect(s.delivered.done).toBe(false);
  });

  it('SHIPPED + 2 events: shipped+inTransit done, delivered pending', () => {
    const s = computeTimelineStages({
      ...base, status: 'SHIPPED',
      tracking: {
        carrier: '顺丰', trackingNumber: 'SF123',
        events: [
          { at: '2026-06-02T10:00:00Z', status: 'SHIPPED', location: '上海', description: '已发货' },
          { at: '2026-06-02T18:00:00Z', status: 'IN_TRANSIT', location: '杭州', description: '运输中' },
        ],
      },
    });
    expect(s.shipped.done).toBe(true);
    expect(s.inTransit.done).toBe(true);
    expect(s.delivered.done).toBe(false);
  });

  it('COMPLETED + 3 events: all done', () => {
    const s = computeTimelineStages({
      ...base, status: 'COMPLETED',
      tracking: {
        carrier: '顺丰', trackingNumber: 'SF123',
        events: [
          { at: '2026-06-02T10:00:00Z', status: 'SHIPPED', location: '上海', description: '已发货' },
          { at: '2026-06-02T18:00:00Z', status: 'IN_TRANSIT', location: '杭州', description: '运输中' },
          { at: '2026-06-03T10:00:00Z', status: 'DELIVERED', location: '北京', description: '已签收' },
        ],
      },
    });
    expect(s.shipped.done).toBe(true);
    expect(s.inTransit.done).toBe(true);
    expect(s.delivered.done).toBe(true);
  });
});

describe('timeline.shouldShowTimeline', () => {
  it('shows for SHIPPED / COMPLETED / REFUNDING / REFUNDED', () => {
    expect(shouldShowTimeline({ ...base, status: 'SHIPPED' })).toBe(true);
    expect(shouldShowTimeline({ ...base, status: 'COMPLETED' })).toBe(true);
    expect(shouldShowTimeline({ ...base, status: 'REFUNDING' })).toBe(true);
    expect(shouldShowTimeline({ ...base, status: 'REFUNDED' })).toBe(true);
  });
  it('hides for PENDING / PAID / CANCELLED', () => {
    expect(shouldShowTimeline({ ...base, status: 'PENDING' })).toBe(false);
    expect(shouldShowTimeline({ ...base, status: 'PAID' })).toBe(false);
    expect(shouldShowTimeline({ ...base, status: 'CANCELLED' })).toBe(false);
  });
});

describe('OrderTrackingTimeline component', () => {
  it('renders nothing for PENDING order', () => {
    const { container } = render(<OrderTrackingTimeline order={{ ...base, status: 'PENDING' }} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders 3 nodes (shipped / inTransit / delivered) for COMPLETED with 3 events', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-06-13T00:00:00Z'));
    render(<OrderTrackingTimeline order={{
      ...base, status: 'COMPLETED',
      tracking: {
        carrier: '顺丰', trackingNumber: 'SF123',
        events: [
          { at: '2026-06-02T10:00:00Z', status: 'SHIPPED', location: '上海', description: '已发货' },
          { at: '2026-06-02T18:00:00Z', status: 'IN_TRANSIT', location: '杭州', description: '运输中' },
          { at: '2026-06-03T10:00:00Z', status: 'DELIVERED', location: '北京', description: '已签收' },
        ],
      },
    }} />);
    expect(screen.getAllByText('已发货').length).toBeGreaterThan(0);
    expect(screen.getAllByText('运输中').length).toBeGreaterThan(0);
    expect(screen.getAllByText('已签收').length).toBeGreaterThan(0);
    // carrier + trackingNumber 头
    expect(screen.getByText(/顺丰 · 单号 SF123/)).toBeInTheDocument();
    // 完整轨迹 details
    expect(screen.getByText(/完整轨迹\(3 条\)/)).toBeInTheDocument();
    vi.useRealTimers();
  });

  it('OD: tracking=null + SHIPPED → delivered 节点显示"等待签收"副文案', () => {
    render(<OrderTrackingTimeline order={{
      ...base,
      status: 'SHIPPED',
      tracking: null,
    }} />);
    expect(screen.getByText('等待签收')).toBeInTheDocument();
  });

  it('OD: COMPLETED + 3 events → done 节点有 bg-success class（填充圆）', () => {
    render(<OrderTrackingTimeline order={{
      ...base, status: 'COMPLETED',
      tracking: {
        carrier: '顺丰', trackingNumber: 'SF123',
        events: [
          { at: '2026-06-02T10:00:00Z', status: 'SHIPPED', location: '上海', description: '已发货' },
          { at: '2026-06-02T18:00:00Z', status: 'IN_TRANSIT', location: '杭州', description: '运输中' },
          { at: '2026-06-03T10:00:00Z', status: 'DELIVERED', location: '北京', description: '已签收' },
        ],
      },
    }} />);
    const successNodes = document.querySelectorAll('.bg-success');
    expect(successNodes.length).toBe(3);
  });
});
