/**
 * 路线图 2.8 + sprint-1-closure 7.1 — OrderActionRow 状态映射单测。
 * 覆盖 7 状态全分支(对齐 {@code openspec/changes/sprint-1-closure/specs/mini-program §Order list and detail})。
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

  // ---- PENDING: cancel + 立即付款 ----
  it('PENDING: cancelOrder + pay(立即付款)', () => {
    const actions = getActionsFor('PENDING');
    const ids = actions.map((a) => a.id);
    expect(ids).toContain('cancelOrder');
    expect(ids).toContain('pay');
    // pay 是主操作
    expect(actions.find((a) => a.id === 'pay')?.variant).toBe('primary');
  });

  // ---- PAID: 提醒发货 + 申请退款 ----
  it('PAID: remindShip + requestRefund', () => {
    const actions = getActionsFor('PAID');
    const ids = actions.map((a) => a.id);
    expect(ids).toContain('remindShip');
    expect(ids).toContain('requestRefund');
  });

  // ---- SHIPPED: 查看物流 + 确认收货(主操作) ----
  it('SHIPPED: viewTracking + confirmReceipt(主操作)', () => {
    const actions = getActionsFor('SHIPPED');
    const ids = actions.map((a) => a.id);
    expect(ids).toContain('viewTracking');
    expect(ids).toContain('confirmReceipt');
    const primary = actions.find((a) => a.variant === 'primary');
    expect(primary?.id).toBe('confirmReceipt');
  });

  // ---- COMPLETED: 评价(占位) + 再次购买(主) + 申请售后 ----
  it('COMPLETED: review(占位) + reorder(主) + afterSale', () => {
    const actions = getActionsFor('COMPLETED');
    const ids = actions.map((a) => a.id);
    expect(ids).toContain('review');
    expect(ids).toContain('reorder');
    expect(ids).toContain('afterSale');
    const primary = actions.find((a) => a.variant === 'primary');
    expect(primary?.id).toBe('reorder');
  });

  // ---- CANCELLED: 删除(danger) + 再次购买(主) ----
  it('CANCELLED: deleteOrder(danger) + reorder(主)', () => {
    const actions = getActionsFor('CANCELLED');
    const danger = actions.find((a) => a.variant === 'danger');
    const primary = actions.find((a) => a.variant === 'primary');
    expect(danger?.id).toBe('deleteOrder');
    expect(primary?.id).toBe('reorder');
  });

  // ---- REFUNDING: 仅 disabled "退款处理中" ----
  it('REFUNDING: 仅 1 个 disabled refundPending,无其他操作', () => {
    const actions = getActionsFor('REFUNDING');
    expect(actions).toHaveLength(1);
    expect(actions[0].id).toBe('refundPending');
    expect(actions[0].variant).toBe('disabled');
  });

  // ---- REFUNDED: 删除 + 再次购买 ----
  it('REFUNDED: deleteOrder(danger) + reorder(主)', () => {
    const actions = getActionsFor('REFUNDED');
    const danger = actions.find((a) => a.variant === 'danger');
    const primary = actions.find((a) => a.variant === 'primary');
    expect(danger?.id).toBe('deleteOrder');
    expect(primary?.id).toBe('reorder');
  });

  it('未知状态返空数组(防御)', () => {
    // @ts-expect-error 故意传非法状态
    expect(getActionsFor('FOO')).toEqual([]);
  });
});
