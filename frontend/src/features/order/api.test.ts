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

  it('list(): GET /api/orders 解包 Page.content', async () => {
    // 后端真实返 Spring Page { content[] };list() 须解包成数组(原 mock 裸数组 = 假绿)
    setWxResponse({ content: [], totalElements: 0, number: 0, size: 20 });
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

  // ===== mp-08 5 状态操作端点(路线图 2.9)=====

  it('pay(): POST /api/orders/{id}/pay with paymentMethod', async () => {
    setWxResponse({ id: 'o1', status: 'PAID' });
    await OrderAPI.pay('o1');
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.url).toBe('http://test.local/api/orders/o1/pay');
    expect(call.method).toBe('POST');
    expect(call.data).toEqual({ paymentMethod: 'wechat' });
  });

  it('remindShip(): POST /api/orders/{id}/remind-ship', async () => {
    setWxResponse(null, 204);
    await OrderAPI.remindShip('o1');
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.url).toBe('http://test.local/api/orders/o1/remind-ship');
    expect(call.method).toBe('POST');
  });

  it('confirmReceive(): POST /api/orders/{id}/confirm-receive', async () => {
    setWxResponse({ id: 'o1', status: 'COMPLETED' });
    await OrderAPI.confirmReceive('o1');
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.url).toBe('http://test.local/api/orders/o1/confirm-receive');
    expect(call.method).toBe('POST');
  });

  it('rebuy(): POST /api/orders/{id}/rebuy returns CartItem[]', async () => {
    setWxResponse([{ productId: 'p1', quantity: 2, selected: true, addedAt: '2026-06-13T00:00:00Z' }]);
    const items = await OrderAPI.rebuy('o1');
    expect(items).toHaveLength(1);
    expect(items[0].productId).toBe('p1');
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.url).toBe('http://test.local/api/orders/o1/rebuy');
    expect(call.method).toBe('POST');
  });

  // ===== 路线图 4.10:申请退款 =====

  it('requestRefund(): POST /api/orders/{id}/refund with amount+reason', async () => {
    setWxResponse({
      id: 'r1', orderId: 'o1', userId: 'u1',
      amount: 50, reason: '质量问题', status: 'REQUESTED',
      createdAt: '2026-06-13T00:00:00Z', updatedAt: '2026-06-13T00:00:00Z',
    });
    const refund = await OrderAPI.requestRefund('o1', { amount: 50, reason: '质量问题' });
    expect(refund.id).toBe('r1');
    expect(refund.status).toBe('REQUESTED');
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.url).toBe('http://test.local/api/orders/o1/refund');
    expect(call.method).toBe('POST');
    expect(call.data).toEqual({ amount: 50, reason: '质量问题' });
  });

  it('attaches Authorization header', async () => {
    setWxResponse({});
    await OrderAPI.list();
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.header.Authorization).toBe('Bearer a');
  });
});
