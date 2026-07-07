const fs = require('fs');
const path = require('path');

global.wx = { showToast: jest.fn(), navigateTo: jest.fn(), stopPullDownRefresh: jest.fn() };
jest.mock('../../../../src/features/productView/api', () => ({
  ProductViewAPI: { list: jest.fn().mockResolvedValue([]), record: jest.fn() },
}));

let pageConfig;
global.Page = (config) => {
  pageConfig = config;
};
require('../footprints-list.js');

describe('footprints-list.wxml ↔ .js bindtap 契约', () => {
  it('每个 (bind|catch)tap 目标在 Page config 上都是真实存在的函数', () => {
    const wxml = fs.readFileSync(
      path.join(__dirname, '../footprints-list.wxml'), 'utf8',
    );
    const matches = [...wxml.matchAll(/(?:bind|catch)tap="([^"]+)"/g)].map((m) => m[1]);
    expect(matches.length).toBeGreaterThan(0);
    for (const name of matches) {
      expect(typeof pageConfig[name]).toBe('function');
    }
  });
});
