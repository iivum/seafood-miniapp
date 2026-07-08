/**
 * cart.wxml ↔ cart.js bindtap 契约（防死绑定回归）。
 *
 * 背景(mp-cross-screen-cleanup change,task 5 brief
 * `.superpowers/sdd/mcsc-task-5-brief.md`,design.md D2):这次改动把
 * cart.js 的 `selectAddress` 重命名为 `onSelectAddress`,必须同步改
 * cart.wxml:37 的 `bindtap="selectAddress"` → `bindtap="onSelectAddress"`,
 * 否则收货地址卡整卡点击在真实小程序里会静默无反应(WeChat 不报错)。
 * 复用 `pages-sub/user/address/__tests__/address-list-wxml-contract.test.js`
 * 已验证过的通用扫描模式:抓 wxml 里所有 bindtap/catchtap 目标,断言目标名
 * 必须是 cart.js 里真实存在的方法,防止这类 wxml↔JS 死绑定回归(cart.js 文件头
 * 注释记录的 onItemCheckTap/onSelectAllTap 死绑定就是同类问题的历史案例)。
 */
const fs = require('fs');
const path = require('path');

const WXML_PATH = path.resolve(__dirname, '../cart.wxml');

// 复用 cart.test.js 同款最小 mock,拿到真实 pageConfig 用于契约核对。
global.wx = {
  showToast: jest.fn(),
  navigateTo: jest.fn(),
  getStorageSync: jest.fn(),
  setStorageSync: jest.fn(),
};

const mockApp = { globalData: { userInfo: { id: 'user-1', nickname: 'Test' } } };
global.getApp = jest.fn(() => mockApp);

jest.mock('../../../utils/request.js', () => ({
  request: jest.fn().mockResolvedValue([]),
  authRequest: jest.fn(),
}));

jest.mock('../../../src/features/cart/store', () => ({
  cartStore: {
    refresh: jest.fn().mockResolvedValue({ items: [] }),
    updateItem: jest.fn(),
    removeItem: jest.fn(),
    toggleItem: jest.fn(),
  },
}));

jest.mock('../../../utils/cart.js', () => ({ getCart: jest.fn(() => []) }));

let pageConfig;
global.Page = (config) => {
  pageConfig = config;
};
require('../cart.js');

describe('cart.wxml ↔ cart.js bindtap 契约（防死绑定回归）', () => {
  let wxml;

  beforeAll(() => {
    wxml = fs.readFileSync(WXML_PATH, 'utf8');
  });

  it('wxml 里每个 bindtap/catchtap 目标都必须是 cart.js 里真实存在的方法', () => {
    const matches = [...wxml.matchAll(/(?:bind|catch)tap="([^"]+)"/g)].map((m) => m[1]);
    expect(matches.length).toBeGreaterThan(0);
    for (const name of matches) {
      expect(typeof pageConfig[name]).toBe('function');
    }
  });

  it('收货地址卡整卡 bindtap="onSelectAddress"（mp-cross-screen-cleanup:selectAddress → onSelectAddress）', () => {
    expect(wxml).toMatch(/bindtap="onSelectAddress"/);
    expect(wxml).not.toMatch(/bindtap="selectAddress"/);
  });
});
