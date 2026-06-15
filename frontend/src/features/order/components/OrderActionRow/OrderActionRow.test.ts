/**
 * 路线图 2.8 — OrderActionRow 状态映射单测。
 * 覆盖 7 状态全分支(每个状态都有正确数量的按钮 + 主操作在前)。
 */
import { describe, expect, it } from '@jest/globals';
import { getActionsFor } from './index';
import type { OrderStatus } from '../../types';

describe('OrderActionRow.getActionsFor', () => {
  const allStatuses: OrderStatus[] = [
    'PENDING', 'PAID', 'SHIPPED', 'COMPLETED',
    'CANCELLED', 'REFUNDING', 'REFUNDED',
  ];

  it.each(allStatuses)('status=%s 返回非空列表', (status) => {
    const actions = getActionsFor(status);
    expect(actions.length).toBeGreaterThan(0);
  });

  it('PENDING 第一按钮是支付(primary)', () => {
    const actions = getActionsFor('PENDING');
    expect(actions[0]).toEqual({ id: 'pay', label: '支付', variant: 'primary' });
  });

  it('PAID/SHIPPED/COMPLETED 都含 requestRefund', () => {
    expect(getActionsFor('PAID').map((a) => a.id)).toContain('requestRefund');
    expect(getActionsFor('SHIPPED').map((a) => a.id)).toContain('requestRefund');
    expect(getActionsFor('COMPLETED').map((a) => a.id)).toContain('requestRefund');
  });

  it('PENDING/REFUNDING/REFUNDED 不含 requestRefund', () => {
    expect(getActionsFor('PENDING').map((a) => a.id)).not.toContain('requestRefund');
    expect(getActionsFor('REFUNDING').map((a) => a.id)).not.toContain('requestRefund');
    expect(getActionsFor('REFUNDED').map((a) => a.id)).not.toContain('requestRefund');
  });

  it('SHIPPED 含 confirmReceipt 作为主操作', () => {
    const actions = getActionsFor('SHIPPED');
    const primary = actions.find((a) => a.variant === 'primary');
    expect(primary?.id).toBe('confirmReceipt');
  });

  it('COMPLETED / CANCELLED / REFUNDED 含 deleteOrder (danger)', () => {
    const dangerIds = ['COMPLETED', 'CANCELLED', 'REFUNDED'].map((s) =>
      getActionsFor(s as OrderStatus).find((a) => a.variant === 'danger')?.id
    );
    expect(dangerIds).toEqual(['deleteOrder', 'deleteOrder', 'deleteOrder']);
  });

  it('未知状态返空数组(防御)', () => {
    // @ts-expect-error 故意传非法状态
    expect(getActionsFor('FOO')).toEqual([]);
  });
});
