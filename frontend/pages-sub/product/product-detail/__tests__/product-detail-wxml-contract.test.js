/**
 * product-detail.wxml ↔ product-detail.js bindtap 契约(防死绑定回归)。
 *
 * 背景(mp-cross-screen-cleanup change,design.md D2):这次改动把
 * product-detail.js 的 `goToProductDetail`(推荐商品卡片跳转)重命名为
 * `onGoToProductDetail`,必须同步改 product-detail.wxml:111 的
 * `bindtap="goToProductDetail"` → `bindtap="onGoToProductDetail"`,否则
 * "推荐商品"横向滚动区域整卡点击在真实小程序里会静默无反应(WeChat 不报错)。
 * 复用 `pages-sub/user/address/__tests__/address-list-wxml-contract.test.js`
 * 已验证过的通用扫描模式。
 */
const fs = require('fs');
const path = require('path');

const WXML_PATH = path.resolve(__dirname, '../product-detail.wxml');

global.wx = {
  showToast: jest.fn(),
  showModal: jest.fn(),
  navigateTo: jest.fn(),
  navigateBack: jest.fn(),
  switchTab: jest.fn(),
};
const mockApp = { globalData: { userInfo: { id: 'u-1' } } };
global.getApp = jest.fn(() => mockApp);

jest.mock('../../../../src/features/product/api', () => ({
  ProductAPI: { getById: jest.fn() },
}));
jest.mock('../../../../src/features/cart/store', () => ({
  cartStore: { addItem: jest.fn().mockResolvedValue() },
}));
jest.mock('../../../../src/modules/recommendation/recommendation.js', () => ({
  recommendationModule: {
    getProductRecommendations: jest.fn().mockResolvedValue([]),
    recordPurchase: jest.fn(),
  },
}));

let pageConfig;
global.Page = (config) => {
  pageConfig = config;
};
require('../product-detail.js');

describe('product-detail.wxml ↔ product-detail.js bindtap 契约(防死绑定回归)', () => {
  let wxml;

  beforeAll(() => {
    wxml = fs.readFileSync(WXML_PATH, 'utf8');
  });

  it('wxml 里每个 bindtap/catchtap 目标都必须是 product-detail.js 里真实存在的方法', () => {
    const matches = [...wxml.matchAll(/(?:bind|catch)tap="([^"]+)"/g)].map((m) => m[1]);
    expect(matches.length).toBeGreaterThan(0);
    for (const name of matches) {
      expect(typeof pageConfig[name]).toBe('function');
    }
  });

  it('推荐商品卡片 bindtap="onGoToProductDetail"(mp-cross-screen-cleanup:goToProductDetail → onGoToProductDetail)', () => {
    expect(wxml).toMatch(/bindtap="onGoToProductDetail"/);
    expect(wxml).not.toMatch(/bindtap="goToProductDetail"/);
  });
});
