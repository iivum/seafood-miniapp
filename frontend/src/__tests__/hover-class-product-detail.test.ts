import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, '../../pages-sub/product/product-detail/product-detail.wxml');

describe('mp-03 商品详情 hover-class 覆盖（S-2）', () => {
  let wxml: string;
  beforeAll(() => { wxml = fs.readFileSync(WXML, 'utf8'); });

  it('推荐商品项有 hover-class="is-clicked"', () => {
    const matches = wxml.match(/bindtap="goToProductDetail"[^/]*/g) ?? [];
    expect(matches.length).toBeGreaterThan(0);
    for (const el of matches) {
      expect(el).toMatch(/hover-class="is-clicked"/);
      expect(el).toMatch(/hover-stay-time="100"/);
    }
  });
});
