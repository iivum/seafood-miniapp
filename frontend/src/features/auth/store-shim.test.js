/**
 * auth/store.js(mp 运行时真实执行的 shim)登录后置获取用户信息回归测试。
 *
 * mp-od-10 login-userinfo:store.test.ts 一直 `import { authStore, AuthStore }
 * from './store'`(Jest moduleFileExtensions 里 ts 排在 js 前面,'./store' 悄悄
 * 解析到 store.ts),从没真正跑过 mp 运行时加载的 store.js —— 与
 * order/api-shim-contract.test.js 记录过的"测 ts 不测 js"是同一个坑。
 *
 * 真实 bug:后端 POST /api/auth/wechat-login 响应体(对应
 * backend/.../TokenResponse.java)从来没有 `user` 字段,store.js 的
 * _applyLoginResponse(res) 却直接用 res.user,恒为 undefined,导致全局
 * authStore.state.user 永远是空 —— 个人中心页即使已登录也显示"点击登录"。
 *
 * 这里直接 require('./store.js')(显式扩展名),并对 './api' 和 '../user/api'
 * 用和 store.js 内部 require 完全相同的相对路径 jest.mock —— 两者从同一目录
 * 解析到同一个绝对路径,mock 才能真正拦截 store.js 实际加载的依赖链路。
 */
jest.mock('./api', () => ({ AuthAPI: { wechatLogin: jest.fn() } }));
jest.mock('../user/api', () => ({ UserAPI: { me: jest.fn() } }));
// 发现:'../../shared/api/request' 在 Jest 下(moduleFileExtensions ts 优先)解析到
// request.ts,而 request.ts 不导出 tokenStorage(store.ts 改从 '../../shared/api/storage'
// 单独 import 它);store.js 却是从 request 这一路径解构 tokenStorage(mp 运行时真实的
// request.js 自带一份 tokenStorage,不依赖 storage.js——storage.js 也确实不存在)。
// 直接 require 会导致 store.js 内部解构出 undefined。这里用真实 request.ts 导出 +
// 真实 storage.ts 的 tokenStorage 合并 mock 补上这个导出缺口,不影响任何真实行为。
jest.mock('../../shared/api/request', () => {
  const actual = jest.requireActual('../../shared/api/request');
  const { tokenStorage } = jest.requireActual('../../shared/api/storage');
  return { ...actual, tokenStorage };
});

const { AuthStore } = require('./store.js');
const { AuthAPI } = require('./api');
const { UserAPI } = require('../user/api');
const { tokenStorage, setBaseUrl } = require('../../shared/api/request');

// 真实后端 TokenResponse.java 的响应形状 —— 注意没有 user 字段。
const backendTokenResponse = {
  accessToken: 'a-1',
  refreshToken: 'r-1',
  accessTokenExpiresAt: '2099-01-01T00:00:00Z',
  refreshTokenExpiresAt: '2099-02-01T00:00:00Z',
  role: 'CUSTOMER',
};

const realUser = {
  id: 'u-1',
  openId: 'wx-openid-1',
  nickname: '张三',
  avatarUrl: 'https://cdn.example.com/avatar/u-1.png',
  role: 'CUSTOMER',
};

describe('features/auth/store.js(mp 运行时真实 shim)', () => {
  let store;

  beforeEach(() => {
    jest.clearAllMocks();
    setBaseUrl('http://test.local/api');
    tokenStorage.clear();
    store = new AuthStore();
  });

  it('登录响应没有 user 字段时,补调 GET /users/me 拿真实用户信息并写入 state.user', async () => {
    AuthAPI.wechatLogin.mockResolvedValue(backendTokenResponse);
    UserAPI.me.mockResolvedValue(realUser);

    await store.loginWithCode('dev-123');

    expect(UserAPI.me).toHaveBeenCalledTimes(1);
    expect(store.getState().user).toEqual(realUser);
    expect(store.getState().isAuthenticated).toBe(true);
  });

  it('UserAPI.me() 失败时静默降级:登录流程仍成功完成,token 已存,user 为 null,不 throw', async () => {
    AuthAPI.wechatLogin.mockResolvedValue(backendTokenResponse);
    UserAPI.me.mockRejectedValue(new Error('network error'));

    let thrown = null;
    try {
      await store.loginWithCode('dev-123');
    } catch (err) {
      thrown = err;
    }

    expect(thrown).toBeNull();
    expect(tokenStorage.getAccessToken()).toBe('a-1');
    expect(store.getState().isAuthenticated).toBe(true);
    expect(store.getState().user).toBeNull();
  });

  it('登录响应已带 user 字段时不重复请求(防御性分支,当前真实后端不会走到)', async () => {
    AuthAPI.wechatLogin.mockResolvedValue({ ...backendTokenResponse, user: realUser });

    await store.loginWithCode('dev-123');

    expect(UserAPI.me).not.toHaveBeenCalled();
    expect(store.getState().user).toEqual(realUser);
  });

  it('login()(wx.login 路径)同样补拉用户信息', async () => {
    global.wx.login.mockImplementation((opts) => opts.success({ code: 'wx-code-1' }));
    AuthAPI.wechatLogin.mockResolvedValue(backendTokenResponse);
    UserAPI.me.mockResolvedValue(realUser);

    await store.login();

    expect(store.getState().user).toEqual(realUser);
  });
});
