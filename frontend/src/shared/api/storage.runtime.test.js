/**
 * storage.js(mp 运行时真实执行的 shim)单测。
 *
 * mp-backend-contract-gaps Gap 4 / D5:src/shared/api/storage.js 此前
 * 完全不存在(只有 storage.ts 类型契约,从未在 mp 运行时被真正 require
 * 过)—— app.js 冷启动的 accessToken → globalData.token 桥接需要它。这是
 * 本仓库反复出现的同一类坑:.ts 源码有,手写运行时 .js shim 没跟上
 * (参见 frontend/src/features/user/api.js / features/auth/store.js 的
 * 先例)。
 *
 * 这里直接 require('./storage.js')(显式扩展名),不用 require('./storage'),
 * 避免 Jest 配置里 moduleFileExtensions(ts 排在 js 前面)悄悄把测试绕回
 * storage.ts,从而测的是 ts 源码而不是 mp 运行时真正加载的 js 文件 —— 这正是
 * order/api-shim-contract.test.js / user/api.test.js 记录过的"测 ts 不测
 * js"坑,不是假设性的,是本仓库已经踩过的。
 */
const { tokenStorage } = require('./storage.js');

describe('shared/api/storage.js shim', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('导出 tokenStorage.getAccessToken(函数)', () => {
    expect(typeof tokenStorage.getAccessToken).toBe('function');
  });

  it('wx.getStorageSync 返回非空字符串时,getAccessToken 原样返回该字符串', () => {
    global.wx.getStorageSync.mockReturnValueOnce('real-access-token');

    expect(tokenStorage.getAccessToken()).toBe('real-access-token');
    expect(global.wx.getStorageSync).toHaveBeenCalledWith('accessToken');
  });

  it('wx.getStorageSync 返回空字符串(未设置过该 key)时,getAccessToken 返回 null', () => {
    global.wx.getStorageSync.mockReturnValueOnce('');

    expect(tokenStorage.getAccessToken()).toBeNull();
  });

  it('wx 未注入时(防御性分支),getAccessToken 返回 null 且不抛异常', () => {
    const originalWx = global.wx;
    delete global.wx;

    expect(() => tokenStorage.getAccessToken()).not.toThrow();
    expect(tokenStorage.getAccessToken()).toBeNull();

    global.wx = originalWx;
  });
});
