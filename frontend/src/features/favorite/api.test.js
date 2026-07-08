/**
 * favorite/api.js(mp 运行时真实执行的 shim)单测。
 *
 * 收藏 + 浏览足迹:直接 require('./api.js')(显式扩展名),不用
 * require('./api'),避免 Jest moduleFileExtensions(ts 排在 js 前面)把测试
 * 悄悄绕回 api.ts —— 同 order/api-shim-contract.test.js / user/api.test.js
 * 记录过的"测 ts 不测 js"坑。
 */
jest.mock('../../shared/api/request', () => ({
  get: jest.fn(),
  post: jest.fn(),
  del: jest.fn(),
}));

const { FavoriteAPI } = require('./api.js');
const { get, post, del } = require('../../shared/api/request');

describe('favorite/api.js shim', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('导出 add/remove/list 三个方法(函数)', () => {
    expect(typeof FavoriteAPI.add).toBe('function');
    expect(typeof FavoriteAPI.remove).toBe('function');
    expect(typeof FavoriteAPI.list).toBe('function');
  });

  it('add(productId) 调 POST /favorites/{id} 且带 needAuth: true', async () => {
    post.mockResolvedValue(['p1']);

    const result = await FavoriteAPI.add('p1');

    expect(post).toHaveBeenCalledWith('/favorites/p1', undefined, { needAuth: true });
    expect(result).toEqual(['p1']);
  });

  it('remove(productId) 调 DELETE /favorites/{id} 且带 needAuth: true', async () => {
    del.mockResolvedValue([]);

    const result = await FavoriteAPI.remove('p1');

    expect(del).toHaveBeenCalledWith('/favorites/p1', { needAuth: true });
    expect(result).toEqual([]);
  });

  it('list() 调 GET /favorites 且带 needAuth: true', async () => {
    const items = [{ productId: 'p1', productName: '三文鱼', price: 58, imageUrl: 'http://img', available: true }];
    get.mockResolvedValue(items);

    const result = await FavoriteAPI.list();

    expect(get).toHaveBeenCalledWith('/favorites', { needAuth: true });
    expect(result).toEqual(items);
  });

  it('add 对商品 id 做 encodeURIComponent(防止 id 含特殊字符拼坏 URL)', async () => {
    post.mockResolvedValue([]);

    await FavoriteAPI.add('p/1');

    expect(post).toHaveBeenCalledWith('/favorites/p%2F1', undefined, { needAuth: true });
  });
});
