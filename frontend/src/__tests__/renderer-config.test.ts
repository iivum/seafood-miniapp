/**
 * P0-2 渲染层降级防御测试。
 *
 * 根因:Skyline 不支持 OD/vant 重度依赖的 CSS
 *  (object-fit:cover / -webkit-line-clamp / :active / vant :after /
 *  @font-face unicode-range),导致商品图变形、loading 破损、字体子集失效。
 *
 * 决策:渲染层降级 WebView,删 componentFramework=glass-easel + rendererOptions.skyline
 *  + 各 page.json 的 renderer=skyline。
 *
 * 此测试锁住"全部 Skyline 字段已删",防止被加回。
 * 现状:app.json 含 componentFramework + rendererOptions.skyline;若干 page.json 含 renderer=skyline
 *  改完后:全部断言通过
 */
import * as fs from 'fs';
import * as path from 'path';

function readJson(rel: string): any {
  return JSON.parse(
    fs.readFileSync(
      path.join(__dirname, '../..', rel),
      'utf-8'
    )
  );
}

describe('渲染层 — WebView 降级(Skyline 全删)', () => {
  it('app.json 不含 rendererOptions', () => {
    const cfg = readJson('app.json');
    expect(cfg.rendererOptions).toBeUndefined();
  });

  it('app.json 不含 componentFramework=glass-easel', () => {
    const cfg = readJson('app.json');
    expect(cfg.componentFramework).toBeUndefined();
  });

  it('app.json 不含顶层 renderer(WeChat 不支持)', () => {
    const cfg = readJson('app.json');
    expect(cfg.renderer).toBeUndefined();
  });

  it.each(
    fs.readdirSync(path.join(__dirname, '../../pages'), { recursive: true })
      .filter((p) => p.toString().endsWith('.json'))
      .map((p) => `pages/${p}`)
  )('%s 不含 renderer=skyline', (rel) => {
    const cfg = readJson(rel);
    expect(cfg.renderer).not.toBe('skyline');
  });
});
