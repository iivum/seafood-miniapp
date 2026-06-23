import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, '../../pages/cart/cart.wxml');

describe('mp-04 购物车 hover-class 覆盖（S-2）', () => {
  let wxml: string;
  beforeAll(() => { wxml = fs.readFileSync(WXML, 'utf8'); });

  it('收货地址 navigator 有 hover-class="is-clicked"', () => {
    const matches = wxml.match(/bindtap="selectAddress"[^/]*/g) ?? [];
    expect(matches.length).toBeGreaterThan(0);
    for (const el of matches) {
      expect(el).toMatch(/hover-class="is-clicked"/);
      expect(el).toMatch(/hover-stay-time="100"/);
    }
  });

  it('全选按钮有 hover-class="is-clicked"', () => {
    const matches = wxml.match(/bindtap="onSelectAllTap"[^/]*/g) ?? [];
    expect(matches.length).toBeGreaterThan(0);
    for (const el of matches) {
      expect(el).toMatch(/hover-class="is-clicked"/);
      expect(el).toMatch(/hover-stay-time="100"/);
    }
  });

  it('商品 checkbox 有 hover-class="is-clicked"', () => {
    const matches = wxml.match(/catchtap="onItemCheckTap"[^/]*/g) ?? [];
    expect(matches.length).toBeGreaterThan(0);
    for (const el of matches) {
      expect(el).toMatch(/hover-class="is-clicked"/);
      expect(el).toMatch(/hover-stay-time="100"/);
    }
  });

  it('去结算按钮有 hover-class="is-clicked"', () => {
    const matches = wxml.match(/bindtap="onCheckout"[^/]*/g) ?? [];
    expect(matches.length).toBeGreaterThan(0);
    for (const el of matches) {
      expect(el).toMatch(/hover-class="is-clicked"/);
      expect(el).toMatch(/hover-stay-time="100"/);
    }
  });
});
