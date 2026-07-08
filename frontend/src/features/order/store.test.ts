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

  // ===== D3b(mp-backend-contract-gaps Gap 2):直接购买建单绕开购物车 =====

  it('placeDirectBuyOrder(): prepends to orders, does NOT touch the cart', async () => {
    // 只排一个响应(建单本身)。若实现误加了 cartStore.clear() 调用,
    // 会多发一次 wx.request 消耗掉队列外的 fallback { errMsg: 'no more' }
    // ——下面的 calls.length 断言就会失败,锁住"direct-buy 不清购物车"这条契约。
    setWxResponseSequence([{ statusCode: 200, data: sampleOrder }]);
    const o = await orderStore.placeDirectBuyOrder({
      addressId: 'a1',
      items: [{ productId: 'p1', quantity: 2 }],
    });
    expect(o).toEqual(sampleOrder);
    expect(orderStore.getState().orders[0]).toEqual(sampleOrder);
    expect(orderStore.getState().current).toEqual(sampleOrder);
    expect((wx.request as jest.Mock).mock.calls.length).toBe(1);
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
