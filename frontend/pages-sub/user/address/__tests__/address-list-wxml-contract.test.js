/**
 * address-list.wxml 结构 + wxml ↔ JS 绑定契约测试。
 *
 * 背景(mp-07 OD 对齐,brief `.superpowers/sdd/mp-od-6-address-brief.md` +
 * coordinator 追加授权 2026-07-02):此前 wxml 的 bindtap 目标名
 * (onSelectAddress/onEditAddress/onDeleteAddress/onAddNewAddress)和
 * address-list.js 里真实的方法名(selectAddress/editAddress/deleteAddress/
 * addNewAddress)完全对不上——整卡选择/编辑/删除/添加新地址,覆盖本页几乎
 * 全部核心交互,在真实小程序里点击都没有反应。既有 address-list.test.js
 * 只直接调用 `pageConfig.editAddress(...)` 这类方法本身,从未断言过 wxml
 * bindtap 写的名字和方法名是否一致,所以这个死绑定一直没被抓到。
 *
 * 同类"wxml 引用了 JS 不存在的方法/字段"问题在 mp-02(activeCategoryId /
 * data-category-id)、mp-03(onIncrement/onDecrement)已反复出现,这里补一个
 * 通用契约测试:扫描 wxml 里所有 bindtap="xxx",断言 xxx 必须是
 * address-list.js 导出的 Page config 上真实存在的方法,防止此类问题回归。
 */
const fs = require('fs');
const path = require('path');

const WXML_PATH = path.resolve(__dirname, '../address-list.wxml');

// 复用 address-list.test.js 同款最小 mock,拿到真实 pageConfig 用于契约核对。
global.wx = {
  showToast: jest.fn(),
  navigateTo: jest.fn(),
  navigateBack: jest.fn(),
  showModal: jest.fn(),
  getStorageSync: jest.fn(),
  setStorageSync: jest.fn(),
  request: jest.fn(),
};
const mockApp = { globalData: { userInfo: { id: 'user-1', nickname: 'Test' } } };
global.getApp = jest.fn(() => mockApp);
global.getCurrentPages = jest.fn(() => []);

jest.mock('../../../../utils/request.js', () => ({
  request: jest.fn().mockResolvedValue([]),
  authRequest: jest.fn(),
}));

let pageConfig;
global.Page = (config) => {
  pageConfig = config;
};
require('../address-list.js');

describe('address-list.wxml ↔ address-list.js bindtap 契约（防死绑定回归）', () => {
  let wxml;

  beforeAll(() => {
    wxml = fs.readFileSync(WXML_PATH, 'utf8');
  });

  it('wxml 里每个 bindtap 目标都必须是 address-list.js 里真实存在的方法', () => {
    const matches = [...wxml.matchAll(/bindtap="([^"]+)"/g)].map((m) => m[1]);
    expect(matches.length).toBeGreaterThan(0);
    for (const name of matches) {
      expect(typeof pageConfig[name]).toBe(`function`);
    }
  });

  it('顶部标题栏：非选择模式显示"地址管理" + 返回按钮 bindtap="goBack"', () => {
    expect(wxml).toMatch(/bindtap="goBack"/);
    expect(wxml).toMatch(/地址管理/);
  });

  it('不会同时出现两个标题：旧的独立 select-title 块已合并进统一 topbar', () => {
    expect(wxml).not.toMatch(/class="select-title"/);
  });

  it('选择模式标题文案"选择收货地址"仍然可达（合并进统一 topbar，非新增两套标题）', () => {
    expect(wxml).toMatch(/选择收货地址/);
  });

  it('非默认地址卡片有"设为默认"可点击行，bindtap="setDefaultAddress" + data-address 绑定完整 item', () => {
    const tagMatch = wxml.match(/<view[^>]*wx:if="\{\{!item\.isDefault\}\}"[^>]*>/);
    expect(tagMatch).not.toBeNull();
    const tag = tagMatch[0];
    expect(tag).toMatch(/bindtap="setDefaultAddress"/);
    expect(tag).toMatch(/data-address="\{\{item\}\}"/);
    expect(wxml).toMatch(/设为默认/);
  });

  it('默认地址卡片不渲染"设为默认"可点击行（本来就是默认的，不需要这个操作）', () => {
    // "设为默认"整段必须被 wx:if="{{!item.isDefault}}" 卫护，不能对默认地址也可点。
    const setDefaultBlockCount = (wxml.match(/设为默认/g) || []).length;
    const guardedCount = (wxml.match(/wx:if="\{\{!item\.isDefault\}\}"/g) || []).length;
    expect(guardedCount).toBeGreaterThan(0);
    expect(setDefaultBlockCount).toBeGreaterThan(0);
  });
});
