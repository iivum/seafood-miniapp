import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, '../../pages/cart/cart.wxml');
const JSON_PATH = path.resolve(__dirname, '../../pages/cart/cart.json');

describe('mp-04 购物车 UI 微图标 emoji → van-icon(mp-icon-emoji-replacement)', () => {
  let wxml: string;

  beforeAll(() => {
    wxml = fs.readFileSync(WXML, 'utf8');
  });

  it('不再包含裸 emoji/勾选字符(购物车/定位/勾选)', () => {
    expect(wxml).not.toMatch(/🛒|📍|✓/);
  });

  it('空购物车 shared-empty 用 icon="cart-o" prop(本地组件真正支持,不再需要 vant 的 image=""/slot 绕过写法)', () => {
    const blocks = wxml.match(/<shared-empty[\s\S]*?<\/shared-empty>/g) ?? [];
    expect(blocks.length).toBe(1);
    expect(blocks[0]).not.toMatch(/image=""/);
    expect(blocks[0]).not.toMatch(/slot="image"/);
    expect(blocks[0]).toMatch(/icon="cart-o"/);
  });

  it('cart.json 的 shared-empty 指向本地组件', () => {
    const parsed = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
    expect(parsed.usingComponents['shared-empty']).toBe('/src/shared/components/Empty/index');
  });

  it('收货地址占位行用 van-icon name="location-o" + 独立文本节点(不再是 emoji 拼在一个 text 里)', () => {
    expect(wxml).toMatch(/<van-icon\s+name="location-o"[^/]*\/>\s*<text>请选择收货地址<\/text>/);
  });

  it('全选 checkbox 和单品 checkbox 的勾选态都用 van-icon name="success"', () => {
    const matches = [...wxml.matchAll(/<van-icon\s+wx:if="\{\{[^}]+\}\}"\s+class="cart-checkbox__icon"\s+name="success"/g)];
    expect(matches.length).toBe(2);
  });

  it('cart.json 注册了 van-icon 组件', () => {
    const parsed = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
    expect(parsed.usingComponents['van-icon']).toBe('@vant/weapp/icon/index');
  });
});
