import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, '../../pages-sub/order/order-confirm/order-confirm.wxml');
const JSON_PATH = path.resolve(__dirname, '../../pages-sub/order/order-confirm/order-confirm.json');

describe('mp-06 订单确认 UI 微图标 emoji → van-icon(mp-icon-emoji-replacement)', () => {
  let wxml: string;

  beforeAll(() => {
    wxml = fs.readFileSync(WXML, 'utf8');
  });

  it('不再包含裸返回箭头/定位/勾选字符', () => {
    expect(wxml).not.toMatch(/‹|📍|✓/);
  });

  it('顶部返回按钮用 van-icon name="arrow-left"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="confirm-topbar__back-icon"\s+name="arrow-left"/);
  });

  it('收货地址占位行用 van-icon name="location-o" + 独立文本节点', () => {
    expect(wxml).toMatch(/<van-icon\s+name="location-o"[^/]*\/>\s*<text>请选择收货地址<\/text>/);
  });

  it('三个配送方式的选中态勾选都用 van-icon name="success"', () => {
    const matches = [...wxml.matchAll(/<van-icon\s+wx:if="\{\{shippingMethod === '[A-Z]+'\}\}"\s+class="delivery-option__check"\s+name="success"/g)];
    expect(matches.length).toBe(3);
  });

  it('order-confirm.json 注册了 van-icon 组件', () => {
    const parsed = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
    expect(parsed.usingComponents['van-icon']).toBe('@vant/weapp/icon/index');
  });
});
