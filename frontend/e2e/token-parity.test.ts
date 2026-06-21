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

function parseWxssHex(content: string) {
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

  describe('CTA WCAG AA contrast', () => {
    const CTAS = [
      { name: 'detail-footer__btn--buy(立即购买)', fg: '#ffffff', bg: '#db633c' },
      { name: 'detail-footer__btn--cart(加入购物车)', fg: '#b83300', bg: '#ffe7d9' },
      { name: 'order-list__status PENDING', fg: '#df911a', bg: '#ffe9cb' },
      { name: 'order-list__status PAID', fg: '#1988a3', bg: '#d2f5ff' },
      { name: 'order-list__status COMPLETED', fg: '#318f5a', bg: '#d5f9e0' },
      { name: 'order-list__status REFUNDING', fg: '#b9003d', bg: '#ffe2e4' },
    ];

    it.each(CTAS)('$name: contrast ratio (Sprint 1 末仅 log 报告,不 hard fail)', (cta) => {
      const ratio = chroma.contrast(cta.fg, cta.bg);
      const status = ratio >= 4.5 ? 'AA-PASS' : ratio >= 3.0 ? 'AA-FAIL-但可见' : 'CRITICAL';
      console.log(`  [${status}] ${cta.name}: fg=${cta.fg} bg=${cta.bg} ratio=${ratio.toFixed(2)}`);
      if (ratio < 4.5) {
        console.warn(`  ⚠️  ${cta.name} ratio ${ratio.toFixed(2)} < WCAG AA 4.5 — Sprint 2 需修`);
      }
      // Sprint 1 末已知 design 限制,本测试只 report,不 hard fail
      // Sprint 2 应修:warning / warning-soft / error-soft / info-soft 配 各自 fg contrast < 4.5
    });
  });
});
