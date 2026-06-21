import { CartStore, cartStore } from './store';
import type { Cart } from './types';

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

const sampleCart: Cart = {
  id: 'c1',
  userId: 'u1',
  items: [{ productId: 'p1', quantity: 2, selected: true, addedAt: '' }],
  totalQuantity: 2,
  totalSelectedQuantity: 2,
  selectedAmount: 100,
  updatedAt: '',
};

describe('features/cart/store', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('refresh(): success updates state', async () => {
    setWxResponseSequence([{ statusCode: 200, data: sampleCart }]);
    const cart = await cartStore.refresh();
    expect(cart).toEqual(sampleCart);
    expect(cartStore.getState().isLoading).toBe(false);
    expect(cartStore.getState().isError).toBe(false);
  });

  it('refresh(): failure sets isError and errorMessage', async () => {
    setWxResponseSequence([{ errMsg: 'network:fail' }]);
    await expect(cartStore.refresh()).rejects.toBeTruthy();
    expect(cartStore.getState().isError).toBe(true);
    expect(cartStore.getState().errorMessage).toBeTruthy();
  });

  it('addItem(): updates cart state from server response', async () => {
    setWxResponseSequence([{ statusCode: 200, data: { ...sampleCart, totalQuantity: 4 } }]);
    const cart = await cartStore.addItem('p2', 2);
    expect(cart.totalQuantity).toBe(4);
    expect(cartStore.getState().cart.totalQuantity).toBe(4);
  });

  it('removeItem(): updates cart', async () => {
    setWxResponseSequence([{ statusCode: 200, data: { ...sampleCart, items: [] } }]);
    await cartStore.removeItem('p1');
    expect(cartStore.getState().cart.items).toEqual([]);
  });

  it('toggleItem(): updates cart', async () => {
    setWxResponseSequence([{ statusCode: 200, data: { ...sampleCart, items: [{ ...sampleCart.items[0], selected: false }] } }]);
    await cartStore.toggleItem('p1');
    expect(cartStore.getState().cart.items[0].selected).toBe(false);
  });

  it('clear(): resets cart', async () => {
    setWxResponseSequence([{ statusCode: 200, data: { ...sampleCart, items: [] } }]);
    await cartStore.clear();
    expect(cartStore.getState().cart.items).toEqual([]);
  });

  it('getItemCount(): returns totalQuantity', () => {
    cartStore['_state' as keyof typeof cartStore]; // satisfy noUnused
    cartStore['_setState' as keyof typeof cartStore];
    (cartStore as unknown as { state: { cart: Cart; isLoading: boolean; isError: boolean; errorMessage: string | null } }).state = {
      cart: { ...sampleCart },
      isLoading: false,
      isError: false,
      errorMessage: null,
    };
    expect(cartStore.getItemCount()).toBe(2);
  });

  it('addLocal(): persists to wx storage without hitting the network', () => {
    (wx.request as jest.Mock).mockClear();
    cartStore.addLocal({ productId: 'p1', quantity: 1, selected: true, addedAt: '' });
    expect(wx.setStorageSync).toHaveBeenCalled();
    expect(wx.request).not.toHaveBeenCalled();
  });

  it('subscribe: listeners receive state', () => {
    const listener = jest.fn();
    const unsub = cartStore.subscribe(listener);
    setWxResponseSequence([{ statusCode: 200, data: sampleCart }]);
    return cartStore.refresh().then(() => {
      expect(listener).toHaveBeenCalled();
      unsub();
    });
  });

  it('CartStore is constructible independently', () => {
    const s = new CartStore();
    expect(s.getState().cart.items).toEqual([]);
  });
});
