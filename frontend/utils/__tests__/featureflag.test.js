// utils/__tests__/featureflag.test.js
//
// 单元测试：featureflag.js 的 isEnabled / refreshFlags
// Mock wx.getStorageSync / wx.setStorageSync 和 request 模块

jest.mock('../request.js', () => ({
  request: jest.fn(),
}));

const { request } = require('../request.js');
const { isEnabled, refreshFlags } = require('../featureflag.js');

const STORAGE_KEY = 'feature_flags';

describe('isEnabled', () => {
  it('isEnabled_returnsFalse_whenFlagsNotCached — 未缓存时默认 false', () => {
    // wx.getStorageSync 对未设置的 key 返回 ''（jest.setup.js 中定义）
    wx.getStorageSync.mockReturnValue('');
    expect(isEnabled('flag-x')).toBe(false);
  });

  it('isEnabled_returnsTrue_whenFlagEnabled — 缓存中 flag enabled=true', () => {
    wx.getStorageSync.mockReturnValue([
      { flagKey: 'my-flag', enabled: true },
    ]);
    expect(isEnabled('my-flag')).toBe(true);
  });

  it('isEnabled_returnsFalse_whenFlagDisabled — 缓存中 flag enabled=false', () => {
    wx.getStorageSync.mockReturnValue([
      { flagKey: 'my-flag', enabled: false },
    ]);
    expect(isEnabled('my-flag')).toBe(false);
  });

  it('isEnabled_returnsFalse_whenFlagKeyNotInCache — flagKey 不存在时 false', () => {
    wx.getStorageSync.mockReturnValue([
      { flagKey: 'other-flag', enabled: true },
    ]);
    expect(isEnabled('missing-flag')).toBe(false);
  });
});

describe('refreshFlags', () => {
  it('refreshFlags_callsApiAndUpdatesStorage — 调用 /api/featureflags 并写入 storage', async () => {
    const flags = [{ flagKey: 'ff-1', enabled: true }];
    request.mockResolvedValue(flags);

    await refreshFlags();

    expect(request).toHaveBeenCalledWith({ url: '/api/featureflags', method: 'GET' });
    expect(wx.setStorageSync).toHaveBeenCalledWith(STORAGE_KEY, flags);
  });

  it('refreshFlags_doesNotUpdateStorage_onApiError — API 失败时保留旧缓存，不抛异常', async () => {
    request.mockRejectedValue(new Error('network error'));

    // 不抛异常
    await expect(refreshFlags()).resolves.toBeUndefined();
    // setStorageSync 不应被调用
    expect(wx.setStorageSync).not.toHaveBeenCalled();
  });
});
