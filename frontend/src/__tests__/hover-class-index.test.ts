import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, '../../pages/index/index.wxml');

describe('mp-01 首页 hover-class 覆盖（S-2）', () => {
  let wxml: string;
  beforeAll(() => { wxml = fs.readFileSync(WXML, 'utf8'); });

  it('banner swiper-item 有 hover-class="is-clicked"', () => {
    const matches = wxml.match(/bindtap="onBannerTap"[^/]*/g) ?? [];
    expect(matches.length).toBeGreaterThan(0);
    for (const el of matches) {
      expect(el).toMatch(/hover-class="is-clicked"/);
      expect(el).toMatch(/hover-stay-time="100"/);
    }
  });

  it('分类 chip 有 hover-class="is-clicked"', () => {
    const matches = wxml.match(/bindtap="onCategoryTap"[^/]*/g) ?? [];
    expect(matches.length).toBeGreaterThan(0);
    for (const el of matches) {
      expect(el).toMatch(/hover-class="is-clicked"/);
      expect(el).toMatch(/hover-stay-time="100"/);
    }
  });
});
