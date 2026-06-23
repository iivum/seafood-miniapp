import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, '../../pages-sub/order/order-list/order-list.wxml');

describe('mp-08 订单列表 hover-class 覆盖（S-2）', () => {
  let wxml: string;
  beforeAll(() => { wxml = fs.readFileSync(WXML, 'utf8'); });

  it('tab 标签有 hover-class="is-clicked"', () => {
    const matches = wxml.match(/bindtap="onTabTap"[^/]*/g) ?? [];
    expect(matches.length).toBeGreaterThan(0);
    for (const el of matches) {
      expect(el).toMatch(/hover-class="is-clicked"/);
      expect(el).toMatch(/hover-stay-time="100"/);
    }
  });

  it('订单卡片有 hover-class="is-clicked"', () => {
    const matches = wxml.match(/bindtap="onOrderTap"[^/]*/g) ?? [];
    expect(matches.length).toBeGreaterThan(0);
    for (const el of matches) {
      expect(el).toMatch(/hover-class="is-clicked"/);
      expect(el).toMatch(/hover-stay-time="100"/);
    }
  });
});
