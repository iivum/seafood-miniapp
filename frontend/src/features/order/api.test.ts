import { OrderAPI } from './api';
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

describe('features/order/api', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    setBaseUrl('http://test.local/api');
    tokenStorage.setTokens('a', 'r');
  });

  it('list(): GET /api/orders', async () => {
    setWxResponse([]);
    const orders = await OrderAPI.list();
    expect(orders).toEqual([]);
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.url).toBe('http://test.local/api/orders');
    expect(call.method).toBe('GET');
  });

  it('getById(): GET /api/orders/{id}', async () => {
    setWxResponse({ id: 'o1' });
    await OrderAPI.getById('o1');
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.url).toBe('http://test.local/api/orders/o1');
  });

  it('create(): POST /api/orders with body', async () => {
    setWxResponse({ id: 'o1' });
    await OrderAPI.create({ addressId: 'a1' });
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.url).toBe('http://test.local/api/orders');
    expect(call.method).toBe('POST');
    expect(call.data).toEqual({ addressId: 'a1' });
  });

  it('cancel(): POST /api/orders/{id}/cancel with reason', async () => {
    setWxResponse({ id: 'o1' });
    await OrderAPI.cancel('o1', 'changed mind');
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.url).toBe('http://test.local/api/orders/o1/cancel');
    expect(call.data).toEqual({ reason: 'changed mind' });
  });

  it('ship(): POST /api/orders/{id}/ship', async () => {
    setWxResponse({ id: 'o1' });
    await OrderAPI.ship('o1');
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.url).toBe('http://test.local/api/orders/o1/ship');
  });

  it('attaches Authorization header', async () => {
    setWxResponse({});
    await OrderAPI.list();
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.header.Authorization).toBe('Bearer a');
  });
});
