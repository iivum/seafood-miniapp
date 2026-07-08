/**
 * favorites-list.wxml ↔ favorites-list.js 契约测试(同 address-list-wxml-contract.test.js
 * 惯例)——扫 wxml 里所有 bindtap/catchtap,断言目标方法在真实 Page(config) 上存在。
 */
const fs = require('fs');
const path = require('path');

global.wx = { showToast: jest.fn(), navigateTo: jest.fn(), stopPullDownRefresh: jest.fn() };
jest.mock('../../../../src/features/favorite/api', () => ({
  FavoriteAPI: { list: jest.fn().mockResolvedValue([]), remove: jest.fn(), add: jest.fn() },
}));

let pageConfig;
global.Page = (config) => {
  pageConfig = config;
};
require('../favorites-list.js');

describe('favorites-list.wxml ↔ .js bindtap 契约', () => {
  it('每个 (bind|catch)tap 目标在 Page config 上都是真实存在的函数', () => {
    const wxml = fs.readFileSync(
      path.join(__dirname, '../favorites-list.wxml'), 'utf8',
    );
    const matches = [...wxml.matchAll(/(?:bind|catch)tap="([^"]+)"/g)].map((m) => m[1]);
    expect(matches.length).toBeGreaterThan(0);
    for (const name of matches) {
      expect(typeof pageConfig[name]).toBe('function');
    }
  });
});
