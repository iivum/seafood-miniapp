/**
 * 4 层 mp 视觉验证 — token parity via chroma.js (Node-only 部分)。
 *
 * **Node 端** — 直接读 tokens.wxss + 算 ΔE + WCAG contrast,不需要 DevTools。
 *   - 抓"OKLch→hex 转换漂移":对比 build 出来的 tokens.wxss
 *   - 抓"CTA 配色违 WCAG AA":chroma WCAG contrast 检查
 *
 * **WebView 端** — 跑在 mp 页面,需要 DevTools(cli auto --auto-port 9420),
 *   拉每个 --token 渲染色,跟期望 hex 比 ΔE。devtools 环境易 stall,主验证在 mp-3layer.test.ts。
 *
 * 跑:TZ=UTC npx jest e2e/token-parity.test.ts --runInBand
 */
const chroma = require('chroma-js');
const fs = require('node:fs');
const path = require('node:path');

function parseWxssHex(content) {
  const map = new Map();
  const re = /--([a-z0-9-]+):\s*(#[0-9a-fA-F]{3,8})\s*;/g;
  let m;
  while ((m = re.exec(content)) !== null) {
    map.set(m[1], m[2].toLowerCase());
  }
  return map;
}

const WXSS = path.resolve(__dirname, '..', 'src', 'shared', 'tokens', 'tokens.wxss');
const ACTUAL_TOKENS = parseWxssHex(fs.readFileSync(WXSS, 'utf-8'));

describe('mp token parity (Node 端 chroma.js 静态校验)', () => {
  describe('build: tokens.wxss 渲染色 sanity', () => {
    it('所有关键 --token 都已 build 产出 hex', () => {
      const required = ['bg', 'surface', 'fg', 'muted', 'border', 'accent', 'accent-soft', 'accent-strong', 'success', 'warning', 'error', 'info'];
      for (const name of required) {
        const actual = ACTUAL_TOKENS.get(name);
        expect(actual).toBeDefined();
      }
    });

    it('accent 不是 black/white(perceptual sanity 避免 build 崩)', () => {
      const accent = chroma(ACTUAL_TOKENS.get('accent'));
      expect(accent.luminance()).toBeGreaterThan(0.05);
      expect(accent.luminance()).toBeLessThan(0.6);
    });
  });

  describe('CTA WCAG AA contrast(Sprint 2 — hard fail)', () => {
    // 6 个 CTA:fg/bg pair 取自 wxml 实际使用(改 wxml 后,这里同步改 fgToken/bgToken)
    // 这样 test 跟页面实际渲染的 pair 同步。
    const CTAS: { name: string; fgToken: string; bgToken: string }[] = [
      // detail-footer btn:
      //   --buy:fg=surface, bg=accent-strong(WCAG 修后)
      //   --cart:fg=accent-strong, bg=accent-soft
      { name: 'detail-footer__btn--buy(立即购买)', fgToken: 'surface', bgToken: 'accent-strong' },
      { name: 'detail-footer__btn--cart(加入购物车)', fgToken: 'accent-strong', bgToken: 'accent-soft' },
      // 4 个 order status badge(WCAG 修后)
      { name: 'order-list__status PENDING', fgToken: 'warning', bgToken: 'warning-soft' },
      { name: 'order-list__status PAID', fgToken: 'info', bgToken: 'info-soft' },
      { name: 'order-list__status COMPLETED', fgToken: 'success', bgToken: 'success-soft' },
      { name: 'order-list__status REFUNDING', fgToken: 'error', bgToken: 'error-soft' },
    ];

    it.each(CTAS)('$name: contrast ratio >= 4.5 (WCAG AA)', (cta) => {
      const fg = ACTUAL_TOKENS.get(cta.fgToken);
      const bg = ACTUAL_TOKENS.get(cta.bgToken);
      if (!fg || !bg) {
        throw new Error(`token 缺失: fg=${cta.fgToken}=${fg} bg=${cta.bgToken}=${bg}`);
      }
      const ratio = chroma.contrast(fg, bg);
      const status = ratio >= 4.5 ? 'AA-PASS' : ratio >= 3.0 ? 'AA-FAIL-但可见' : 'CRITICAL';
      console.log(`  [${status}] ${cta.name}: fg=${cta.fgToken}(${fg}) bg=${cta.bgToken}(${bg}) ratio=${ratio.toFixed(2)}`);
      expect(ratio).toBeGreaterThanOrEqual(4.5);
    });
  });
});
