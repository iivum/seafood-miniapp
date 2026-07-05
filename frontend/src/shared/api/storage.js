/**
 * Runtime shim for shared/api/storage.ts.
 *
 * mp-backend-contract-gaps Gap 4 / D5:app.js 的冷启动 accessToken →
 * globalData.token 桥接需要 require('./src/shared/api/storage.js'),但这个
 * mp 运行时 shim 此前完全不存在(只有 storage.ts 类型契约)—— 与本仓库反复
 * 出现的"只有 .ts 源码,从没写过对应 .js 运行时 shim"是同一类坑(参见
 * frontend/src/features/user/api.js 的先例)。
 *
 * 只手写这个任务实际消费的 getAccessToken;.ts 源码里的
 * getRefreshToken/setTokens/setUser/getUser/clear 目前没有 mp 运行时调用方,
 * 按既有先例不重复实现整个 .ts 表面,避免维护两份从未被跑到的死代码。
 * 逻辑照抄 storage.ts 的 getAccessToken 实现(见该文件 L25-29)。
 */

const ACCESS_KEY = 'accessToken';

function hasWx() {
  return typeof wx !== 'undefined' && !!wx.getStorageSync;
}

const tokenStorage = {
  getAccessToken() {
    if (!hasWx()) return null;
    const v = wx.getStorageSync(ACCESS_KEY);
    return typeof v === 'string' && v.length > 0 ? v : null;
  },
};

module.exports = { tokenStorage };
