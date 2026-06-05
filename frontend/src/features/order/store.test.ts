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
