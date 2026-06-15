/**
 * OD v2 视觉契约快照测试
 *
 * 锁定 7 个组件(ProductCard/ProductList/CartItemRow/OrderItemRow + Button/Empty/Loading)
 * 的 WXML 结构 + WXSS v2 token 引用。任何改 v2 视觉契约的提交都会让此测试失败,
 * 提示作者确认是否有意变更 + 同步 OpenSpec change。
 *
 * 配合 scripts/build-tokens.js + scripts/__tests__/build-tokens.test.js
 * 一起作为 design-token parity 的「视觉层」守门员。
 */
import * as fs from 'fs';
import * as path from 'path';

const SRC = path.resolve(__dirname, '..');

const ROWS = [
  'shared/components/Button',
  'shared/components/Empty',
  'shared/components/Loading',
  'features/product/components/ProductCard',
  'features/product/components/ProductList',
  'features/cart/components/CartItemRow',
  'features/order/components/OrderItemRow',
] as const;

function read(rel: string, ext: 'wxml' | 'wxss'): string {
  return fs.readFileSync(path.join(SRC, rel, `index.${ext}`), 'utf8');
}

/** 规范化空白,避免纯缩进变更导致快照噪声。 */
function normalize(s: string): string {
  return s.replace(/\r\n/g, '\n').replace(/[ \t]+\n/g, '\n').trim();
}

describe('OD v2 视觉契约(7 个组件)', () => {
  for (const rel of ROWS) {
    describe(rel, () => {
      const wxml = normalize(read(rel, 'wxml'));
      const wxss = normalize(read(rel, 'wxss'));

      it('WXML 结构快照', () => {
        expect(wxml).toMatchSnapshot(`wxml:${rel}`);
      });

      it('WXSS 视觉快照', () => {
        expect(wxss).toMatchSnapshot(`wxss:${rel}`);
      });
    });
  }

  describe('v2 token 引用契约(避免 v1 嵌套 token 复活)', () => {
    /** v1 嵌套 / hex 写法必须不再出现于 v2 视觉文件;否则视为视觉回退。 */
    const v1AntiPatterns: Array<[name: string, pattern: RegExp]> = [
      ['v1 navy hex', /#1e3a5f/i],
      ['v1 coral hex', /#FF6B6B/i],
      ['v1 teal hex', /#4ECDC4/i],
      ['v1 navy --color-primary', /var\(--color-primary/],
      ['v1 --color-text', /var\(--color-text\b/],
      ['v1 --color-bg-subtle', /var\(--color-bg-subtle/],
      ['v1 --color-price', /var\(--color-price/],
      ['v1 --color-text-secondary', /var\(--color-text-secondary/],
    ];

    for (const rel of ROWS) {
      it(`${rel} 不再使用 v1 颜色 token`, () => {
        const wxss = read(rel, 'wxss');
        for (const [name, pattern] of v1AntiPatterns) {
          expect({ file: rel, antiPattern: name, hit: pattern.test(wxss) }).toEqual({
            file: rel,
            antiPattern: name,
            hit: false,
          });
        }
      });
    }
  });

  describe('v2 token 引用契约(必须使用)', () => {
    /** 各组件必须使用的 v2 token 子集,至少命中 1 个。 */
    const requiredByComponent: Record<string, RegExp[]> = {
      'shared/components/Button': [/--accent/, /--radius-pill/, /--shadow-md|--accent[^"]*\/.*0\.08/],
      'shared/components/Empty': [/--font-display/, /--accent-soft|--accent-strong/],
      'shared/components/Loading': [/--accent/, /--font-display/],
      'features/product/components/ProductCard': [/--accent/, /--font-display/, /--radius-xl|--shadow/],
      'features/cart/components/CartItemRow': [/--accent/, /--font-display/, /--font-mono|--radius-md/],
      'features/order/components/OrderItemRow': [/--font-display/, /--warning-soft|--state/],
      'features/product/components/ProductList': [/--bg/],
    };

    for (const [rel, patterns] of Object.entries(requiredByComponent)) {
      it(`${rel} 至少使用预期 v2 token 子集`, () => {
        const wxss = read(rel, 'wxss');
        for (const p of patterns) {
          expect(p.test(wxss)).toBe(true);
        }
      });
    }
  });
});
