import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, '../../pages/category/category.wxml');

describe('mp-02 分类页 hover-class 覆盖（S-2）', () => {
  let wxml: string;
  beforeAll(() => { wxml = fs.readFileSync(WXML, 'utf8'); });

  it('左侧 sidebar 分类项有 hover-class="is-clicked"', () => {
    const matches = wxml.match(/class="cat-sidebar__item[^"]*"[^/]*bindtap="onCategoryTap"[^/]*/g)
      ?? wxml.match(/bindtap="onCategoryTap"[^/]*/g)
      ?? [];
    expect(matches.length).toBeGreaterThan(0);
    for (const el of matches) {
      expect(el).toMatch(/hover-class="is-clicked"/);
      expect(el).toMatch(/hover-stay-time="100"/);
    }
  });
});
