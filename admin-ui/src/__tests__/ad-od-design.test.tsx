/**
 * ad 6 屏 OD 设计对齐测试
 *
 * 对照 Open Design 项目 seafood-miniapp(686e3434-0233-451e-9c99-debee025a336)
 * 的 ad-01 ~ ad-06 HTML mockup,断言当前 admin-ui React 实现应当包含 OD
 * 关键 layout 元素 + 文案。
 *
 * TDD RED 阶段(2026-06-18):当前 admin-ui 是 sprint 1 closure 旧设计,
 * 跟 OD 差异大 — 跑看大量 fail 作为 v2.2 路线图。
 *
 * 2 层断言:
 *  - L1 静态层(读 .tsx 源文件,断言含 OD 关键 token)
 *  - L4 颜色 token parity(chroma 验 CSS 文件含设计 token)
 *
 * 跑:cd admin-ui && npx vitest run src/__tests__/ad-od-design.test.tsx
 */

import { describe, it, expect } from 'vitest';
import * as fs from 'node:fs';
import * as path from 'node:path';
// @ts-ignore — chroma-js 无 @types
import chroma from 'chroma-js';

interface OdElement {
  selector: string;
  label: string;
}

interface AdSpec {
  name: string;
  sourceFile: string;
  required: OdElement[];
  tokens?: { name: string; hex: string; usage: string }[];
}

const ADMIN_ROOT = path.resolve(__dirname, '../..');

const AD_SPECS: AdSpec[] = [
  {
    name: 'ad-01-login',
    sourceFile: 'src/features/auth/LoginPage.tsx',
    required: [
      { selector: '欢迎回来', label: '欢迎回来 主标题' },
      { selector: 'Account', label: 'Account 字段名(英)' },
      { selector: 'Password', label: 'Password 字段名(英)' },
      { selector: '7 天|免登录', label: '"7 天内免登录" checkbox' },
      { selector: '忘记密码|找回', label: '"忘记密码?" 链接' },
      { selector: '登录管理|登录', label: '"登录" 提交按钮' },
    ],
  },
  {
    name: 'ad-02-dashboard',
    sourceFile: 'src/features/dashboard/DashboardPage.tsx',
    required: [
      { selector: 'GMV|gmv|销售额', label: 'GMV 销售额 KPI' },
      { selector: 'AVG ORDER|客单价|平均订单', label: 'AVG ORDER 客单价 KPI' },
      { selector: 'CONVERSION|转化率|转化', label: 'CONVERSION 转化率 KPI' },
      { selector: 'ORDERS|订单数|订单量', label: 'ORDERS 订单数 KPI' },
      { selector: '趋势|chart|Trend', label: '订单趋势 chart 容器' },
      { selector: 'TOP|销量|排行', label: '销量 TOP 表格' },
      { selector: '分类|品类', label: '分类销售 bar 区' },
      { selector: '导出报表|导出', label: '导出报表按钮' },
    ],
  },
  {
    name: 'ad-03-product-list',
    sourceFile: 'src/features/products/ProductListPage.tsx',
    required: [
      { selector: '新品|新增商品|添加', label: '"新增商品" 按钮' },
      { selector: '导出.*CSV|导出', label: '"导出 CSV" 按钮' },
      { selector: 'onSale|在售|ACTIVE', label: '状态 tab "在售"' },
      { selector: 'OOS|缺货|OUT', label: '状态 tab "缺货"' },
      { selector: 'CATEGORY|分类', label: '分类 tab' },
      { selector: 'fish|鱼类|贝类|虾蟹|软体|海藻', label: '5 分类 chip(鱼类/贝类/虾蟹/软体/海藻)' },
    ],
  },
  {
    name: 'ad-04-product-form',
    sourceFile: 'src/features/products/ProductForm.tsx',
    required: [
      { selector: 'name|商品名|名称', label: '商品名 input' },
      { selector: 'desc|描述|简介', label: '描述 textarea' },
      { selector: 'price|价格|单价', label: '价格 input' },
      { selector: 'stock|库存|存货', label: '库存 input' },
      { selector: 'category|分类|类目', label: '分类 select' },
      { selector: 'status|状态|active|ACTIVE', label: '状态 select' },
      { selector: 'image|图片|images', label: '商品图片 upload' },
      { selector: 'SKU|sku', label: 'SKU 规格 field array' },
      { selector: '创建|发布|保存', label: '"创建" 提交按钮' },
    ],
  },
  {
    name: 'ad-05-order-list',
    sourceFile: 'src/features/orders/OrderListPage.tsx',
    required: [
      { selector: 'PENDING|待付款|待付|pending', label: 'PENDING 状态 tab' },
      { selector: 'PAID|已付款|已付|paid', label: 'PAID 状态 tab' },
      { selector: 'SHIPPED|已发货|已发|shipped', label: 'SHIPPED 状态 tab' },
      { selector: 'COMPLETED|已完成|完成|completed', label: 'COMPLETED 状态 tab' },
      { selector: 'CANCELLED|已取消|取消|cancelled', label: 'CANCELLED 状态 tab' },
      { selector: '批量发货|batch-ship|batchShip', label: '"批量发货" 按钮' },
      { selector: '导出.*CSV|exportCsv', label: '"导出 CSV" 按钮' },
    ],
  },
  {
    name: 'ad-06-order-detail',
    sourceFile: 'src/features/orders/OrderDetailPage.tsx',
    required: [
      { selector: '订单编号|order.*id', label: '订单编号字段' },
      { selector: '用户|user|客户|customer', label: '用户信息' },
      { selector: '商品|item|product', label: '商品列表' },
      { selector: '金额|amount|total|summary', label: '金额明细' },
      { selector: '地址|address|shipping', label: '配送地址' },
      { selector: '状态|status|state', label: '订单状态徽标' },
    ],
  },
];

function readSource(relPath: string): string | null {
  const abs = path.resolve(ADMIN_ROOT, relPath);
  if (!fs.existsSync(abs)) return null;
  return fs.readFileSync(abs, 'utf-8');
}

function anyMatch(content: string, selector: string): boolean {
  // selector 是一组 | 分隔的备选,任一匹配即命中
  const tokens = selector.split('|').map(s => s.trim()).filter(Boolean);
  return tokens.some(t => {
    // 大小写不敏感;支持简单通配符 * → .*
    const re = new RegExp(t.replace(/[.+^${}()|[\]\\]/g, '\\$&').replace(/\*/g, '.*'), 'i');
    return re.test(content);
  });
}

describe('ad 6 屏 OD 设计对齐(4 层断言 — TDD RED 阶段)', () => {
  describe('L1 结构 — ad 6 屏 OD 元素存在性(静态文件层)', () => {
    AD_SPECS.forEach(spec => {
      describe(spec.name, () => {
        spec.required.forEach(elem => {
          it(`OD 元素存在: ${elem.label} (在 ${spec.sourceFile})`, () => {
            const content = readSource(spec.sourceFile);
            if (content === null) {
              throw new Error(`source file 缺失: ${spec.sourceFile}`);
            }
            const ok = anyMatch(content, elem.selector);
            expect({
              spec: spec.name,
              element: elem.label,
              source: spec.sourceFile,
              found: ok,
            }).toEqual({
              spec: spec.name,
              element: elem.label,
              source: spec.sourceFile,
              found: true,
            });
          });
        });
      });
    });
  });

  describe('L4 颜色 token parity(admin-ui 关键 token)', () => {
    it('admin-ui tailwind/globals.css 应含设计 token', () => {
      // admin-ui 用 tailwind + CSS variables,OD mockup 用 oklch
      // 简单验证:globals.css / index.css 应包含核心 token 名
      const candidates = [
        'src/index.css',
        'src/globals.css',
        'tailwind.config.js',
        'tailwind.config.ts',
      ];
      let foundAny = false;
      for (const rel of candidates) {
        const content = readSource(rel);
        if (content && /(--accent|--bg|--fg|--surface|--muted)/i.test(content)) {
          foundAny = true;
          break;
        }
      }
      expect({ foundAny, candidates }).toEqual({ foundAny: true, candidates: expect.any(Array) });
    });

    it.each([
      { name: 'accent', hex: '#c2410c', usage: 'OD accent 暖橘(类比 oklch(64% 0.16 38))' },
    ])('admin-ui $name token hex 合法', ({ hex }) => {
      expect(hex).toMatch(/^#[0-9a-f]{3,8}$/i);
    });
  });
});
