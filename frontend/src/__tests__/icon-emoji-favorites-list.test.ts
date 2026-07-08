import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, '../../pages-sub/user/favorites/favorites-list.wxml');
const JSON_PATH = path.resolve(__dirname, '../../pages-sub/user/favorites/favorites-list.json');

describe('我的收藏 UI 微图标 emoji → van-icon(mp-icon-emoji-replacement)', () => {
  let wxml: string;

  beforeAll(() => {
    wxml = fs.readFileSync(WXML, 'utf8');
  });

  it('不再包含裸返回箭头/实心爱心/空心爱心字符', () => {
    expect(wxml).not.toMatch(/‹|♥|🤍/);
  });

  it('顶部返回按钮用 van-icon name="arrow-left"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="favorites-topbar__back-icon"\s+name="arrow-left"/);
  });

  it('取消收藏按钮用 van-icon name="like"(实心,区分空态用的描边 like-o)', () => {
    expect(wxml).toMatch(/<van-icon\s+name="like"\s+size="18px"\s*\/>/);
  });

  it('空收藏列表图标用 van-icon name="like-o"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="empty-state__icon"\s+name="like-o"/);
  });

  it('favorites-list.json 注册了 van-icon 组件', () => {
    const parsed = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
    expect(parsed.usingComponents['van-icon']).toBe('@vant/weapp/icon/index');
  });
});
