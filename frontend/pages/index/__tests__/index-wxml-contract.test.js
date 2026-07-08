/**
 * index.wxml ↔ index.js bindtap 契约(防死绑定回归)。
 *
 * 背景(mp-cross-screen-cleanup change,design.md D2):这次改动把 index.js
 * 的 `addToCart` 重命名为 `onAddToCart`,必须同步改 index.wxml 里
 * `catchtap="addToCart"` → `catchtap="onAddToCart"`,否则首页商品卡片的
 * "加入购物车" 按钮在真实小程序里会静默无反应(WeChat 不报错)。复用
 * `pages-sub/user/address/__tests__/address-list-wxml-contract.test.js`
 * 已验证过的通用扫描模式:抓 wxml 里所有 bindtap/catchtap 目标,断言目标名
 * 必须是 index.js 里真实存在的方法。
 */
const fs = require('fs');
const path = require('path');

const WXML_PATH = path.resolve(__dirname, '../index.wxml');

global.wx = {
  showLoading: jest.fn(),
  hideLoading: jest.fn(),
  showToast: jest.fn(),
  navigateTo: jest.fn(),
  stopPullDownRefresh: jest.fn(),
  getStorageSync: jest.fn(() => ''),
};
const mockApp = { globalData: { userInfo: { id: 'u-1' } } };
global.getApp = jest.fn(() => mockApp);

jest.mock('../../../src/features/cart/api', () => ({
  CartAPI: { addItem: jest.fn().mockResolvedValue() },
}));
jest.mock('../../../src/api/banner.js', () => ({
  BannerAPI: { getBanners: jest.fn().mockResolvedValue([]) },
}));
jest.mock('../../../src/modules/productList/productList.js', () => ({
  ProductListModule: jest.fn().mockImplementation(() => ({
    loadProducts: jest.fn().mockResolvedValue(),
    refreshProducts: jest.fn().mockResolvedValue(),
    loadNextPage: jest.fn().mockResolvedValue(),
    clearError: jest.fn(),
    state: { products: [], isLoading: false, isError: false, pagination: { currentPage: 0, totalPages: 1, totalProducts: 0 } },
    isEmpty: false,
    isLoading: false,
    hasNext: false,
    getErrorMessage: () => '',
    getEmptyStateMessage: () => '',
  })),
}));

let pageConfig;
global.Page = (config) => {
  pageConfig = config;
};
require('../index.js');

describe('index.wxml ↔ index.js bindtap 契约(防死绑定回归)', () => {
  let wxml;

  beforeAll(() => {
    wxml = fs.readFileSync(WXML_PATH, 'utf8');
  });

  it('wxml 里每个 bindtap/catchtap 目标都必须是 index.js 里真实存在的方法', () => {
    const matches = [...wxml.matchAll(/(?:bind|catch)tap="([^"]+)"/g)].map((m) => m[1]);
    expect(matches.length).toBeGreaterThan(0);
    for (const name of matches) {
      expect(typeof pageConfig[name]).toBe('function');
    }
  });

  it('商品卡片加购按钮 catchtap="onAddToCart"(mp-cross-screen-cleanup:addToCart → onAddToCart)', () => {
    expect(wxml).toMatch(/catchtap="onAddToCart"/);
    expect(wxml).not.toMatch(/catchtap="addToCart"/);
  });
});
