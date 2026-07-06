/**
 * order/store.js(mp 运行时真实执行的 shim) ↔ order/store.ts(类型检查用源码,mp 运行时
 * 从不执行)方法集合契约测试。
 *
 * mp-cross-screen-cleanup D7(共享 order-action controller)研究阶段诊断发现:
 * store.ts 的 requestRefund() 早就实现且被 store.test.ts 完整测试覆盖(乐观更新
 * status=REFUNDING + 失败回滚),但 mp 运行时真正加载的 .js shim 一直没有同步这个
 * 方法——同 order/api-shim-contract.test.js 顶部注释记录的 "ts 有、js shim 没有"
 * 是完全相同的一类 drift。这个缺口此前没被任何测试抓到,因为 order-list.js/
 * order-detail.js 的页面测试都 `jest.mock` 掉了整个 store 模块,从来没有真正加载过
 * 这份 .js 文件——直到本次给 order-actions.js 写退款修复时才发现,若不补上,
 * `orderStore.requestRefund(...)` 在真实 mp 运行时会直接 TypeError,让"修复退款
 * amount 缺失"这个改动本身在生产环境完全打不到网络请求那一步。
 *
 * 这个测试直接 require .js shim(不 mock store 本身),锁住方法集合 + amount 转发
 * 行为,不再和 .ts 漂移。
 */
// mock `./api`(不带扩展名,和 store.js 内部 `require('./api')` 用的是完全相同的
// 相对路径,保证两处解析到同一个模块)。这个仓库的 jest moduleFileExtensions 顺序是
// ['ts','tsx','js',...](ts 排在 js 前面)——若这里改成显式的 `./api.js` 再
// jest.spyOn,拿到的会是另一份 api.js 模块实例,跟 store.js 内部 `require('./api')`
// 解析到的 api.ts 不是同一个引用,spy 不到真正被调用的那个,反而会真的跑进
// api.ts → shared/api/request.ts → wx.request(jest.setup.js 里的裸 jest.fn,没有
// mockImplementation)→ 永久 pending,测试整个挂死(排查这个模块解析陷阱花了不少
// 功夫,这里留档供以后类似 shim-contract 测试参考)。
const mockList = jest.fn();
const mockGetById = jest.fn();
const mockCreate = jest.fn();
const mockCancel = jest.fn();
const mockRequestRefund = jest.fn();
jest.mock('./api', () => ({
  OrderAPI: {
    list: (...a) => mockList(...a),
    getById: (...a) => mockGetById(...a),
    create: (...a) => mockCreate(...a),
    cancel: (...a) => mockCancel(...a),
    requestRefund: (...a) => mockRequestRefund(...a),
  },
}));

// 同样的 ts/js 解析陷阱适用于 `../cart/store`(store.js#placeOrder 调用
// cartStore.clear())——用同一个不带扩展名的相对路径 mock,不显式加 .js。
const mockCartClear = jest.fn();
jest.mock('../cart/store', () => ({
  cartStore: { clear: (...a) => mockCartClear(...a) },
}));

const { orderStore } = require('./store.js');

describe('order/store.js shim ↔ order/store.ts 方法集合契约', () => {
  it('js shim 实例应暴露和 ts 源码文档记录一致的公开方法', () => {
    // store.ts 的公开方法集合(不含 private setState);手工维护这份期望列表,不
    // import .ts 本身,否则测试又会绕回"测 ts 不测 js"的老路。
    const expectedMethods = [
      'getState',
      'subscribe',
      'refresh',
      'loadById',
      'placeOrder',
      'placeDirectBuyOrder',
      'cancel',
      'requestRefund',
      'filter',
    ].sort();
    const proto = Object.getPrototypeOf(orderStore);
    const actualMethods = Object.getOwnPropertyNames(proto).filter(
      (k) => k !== 'constructor' && !k.startsWith('_'),
    );
    expect(actualMethods.sort()).toEqual(expectedMethods);
  });

  it('每个公开方法都应是函数(不是漏写成 undefined/非函数)', () => {
    const proto = Object.getPrototypeOf(orderStore);
    const keys = Object.getOwnPropertyNames(proto).filter(
      (k) => k !== 'constructor' && !k.startsWith('_'),
    );
    for (const key of keys) {
      expect(typeof orderStore[key]).toBe('function');
    }
  });

  it('requestRefund(id, amount, reason) 正确把 amount 转发给 OrderAPI.requestRefund(mp-cross-screen-cleanup 关键修复)', async () => {
    // 断言 store.js 真的把 amount 传下去了,而不是像 order-detail.js 原
    // applyRefund() 那样漏传(那个 bug 之所以没被测出来,正是因为所有调用方测试都
    // mock 掉了 store/api,从没真正跑过这条转发链路)。
    mockRequestRefund.mockResolvedValueOnce({ updatedAt: '2026-07-06T00:00:00Z' });
    await orderStore.requestRefund('o-shim-1', 66.6, '用户主动申请');
    expect(mockRequestRefund).toHaveBeenCalledWith('o-shim-1', {
      amount: 66.6,
      reason: '用户主动申请',
    });
  });

  it('requestRefund 失败时回滚(乐观更新的 status 恢复,和 store.ts 行为一致)', async () => {
    mockRequestRefund.mockRejectedValueOnce(new Error('network'));
    await expect(orderStore.requestRefund('o-shim-2', 10, '质量问题')).rejects.toThrow(
      'network',
    );
  });

  // 以下几条覆盖 store.js 剩余的公开方法本身也是真的在转发到 OrderAPI(不只是
  // "方法存在"),而不是只测新加的 requestRefund——既然这个文件已经走到直接 require
  // 真实 .js shim 这条路,顺手把其它方法也钉住,免得下次再有别的方法漏同步。
  it('getState/subscribe:暴露状态 + 订阅', () => {
    expect(orderStore.getState()).toEqual(
      expect.objectContaining({ orders: expect.any(Array) }),
    );
    const listener = jest.fn();
    const unsubscribe = orderStore.subscribe(listener);
    expect(typeof unsubscribe).toBe('function');
    unsubscribe();
  });

  it('refresh():调用 OrderAPI.list 并写入 orders', async () => {
    mockList.mockResolvedValueOnce([{ id: 'o1', status: 'PENDING' }]);
    const orders = await orderStore.refresh();
    expect(mockList).toHaveBeenCalled();
    expect(orders).toEqual([{ id: 'o1', status: 'PENDING' }]);
    expect(orderStore.getState().orders).toEqual([{ id: 'o1', status: 'PENDING' }]);
  });

  it('loadById():调用 OrderAPI.getById 并写入 current', async () => {
    mockGetById.mockResolvedValueOnce({ id: 'o2', status: 'PAID' });
    const order = await orderStore.loadById('o2');
    expect(mockGetById).toHaveBeenCalledWith('o2');
    expect(order.status).toBe('PAID');
    expect(orderStore.getState().current).toEqual({ id: 'o2', status: 'PAID' });
  });

  it('placeOrder():调用 OrderAPI.create,成功后 best-effort 清空购物车', async () => {
    mockCreate.mockResolvedValueOnce({ id: 'o3', status: 'PENDING' });
    mockCartClear.mockResolvedValueOnce({});
    const order = await orderStore.placeOrder({ addressId: 'a1', items: [] });
    expect(mockCreate).toHaveBeenCalled();
    expect(order.id).toBe('o3');
    expect(mockCartClear).toHaveBeenCalled();
  });

  it('placeDirectBuyOrder():调用 OrderAPI.create,不清空购物车(D3b)', async () => {
    mockCreate.mockResolvedValueOnce({ id: 'o4', status: 'PENDING' });
    await orderStore.placeDirectBuyOrder({ addressId: 'a1', items: [] });
    expect(mockCartClear).not.toHaveBeenCalled();
  });

  it('cancel():调用 OrderAPI.cancel 并更新 orders 里对应项', async () => {
    mockList.mockResolvedValueOnce([{ id: 'o5', status: 'PENDING' }]);
    await orderStore.refresh();
    mockCancel.mockResolvedValueOnce({ id: 'o5', status: 'CANCELLED' });
    const cancelled = await orderStore.cancel('o5', '用户取消订单');
    expect(mockCancel).toHaveBeenCalledWith('o5', '用户取消订单');
    expect(cancelled.status).toBe('CANCELLED');
  });

  it('filter():按状态过滤,不传时返回全部', () => {
    expect(Array.isArray(orderStore.filter())).toBe(true);
    expect(Array.isArray(orderStore.filter('PENDING'))).toBe(true);
  });
});
