import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, '../../pages-sub/user/address/address-list.wxml');
const JSON_PATH = path.resolve(__dirname, '../../pages-sub/user/address/address-list.json');

describe('mp-07 地址管理 UI 微图标 emoji → van-icon(mp-icon-emoji-replacement)', () => {
  let wxml: string;

  beforeAll(() => {
    wxml = fs.readFileSync(WXML, 'utf8');
  });

  it('不再包含裸返回箭头/编辑/删除/勾选/邮筒字符', () => {
    expect(wxml).not.toMatch(/‹|✏️|🗑️|✓|📭/);
  });

  it('顶部返回按钮用 van-icon name="arrow-left"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="addr-topbar__back-icon"\s+name="arrow-left"/);
  });

  it('编辑按钮用 van-icon name="edit"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="address-card__action-icon"\s+name="edit"/);
  });

  it('删除按钮用 van-icon name="delete-o"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="address-card__action-icon"\s+name="delete-o"/);
  });

  it('选择模式 radio 勾选态用 van-icon name="success"', () => {
    // 绑定源是 selectedAddress.id(selectedId 是从未定义过的死绑定,2026-07-10 修复)
    expect(wxml).toMatch(/<van-icon\s+wx:if="\{\{selectedAddress\.id === item\.id\}\}"\s+class="address-card__radio-check"\s+name="success"/);
  });

  it('空地址列表图标用 van-icon name="location-o"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="empty-state__icon"\s+name="location-o"/);
  });

  it('address-list.json 注册了 van-icon 组件', () => {
    const parsed = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
    expect(parsed.usingComponents['van-icon']).toBe('@vant/weapp/icon/index');
  });
});
