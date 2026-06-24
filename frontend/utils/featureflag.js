// utils/featureflag.js
//
// 小程序客户端 Feature Flag SDK
// - isEnabled(flagKey)   同步读缓存（wx.storage），不联网
// - refreshFlags()       异步拉取最新 flags 并写缓存；API 失败时静默保留旧缓存

const { request } = require('./request.js');

const STORAGE_KEY = 'feature_flags';
const API_PATH = '/api/featureflags';

/**
 * 同步判断 flag 是否开启。
 * 从 wx.storage 缓存读取，不发网络请求。
 * @param {string} flagKey
 * @returns {boolean}
 */
function isEnabled(flagKey) {
  const flags = wx.getStorageSync(STORAGE_KEY);
  if (!flags || !Array.isArray(flags)) return false;
  const flag = flags.find(f => f.flagKey === flagKey);
  return flag ? flag.enabled === true : false;
}

/**
 * 拉取最新 flags 并更新缓存（offline-first：API 失败时静默保留旧缓存，不抛异常）。
 * @returns {Promise<void>}
 */
async function refreshFlags() {
  try {
    const flags = await request({ url: API_PATH, method: 'GET' });
    wx.setStorageSync(STORAGE_KEY, flags);
  } catch (e) {
    // 网络失败 / 服务不可达 → 保留旧缓存，不上抛
  }
}

module.exports = { isEnabled, refreshFlags };
