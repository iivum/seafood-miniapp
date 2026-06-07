import { CartAPI } from './api';
import { setBaseUrl } from '../../shared/api/request';
import { tokenStorage } from '../../shared/api/storage';

function setWxResponse(data: unknown, statusCode = 200) {
  (wx.request as jest.Mock).mockImplementation((opts: {
    success: (res: unknown) => void;
    fail: (err: unknown) => void;
  }) => {
    opts.success({ statusCode, data });
  });
}

describe('features/cart/api', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    setBaseUrl('http://test.local/api');
    tokenStorage.clear();
    tokenStorage.setTokens('access-1', 'refresh-1');
  });

  it('get(): GET /api/cart with Authorization header', async () => {
    setWxResponse({ id: 'c1', items: [] });
    const cart = await CartAPI.get();
    expect(cart.id).toBe('c1');
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.url).toBe('http://test.local/api/cart');
    expect(call.method).toBe('GET');
    expect(call.header.Authorization).toBe('Bearer access-1');
  });

  it('addItem(): POST /api/cart/items with body', async () => {
    setWxResponse({ id: 'c1', items: [] });
    await CartAPI.addItem({ productId: 'p1', quantity: 2 });
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.url).toBe('http://test.local/api/cart/items');
    expect(call.method).toBe('POST');
    expect(call.data).toEqual({ productId: 'p1', quantity: 2 });
  });

  it('updateItem(): PUT /api/cart/items/{id} with body', async () => {
    setWxResponse({ id: 'c1', items: [] });
    await CartAPI.updateItem('p1', { productId: 'p1', quantity: 3 });
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.url).toBe('http://test.local/api/cart/items/p1');
    expect(call.method).toBe('PUT');
    expect(call.data).toEqual({ productId: 'p1', quantity: 3 });
  });

  it('removeItem(): DELETE /api/cart/items/{id}', async () => {
    setWxResponse({ id: 'c1', items: [] });
    await CartAPI.removeItem('p1');
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.url).toBe('http://test.local/api/cart/items/p1');
    expect(call.method).toBe('DELETE');
  });

  it('toggleItem(): PATCH /api/cart/items/{id}', async () => {
    setWxResponse({ id: 'c1', items: [] });
    await CartAPI.toggleItem('p1');
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.url).toBe('http://test.local/api/cart/items/p1');
    expect(call.method).toBe('PATCH');
  });

  it('clear(): DELETE /api/cart', async () => {
    setWxResponse({ id: 'c1', items: [] });
    await CartAPI.clear();
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.url).toBe('http://test.local/api/cart');
    expect(call.method).toBe('DELETE');
  });

  it('URL-encodes product ids with special characters', async () => {
    setWxResponse({ id: 'c1', items: [] });
    await CartAPI.removeItem('a/b');
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.url).toBe('http://test.local/api/cart/items/a%2Fb');
  });
});
