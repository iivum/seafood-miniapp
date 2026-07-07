/**
 * user/api.js(mp 运行时真实执行的 shim)单测。
 *
 * mp-od-10 login-userinfo:src/features/user/api.js 此前完全不存在(只有
 * api.ts 类型契约,从未在 mp 运行时被真正 require 过)。这是修复"登录后
 * userInfo 恒为 undefined"全局 bug 的前置——authStore 需要这个 shim 补拉
 * GET /users/me。
 *
 * 这里直接 require('./api.js')(显式扩展名),不用 require('./api'),避免
 * Jest moduleFileExtensions(ts 排在 js 前面)把测试悄悄绕回 api.ts —— 这正是
 * order/api-shim-contract.test.js 记录过的"测 ts 不测 js"坑(实测验证:
 * product/__tests__/api.test.js 用 require('../api') 时覆盖率显示只有
 * api.ts 的语句被执行,api.js 零覆盖)。
 */
jest.mock('../../shared/api/request', () => ({
  get: jest.fn(),
  patch: jest.fn(),
}));

const { UserAPI } = require('./api.js');
const { get, patch } = require('../../shared/api/request');

describe('user/api.js shim', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('导出 me 方法(函数)', () => {
    expect(typeof UserAPI.me).toBe('function');
  });

  it('me() 调 GET /users/me 且带 needAuth: true', async () => {
    const backendUser = {
      id: 'u-1',
      nickname: '小明',
      avatarUrl: 'https://cdn.example.com/avatar/u-1.png',
      role: 'CUSTOMER',
    };
    get.mockResolvedValue(backendUser);

    const user = await UserAPI.me();

    expect(get).toHaveBeenCalledWith('/users/me', { needAuth: true });
    expect(user).toEqual(backendUser);
  });

  it('导出 bindPhone 方法(函数)', () => {
    expect(typeof UserAPI.bindPhone).toBe('function');
  });

  it('bindPhone(code) 调 PATCH /users/me/phone 且带 needAuth: true', async () => {
    const updated = { id: 'u-1', nickname: '小明', role: 'CUSTOMER', phone: '13711112222' };
    patch.mockResolvedValue(updated);

    const user = await UserAPI.bindPhone('dev-abc');

    expect(patch).toHaveBeenCalledWith('/users/me/phone', { code: 'dev-abc' }, { needAuth: true });
    expect(user).toEqual(updated);
  });
});
