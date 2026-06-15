import { orderStore, OrderStore } from './store';
import type { Order } from './types';

function setWxResponseSequence(responses: unknown[]) {
  let i = 0;
  (wx.request as jest.Mock).mockImplementation((opts: {
    success: (res: unknown) => void;
    fail: (err: unknown) => void;
  }) => {
    const r = responses[i++] ?? { errMsg: 'no more' };
    if (r && typeof r === 'object' && 'statusCode' in (r as Record<string, unknown>)) {
      opts.success(r);
    } else {
      opts.fail(r);
    }
  });
}

const sampleOrder: Order = {
  id: 'o1',
  userId: 'u1',
  items: [{ productId: 'p1', productName: 'X', unitPrice: 10, quantity: 2 }],
  totalAmount: 20,
  status: 'PENDING',
  createdAt: '',
  updatedAt: '',
};

describe('features/order/store', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('refresh(): populates orders on success', async () => {
    setWxResponseSequence([{ statusCode: 200, data: [sampleOrder] }]);
    const orders = await orderStore.refresh();
    expect(orders).toEqual([sampleOrder]);
    expect(orderStore.getState().orders).toEqual([sampleOrder]);
  });

  it('refresh(): failure sets isError', async () => {
    setWxResponseSequence([{ errMsg: 'fail' }]);
    await expect(orderStore.refresh()).rejects.toBeTruthy();
    expect(orderStore.getState().isError).toBe(true);
  });

  it('loadById(): sets current', async () => {
    setWxResponseSequence([{ statusCode: 200, data: sampleOrder }]);
    const o = await orderStore.loadById('o1');
    expect(o).toEqual(sampleOrder);
    expect(orderStore.getState().current).toEqual(sampleOrder);
  });

  it('placeOrder(): prepends to orders and clears cart', async () => {
    setWxResponseSequence([
      { statusCode: 200, data: sampleOrder },
      { statusCode: 200, data: { id: 'c1', items: [] } },
    ]);
    const o = await orderStore.placeOrder({ addressId: 'a1' });
    expect(o).toEqual(sampleOrder);
    expect(orderStore.getState().orders[0]).toEqual(sampleOrder);
  });

  it('placeOrder(): cart-clear failure does not break the order', async () => {
    setWxResponseSequence([
      { statusCode: 200, data: sampleOrder },
      { errMsg: 'cart fail' },
    ]);
    const o = await orderStore.placeOrder({ addressId: 'a1' });
    expect(o).toEqual(sampleOrder);
  });

  it('cancel(): updates the matching order in the list', async () => {
    setWxResponseSequence([
      { statusCode: 200, data: [sampleOrder] },
      { statusCode: 200, data: { ...sampleOrder, status: 'CANCELLED', cancelReason: 'reason' } },
    ]);
    await orderStore.refresh();
    const cancelled = await orderStore.cancel('o1', 'reason');
    expect(cancelled.status).toBe('CANCELLED');
    expect(orderStore.getState().orders[0].status).toBe('CANCELLED');
  });

  // ===== 路线图 4.10:requestRefund 乐观更新 + 失败回滚 =====

  it('requestRefund(): optimistically sets status=REFUNDING before await', async () => {
    setWxResponseSequence([
      { statusCode: 200, data: [sampleOrder] },
      { statusCode: 201, data: { id: 'r1', orderId: 'o1', updatedAt: '2026-06-13T00:00:00Z' } },
    ]);
    await orderStore.refresh();
    // 起始状态 PENDING
    expect(orderStore.getState().orders[0].status).toBe('PENDING');
    // requestRefund await 中:乐观更新已生效
    const p = orderStore.requestRefund('o1', 20, '质量问题');
    // await 微任务,先断言乐观状态
    await Promise.resolve();
    expect(orderStore.getState().orders[0].status).toBe('REFUNDING');
    await p;
  });

  it('requestRefund(): rolls back status on API failure', async () => {
    setWxResponseSequence([
      { statusCode: 200, data: [sampleOrder] },
      { errMsg: 'refund fail' },
    ]);
    await orderStore.refresh();
    await expect(orderStore.requestRefund('o1', 20, '质量问题')).rejects.toBeTruthy();
    // 失败回滚:状态应回到 PENDING
    expect(orderStore.getState().orders[0].status).toBe('PENDING');
  });

  it('filter(): returns only matching status', () => {
    (orderStore as unknown as { state: { orders: Order[] } }).state.orders = [
      { ...sampleOrder, status: 'PENDING' },
      { ...sampleOrder, id: 'o2', status: 'SHIPPED' },
    ];
    expect(orderStore.filter('PENDING')).toHaveLength(1);
    expect(orderStore.filter()).toHaveLength(2);
  });

  it('subscribe: listeners receive state updates', () => {
    const listener = jest.fn();
    const unsub = orderStore.subscribe(listener);
    setWxResponseSequence([{ statusCode: 200, data: [sampleOrder] }]);
    return orderStore.refresh().then(() => {
      expect(listener).toHaveBeenCalled();
      unsub();
    });
  });

  it('OrderStore is constructible independently', () => {
    const s = new OrderStore();
    expect(s.getState().orders).toEqual([]);
  });
});
