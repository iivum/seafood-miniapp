/**
 * productView/api.js(mp 运行时真实执行的 shim)单测。同 favorite/api.test.js
 * 惯例:直接 require('./api.js'),不走 './api',防 ts/js drift。
 */
jest.mock('../../shared/api/request', () => ({
  get: jest.fn(),
  post: jest.fn(),
}));

const { ProductViewAPI } = require('./api.js');
const { get, post } = require('../../shared/api/request');

describe('productView/api.js shim', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('导出 record/list 两个方法(函数)', () => {
    expect(typeof ProductViewAPI.record).toBe('function');
    expect(typeof ProductViewAPI.list).toBe('function');
  });

  it('record(productId) 调 POST /product-views/{id} 且带 needAuth: true', async () => {
    post.mockResolvedValue(undefined);

    await ProductViewAPI.record('p1');

    expect(post).toHaveBeenCalledWith('/product-views/p1', undefined, { needAuth: true });
  });

  it('list() 调 GET /product-views 且带 needAuth: true', async () => {
    const items = [{ productId: 'p1', productName: '龙虾', price: 128, imageUrl: 'http://img', available: true, viewedAt: '2026-07-06T00:00:00Z' }];
    get.mockResolvedValue(items);

    const result = await ProductViewAPI.list();

    expect(get).toHaveBeenCalledWith('/product-views', { needAuth: true });
    expect(result).toEqual(items);
  });
});
