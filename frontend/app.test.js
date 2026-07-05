/**
 * frontend/app.test.js
 *
 * mp-backend-contract-gaps Gap 4 / D5:app.js 冷启动未把已登录用户的
 * accessToken 桥接到 globalData.token —— 小程序冷启动时如果用户是上一次
 * session 遗留下来的登录状态(accessToken/refreshToken 已经在 storage
 * 里),但这次冷启动没有触发新的 login() 调用,globalData.token 就一直
 * 停在初始值 null。任何仍走 utils/request.js 这套 legacy 请求层的已登录
 * 接口(utils/request.js 只在 app.globalData.token 为真值时才附加
 * Authorization 头)会静默地不带 token 发请求 —— 后端把它当未登录处理,
 * 前端这边完全看不到任何错误。已确认的真实影响:mp-04 购物车 / mp-06
 * 订单确认页的"默认收货地址自动选中"在这种冷启动场景下静默失效。
 *
 * 这里直接对 app.js 的 onLaunch 做单测:mock
 * src/shared/api/storage.js 的 tokenStorage.getAccessToken(),断言
 * onLaunch() 跑完之后 globalData.token 被正确桥接成 storage 里的值。
 */

// storage.js 在本文件刚落地时(任务 4.1)还没建(4.2 才建),用
// { virtual: true } 让 jest.mock 不因为模块文件当前不存在而报错;
// storage.js 真正落地之后这个 mock 依然按预期拦截同一路径的 require。
const mockGetAccessToken = jest.fn();
jest.mock(
  './src/shared/api/storage.js',
  () => ({ tokenStorage: { getAccessToken: mockGetAccessToken } }),
  { virtual: true }
);

// app.js 已有的 setBaseUrl 桥接依赖这个模块;这里只关心 token 桥接这一行为,
// 用最小 mock 避免真实 request.js 的其它副作用干扰断言。
jest.mock('./src/shared/api/request.js', () => ({
  setBaseUrl: jest.fn(),
}));

// refreshFlags 会发起网络请求(与本任务无关的既有行为),mock 掉避免
// 产生未处理的 promise 或污染这个测试的断言。
jest.mock('./utils/featureflag.js', () => ({ refreshFlags: jest.fn() }));

// 捕获 App(config) —— 与仓库里 Page(config) 单测(如 pages/cart/__tests__/
// cart.test.js)相同的既有模式。
let appConfig;
global.App = (config) => {
  appConfig = config;
};

require('./app.js');

describe('app.js onLaunch —— 冷启动 accessToken 桥接到 globalData.token(Gap 4 / D5)', () => {
  let ctx;

  const makeCtx = () => {
    const c = {
      globalData: JSON.parse(JSON.stringify(appConfig.globalData)),
    };
    Object.keys(appConfig).forEach((key) => {
      if (typeof appConfig[key] === 'function' && key !== 'globalData') {
        c[key] = appConfig[key].bind(c);
      }
    });
    return c;
  };

  beforeEach(() => {
    jest.clearAllMocks();
    ctx = makeCtx();
  });

  it('已登录冷启动(storage 里有 accessToken,但本次冷启动没走 login())—— onLaunch 后 globalData.token 等于 storage 里的值', () => {
    mockGetAccessToken.mockReturnValue('dev-access-token-abc');

    ctx.onLaunch();

    expect(ctx.globalData.token).toBe('dev-access-token-abc');
  });

  it('未登录冷启动(storage 里没有 accessToken)—— globalData.token 为 null,onLaunch 不抛异常', () => {
    mockGetAccessToken.mockReturnValue(null);

    expect(() => ctx.onLaunch()).not.toThrow();
    expect(ctx.globalData.token).toBeNull();
  });
});
