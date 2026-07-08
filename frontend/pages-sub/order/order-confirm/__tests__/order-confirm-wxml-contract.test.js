/**
 * order-confirm.wxml ↔ order-confirm.js bindtap 契约（防死绑定回归）。
 *
 * 背景(mp-cross-screen-cleanup change,task 5 brief
 * `.superpowers/sdd/mcsc-task-5-brief.md`,design.md D1/D2):这次改动把
 * order-confirm.js 的 `goBack` 重命名为 `onBack`(design.md D1),必须同步改
 * order-confirm.wxml:22 的 `bindtap="goBack"` → `bindtap="onBack"`,否则
 * 顶部标题栏返回按钮会在真实小程序里点击无反应(WeChat 不报错,静默无动作)。
 * 复用 `pages-sub/user/address/__tests__/address-list-wxml-contract.test.js`
 * 已验证过的通用扫描模式:抓 wxml 里所有 bindtap/catchtap 目标,断言目标名
 * 必须是 order-confirm.js 里真实存在的方法,防止这类 wxml↔JS 死绑定回归。
 */
const fs = require('fs');
const path = require('path');

const WXML_PATH = path.resolve(__dirname, '../order-confirm.wxml');

// 复用 order-confirm.test.js 同款最小 mock,拿到真实 pageConfig 用于契约核对。
global.wx = {
  showToast: jest.fn(),
  showLoading: jest.fn(),
  hideLoading: jest.fn(),
  navigateTo: jest.fn(),
  navigateBack: jest.fn(),
  redirectTo: jest.fn(),
};

const mockApp = { globalData: { userInfo: { id: 'user-1', nickname: 'Test' } } };
global.getApp = jest.fn(() => mockApp);
global.getCurrentPages = jest.fn(() => [{}]);

jest.mock('../../../../utils/request.js', () => ({
  request: jest.fn().mockResolvedValue([]),
  authRequest: jest.fn(),
}));

jest.mock('../../../../src/features/order/store', () => ({
  orderStore: {
    loadById: jest.fn(),
    placeOrder: jest.fn(),
    placeDirectBuyOrder: jest.fn(),
  },
}));

jest.mock('../../../../src/features/cart/store', () => ({
  cartStore: { refresh: jest.fn().mockResolvedValue({ items: [] }) },
}));

jest.mock('../../../../src/features/product/api', () => ({
  ProductAPI: { getById: jest.fn() },
}));

jest.mock('../../../../src/modules/payment/payment.js', () => ({
  paymentModule: { requestPayment: jest.fn() },
}));

let pageConfig;
global.Page = (config) => {
  pageConfig = config;
};
require('../order-confirm.js');

describe('order-confirm.wxml ↔ order-confirm.js bindtap 契约（防死绑定回归）', () => {
  let wxml;

  beforeAll(() => {
    wxml = fs.readFileSync(WXML_PATH, 'utf8');
  });

  it('wxml 里每个 bindtap/catchtap 目标都必须是 order-confirm.js 里真实存在的方法', () => {
    const matches = [...wxml.matchAll(/(?:bind|catch)tap="([^"]+)"/g)].map((m) => m[1]);
    expect(matches.length).toBeGreaterThan(0);
    for (const name of matches) {
      expect(typeof pageConfig[name]).toBe('function');
    }
  });

  it('顶部标题栏返回按钮 bindtap="onBack"（design.md D1:goBack → onBack）', () => {
    expect(wxml).toMatch(/bindtap="onBack"/);
    expect(wxml).not.toMatch(/bindtap="goBack"/);
  });
});
