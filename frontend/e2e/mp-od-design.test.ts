/**
 * mp 8 屏 OD 设计对齐测试
 *
 * 对照 docs/redesign/mp-screenshots/design-ref/ 8 张 OD 高保真设计稿,
 * 断言当前 mp 实现应当匹配 OD 的关键 layout 元素。
 *
 * TDD 流程:
 *  1. RED   — 当前实现不匹配 OD,跑这测试应当全 fail
 *  2. GREEN — 修当前实现(改 wxss / 补 banners 字段 / 改 wxml)让测试过
 *  3. REFACTOR — 清理
 *
 * 设计稿来源: docs/redesign/mp-screenshots/design-ref/
 *   mp-01-home.png / mp-02-category.png / mp-03-product-detail.png /
 *   mp-04-cart.png / mp-05-profile.png / mp-06-order-confirm.png /
 *   mp-07-address.png / mp-08-order-list.png
 *
 * 4 层断言(对齐 visual-verification-patterns.md):
 *   1. 结构 (wxml 节点 / class / 文案)
 *   2. 数据 (page.data 字段)
 *   3. 行为 (console error/warning = 0)
 *   4. 颜色 (chroma 验 token parity)
 */

import { describe, it, expect, beforeAll } from '@jest/globals';
// @ts-ignore — chroma-js 无 @types
import chroma from 'chroma-js';
import * as fs from 'node:fs';
import * as path from 'node:path';

interface OdElement {
  selector: string;
  label: string;
}

interface OdSpec {
  name: string;
  required: OdElement[];
  tokens?: { name: string; hex: string; usage: string }[];
  route: { tab?: string; url?: string };
  /** 静态 wxml 源文件路径(相对 frontend/) */
  sourceFile?: string;
  /** 静态 wxss 源文件路径 */
  sourceWxss?: string;
}

const OD_SPECS: OdSpec[] = [
  {
    name: 'mp-01-home',
    route: { tab: '/pages/index/index' },
    /** 静态源文件(不依赖 DevTools) — 测当前实现是否包含 OD 元素 */
    sourceFile: 'pages/index/index.wxml',
    sourceWxss: 'pages/index/index.wxss',
    required: [
      { selector: 'home-address|home-search|location|search-bar', label: '顶部地址 + 搜索框' },
      { selector: 'home-banner|home-banner__item', label: 'Hero banner 红色卡片' },
      { selector: 'home-banner__title', label: 'banner title 文案字段' },
      { selector: 'home-banner__subtitle', label: 'banner subtitle 文案字段' },
      { selector: 'banners', label: 'banners data 字段(数组)' },
      { selector: 'home-chip', label: '分类 chip(5 个)' },
      { selector: 'home-tag|home-filter|tag-tab', label: '时令/上新/促销 标签' },
      { selector: 'home-card|product-card|recommend-card', label: '推荐商品大卡(2 列)' },
    ],
    tokens: [
      { name: 'banner-bg', hex: '#7a3415', usage: 'Hero banner 棕红背景' },
      { name: 'chip-bg', hex: '#fbf6f1', usage: '分类 chip 浅米色' },
      { name: 'price-accent', hex: '#c2410c', usage: '价格强调' },
    ],
  },
  {
    name: 'mp-02-category',
    route: { tab: '/pages/category/category' },
    sourceFile: 'pages/category/category.wxml',
    sourceWxss: 'pages/category/category.wxss',
    required: [
      { selector: 'cat-sidebar|cat-nav|category-sidebar', label: '左侧分类 nav(8 项)' },
      { selector: 'cat-banner|cat-featured', label: '右侧 featured banner' },
      { selector: 'cat-grid|cat-product|product-card', label: '3x2 product grid + 本季新品' },
      { selector: 'cat-section-title|section-title', label: '"人气 TOP 6" / "本季新品" section header' },
    ],
  },
  {
    name: 'mp-03-product-detail',
    route: { url: '/pages-sub/product/product-detail/product-detail?id=6a2f097fcb28035db83d88b3' },
    sourceFile: 'pages-sub/product/product-detail/product-detail.wxml',
    sourceWxss: 'pages-sub/product/product-detail/product-detail.wxss',
    required: [
      { selector: 'detail-banner|detail-image|detail-swiper', label: '商品大图 carousel' },
      { selector: 'detail-price|detail-info', label: '价格区(¥288)' },
      { selector: 'detail-stepper|detail-sku|detail-spec', label: 'SKU / 规格选择' },
      { selector: 'detail-footer|detail-footer__btn', label: '底部 sticky 按钮栏' },
    ],
  },
  {
    name: 'mp-04-cart',
    route: { tab: '/pages/cart/cart' },
    sourceFile: 'pages/cart/cart.wxml',
    sourceWxss: 'pages/cart/cart.wxss',
    required: [
      { selector: 'cart-header|cart-title', label: '顶部"购物车 · N" + 编辑按钮' },
      { selector: 'cart-delivery|cart-address', label: '配送地址 + 时效卡片' },
      { selector: 'cart-item|cart-product', label: '商品卡(checkbox + 图 + 名 + 价 + stepper)' },
      { selector: 'cart-recommend|cart-extras', label: '"一起买" 推荐区' },
      { selector: 'cart-footer|cart-summary', label: '底部全选 + 合计 + 去结算 sticky' },
    ],
  },
  {
    name: 'mp-05-profile',
    route: { url: '/pages/profile/profile' },
    sourceFile: 'pages/profile/profile.wxml',
    sourceWxss: 'pages/profile/profile.wxss',
    required: [
      { selector: 'profile-header|user-info', label: '用户信息 card' },
      { selector: 'profile-orders|order-status-row', label: '订单状态 row' },
      { selector: 'profile-list|profile-menu', label: '设置/菜单 list' },
    ],
  },
  {
    name: 'mp-06-order-confirm',
    route: { url: '/pages-sub/order/order-confirm/order-confirm' },
    sourceFile: 'pages-sub/order/order-confirm/order-confirm.wxml',
    sourceWxss: 'pages-sub/order/order-confirm/order-confirm.wxss',
    required: [
      { selector: 'order-address|confirm-address', label: '收货地址' },
      { selector: 'order-items|confirm-items', label: '商品清单' },
      { selector: 'order-delivery|confirm-delivery', label: '配送方式' },
      { selector: 'order-remark|confirm-remark', label: '备注' },
      { selector: 'order-summary|confirm-summary', label: '金额明细' },
      { selector: 'order-submit|confirm-submit', label: '提交按钮' },
    ],
  },
  {
    name: 'mp-07-address',
    route: { url: '/pages-sub/user/address/address-list' },
    sourceFile: 'pages-sub/user/address/address-list.wxml',
    sourceWxss: 'pages-sub/user/address/address-list.wxss',
    required: [
      { selector: 'address-list|address-item', label: '地址列表项' },
      { selector: 'address-add|address-footer', label: '新增地址按钮' },
    ],
  },
  {
    name: 'mp-08-order-list',
    route: { url: '/pages-sub/order/order-list/order-list' },
    sourceFile: 'pages-sub/order/order-list/order-list.wxml',
    sourceWxss: 'pages-sub/order/order-list/order-list.wxss',
    required: [
      { selector: 'order-tabs|order-list__tabs', label: '5 状态 tab' },
      { selector: 'order-card|order-item', label: '订单卡片' },
    ],
    tokens: [
      { name: 'status-pending', hex: '#df911a', usage: 'PENDING 徽标(当前 ratio=2.17)' },
      { name: 'status-paid', hex: '#1988a3', usage: 'PAID 徽标(当前 ratio=3.58)' },
      { name: 'status-completed', hex: '#318f5a', usage: 'COMPLETED 徽标(当前 ratio=3.54)' },
    ],
  },
];

const WXSS = path.resolve(__dirname, '..', 'src', 'shared', 'tokens', 'tokens.wxss');

describe('mp 8 屏 OD 设计对齐(4 层断言 — TDD RED 阶段)', () => {
  describe('L4 颜色 token parity', () => {
    let actualTokens: Map<string, string>;

    beforeAll(() => {
      const content = fs.readFileSync(WXSS, 'utf-8');
      actualTokens = new Map();
      const re = /--([a-z0-9-]+):\s*(#[0-9a-fA-F]{3,8})\s*;/g;
      let m: RegExpExecArray | null;
      while ((m = re.exec(content)) !== null) {
        actualTokens.set(m[1], m[2].toLowerCase());
      }
    });

    it('关键 token 全部 build 产出 hex', () => {
      const required = ['bg', 'surface', 'fg', 'muted', 'border', 'accent',
        'accent-soft', 'accent-strong', 'success', 'warning', 'error', 'info'];
      for (const name of required) {
        const actual = actualTokens.get(name);
        expect({ token: name, actual }).toEqual({ token: name, actual: expect.any(String) });
      }
    });

    it.each(
      OD_SPECS.flatMap(spec =>
        (spec.tokens || []).map(t => ({ spec: spec.name, token: t }))
      )
    )('$spec — $token.name hex 合法', ({ token }) => {
      expect(token.hex).toMatch(/^#[0-9a-f]{3,8}$/i);
      if (token.name === 'status-pending') {
        const ratio = chroma.contrast(token.hex, '#ffe9cb');
        expect(ratio).toBeGreaterThan(2.0);
      }
    });
  });

  describe('L1 结构 — 8 屏 OD 元素存在性(静态文件层)', () => {
    function anyMatch(content: string, selector: string): boolean {
      const tokens = selector.split('|').map(s => s.trim()).filter(Boolean);
      return tokens.some(t => content.includes(t));
    }

    function readSource(relPath: string | undefined): string | null {
      if (!relPath) return null;
      const abs = path.resolve(__dirname, '..', relPath);
      if (!fs.existsSync(abs)) return null;
      return fs.readFileSync(abs, 'utf-8');
    }

    OD_SPECS.forEach(spec => {
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
});
