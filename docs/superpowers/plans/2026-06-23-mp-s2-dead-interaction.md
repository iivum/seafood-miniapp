# mp S-2 死交互修复实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为小程序 7 个页面的所有可点击元素补加 hover-class="is-clicked" 视觉反馈

**Architecture:** 纯 WXML 属性变更，app.wxss 已有 .is-clicked 定义，无需改样式

**Tech Stack:** 微信小程序 WXML/WXSS, Jest 快照测试

## Global Constraints
- hover-stay-time 固定 100ms
- scroll-view 内子元素不支持 hover-class，需外层 view 包裹
- 不改 app.wxss，不改 JS 逻辑

---

## 参考设计文档

`docs/superpowers/specs/2026-06-23-mp-s2-dead-interaction-design.md`

**需修改的 5 个文件（共 10 处）**：

| 文件 | 缺少 hover-class 的元素 |
|------|------------------------|
| `frontend/pages/index/index.wxml` | swiper-item (L19)、home-chip (L32-41) |
| `frontend/pages/category/category.wxml` | cat-sidebar__item (L10-19) |
| `frontend/pages-sub/product/product-detail/product-detail.wxml` | detail-recommend__item (L63-74) |
| `frontend/pages/cart/cart.wxml` | cart-address navigator (L20)、cart-bar__select-all (L38)、cart-item__check (L54)、cart-footer__checkout (L123) |
| `frontend/pages-sub/order/order-list/order-list.wxml` | order-list__tab (L24-34)、order-card (L62-69) |

**已完全覆盖、无需修改**：`order-confirm.wxml`、`address-list.wxml`、`address-edit.wxml`

---

## Task 1：mp-01 首页（index.wxml）

**文件**：`frontend/pages/index/index.wxml`
**目标元素**：
1. `<swiper-item>` 上的 `bindtap="onBannerTap"`（L19）
2. `<view class="home-chip">` 上的 `bindtap="onCategoryTap"`（L32-41，scroll-view 内直接加）

- [ ] **Step 1**：新建测试文件 `frontend/src/__tests__/hover-class-index.test.ts`，加以下断言：

```typescript
import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, '../../../pages/index/index.wxml');

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
```

- [ ] **Step 2**：在 `frontend/` 目录下跑测试，确认 RED：
  ```bash
  cd /Users/linbinghui/agent-work/seafood-miniapp/frontend
  npx jest src/__tests__/hover-class-index.test.ts --no-coverage
  ```

- [ ] **Step 3**：修改 `frontend/pages/index/index.wxml`：
  - L19 `<swiper-item>` 加 `hover-class="is-clicked" hover-stay-time="100"`
  - L32-41 `<view class="home-chip">` 加 `hover-class="is-clicked" hover-stay-time="100"`

- [ ] **Step 4**：再次跑测试，确认 GREEN：
  ```bash
  cd /Users/linbinghui/agent-work/seafood-miniapp/frontend
  npx jest src/__tests__/hover-class-index.test.ts --no-coverage
  ```

- [ ] **Step 5**：commit：
  ```bash
  git add frontend/pages/index/index.wxml frontend/src/__tests__/hover-class-index.test.ts
  git commit -m "feat(mp): S-2 首页 banner/chip 加 hover-class is-clicked"
  ```

---

## Task 2：mp-02 分类页（category.wxml）

**文件**：`frontend/pages/category/category.wxml`
**目标元素**：
1. `<view class="cat-sidebar__item">` 上的 `bindtap="onCategoryTap"`（L10-19，scroll-view scroll-y 内直接加）

- [ ] **Step 1**：新建测试文件 `frontend/src/__tests__/hover-class-category.test.ts`，加以下断言：

```typescript
import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, '../../../pages/category/category.wxml');

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
```

- [ ] **Step 2**：确认 RED：
  ```bash
  cd /Users/linbinghui/agent-work/seafood-miniapp/frontend
  npx jest src/__tests__/hover-class-category.test.ts --no-coverage
  ```

- [ ] **Step 3**：修改 `frontend/pages/category/category.wxml`：
  - L10-19 `<view class="cat-sidebar__item ...">` 加 `hover-class="is-clicked" hover-stay-time="100"`

- [ ] **Step 4**：确认 GREEN：
  ```bash
  cd /Users/linbinghui/agent-work/seafood-miniapp/frontend
  npx jest src/__tests__/hover-class-category.test.ts --no-coverage
  ```

- [ ] **Step 5**：commit：
  ```bash
  git add frontend/pages/category/category.wxml frontend/src/__tests__/hover-class-category.test.ts
  git commit -m "feat(mp): S-2 分类页 sidebar 分类项加 hover-class is-clicked"
  ```

---

## Task 3：mp-03 商品详情（product-detail.wxml）

**文件**：`frontend/pages-sub/product/product-detail/product-detail.wxml`
**目标元素**：
1. `<view class="detail-recommend__item">` 上的 `bindtap="goToProductDetail"`（L63-74，scroll-view scroll-x 内直接加）

- [ ] **Step 1**：新建测试文件 `frontend/src/__tests__/hover-class-product-detail.test.ts`，加以下断言：

```typescript
import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, '../../../pages-sub/product/product-detail/product-detail.wxml');

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
```

- [ ] **Step 2**：确认 RED：
  ```bash
  cd /Users/linbinghui/agent-work/seafood-miniapp/frontend
  npx jest src/__tests__/hover-class-product-detail.test.ts --no-coverage
  ```

- [ ] **Step 3**：修改 `frontend/pages-sub/product/product-detail/product-detail.wxml`：
  - L63-74 `<view class="detail-recommend__item">` 加 `hover-class="is-clicked" hover-stay-time="100"`

- [ ] **Step 4**：确认 GREEN：
  ```bash
  cd /Users/linbinghui/agent-work/seafood-miniapp/frontend
  npx jest src/__tests__/hover-class-product-detail.test.ts --no-coverage
  ```

- [ ] **Step 5**：commit：
  ```bash
  git add frontend/pages-sub/product/product-detail/product-detail.wxml frontend/src/__tests__/hover-class-product-detail.test.ts
  git commit -m "feat(mp): S-2 商品详情推荐商品项加 hover-class is-clicked"
  ```

---

## Task 4：mp-04 购物车（cart.wxml）

**文件**：`frontend/pages/cart/cart.wxml`
**目标元素**：
1. `<navigator class="cart-address">` 上的 `bindtap="selectAddress"`（L20）
2. `<view class="cart-bar__select-all">` 上的 `bindtap="onSelectAllTap"`（L38）
3. `<view class="cart-item__check">` 上的 `catchtap="onItemCheckTap"`（L54）
4. `<view class="cart-footer__checkout">` 上的 `bindtap="onCheckout"`（L123）

- [ ] **Step 1**：新建测试文件 `frontend/src/__tests__/hover-class-cart.test.ts`，加以下断言：

```typescript
import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, '../../../pages/cart/cart.wxml');

describe('mp-04 购物车 hover-class 覆盖（S-2）', () => {
  let wxml: string;
  beforeAll(() => { wxml = fs.readFileSync(WXML, 'utf8'); });

  it('收货地址 navigator 有 hover-class="is-clicked"', () => {
    const matches = wxml.match(/bindtap="selectAddress"[^/]*/g) ?? [];
    expect(matches.length).toBeGreaterThan(0);
    for (const el of matches) {
      expect(el).toMatch(/hover-class="is-clicked"/);
      expect(el).toMatch(/hover-stay-time="100"/);
    }
  });

  it('全选按钮有 hover-class="is-clicked"', () => {
    const matches = wxml.match(/bindtap="onSelectAllTap"[^/]*/g) ?? [];
    expect(matches.length).toBeGreaterThan(0);
    for (const el of matches) {
      expect(el).toMatch(/hover-class="is-clicked"/);
      expect(el).toMatch(/hover-stay-time="100"/);
    }
  });

  it('商品 checkbox 有 hover-class="is-clicked"', () => {
    const matches = wxml.match(/catchtap="onItemCheckTap"[^/]*/g) ?? [];
    expect(matches.length).toBeGreaterThan(0);
    for (const el of matches) {
      expect(el).toMatch(/hover-class="is-clicked"/);
      expect(el).toMatch(/hover-stay-time="100"/);
    }
  });

  it('去结算按钮有 hover-class="is-clicked"', () => {
    const matches = wxml.match(/bindtap="onCheckout"[^/]*/g) ?? [];
    expect(matches.length).toBeGreaterThan(0);
    for (const el of matches) {
      expect(el).toMatch(/hover-class="is-clicked"/);
      expect(el).toMatch(/hover-stay-time="100"/);
    }
  });
});
```

- [ ] **Step 2**：确认 RED：
  ```bash
  cd /Users/linbinghui/agent-work/seafood-miniapp/frontend
  npx jest src/__tests__/hover-class-cart.test.ts --no-coverage
  ```

- [ ] **Step 3**：修改 `frontend/pages/cart/cart.wxml`：
  - L20 `<navigator class="cart-address">` 加 `hover-class="is-clicked" hover-stay-time="100"`
  - L38 `<view class="cart-bar__select-all">` 加 `hover-class="is-clicked" hover-stay-time="100"`
  - L54 `<view class="cart-item__check">` 加 `hover-class="is-clicked" hover-stay-time="100"`
  - L123 `<view class="cart-footer__checkout">` 加 `hover-class="is-clicked" hover-stay-time="100"`

- [ ] **Step 4**：确认 GREEN：
  ```bash
  cd /Users/linbinghui/agent-work/seafood-miniapp/frontend
  npx jest src/__tests__/hover-class-cart.test.ts --no-coverage
  ```

- [ ] **Step 5**：commit：
  ```bash
  git add frontend/pages/cart/cart.wxml frontend/src/__tests__/hover-class-cart.test.ts
  git commit -m "feat(mp): S-2 购物车地址/全选/checkbox/结算加 hover-class is-clicked"
  ```

---

## Task 5：mp-08 订单列表（order-list.wxml）

**文件**：`frontend/pages-sub/order/order-list/order-list.wxml`
**目标元素**：
1. `<view class="order-list__tab">` 上的 `bindtap="onTabTap"`（L24-34，scroll-view scroll-x 内直接加）
2. `<view class="order-card">` 上的 `bindtap="onOrderTap"`（L62-69）

- [ ] **Step 1**：新建测试文件 `frontend/src/__tests__/hover-class-order-list.test.ts`，加以下断言：

```typescript
import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, '../../../pages-sub/order/order-list/order-list.wxml');

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
```

- [ ] **Step 2**：确认 RED：
  ```bash
  cd /Users/linbinghui/agent-work/seafood-miniapp/frontend
  npx jest src/__tests__/hover-class-order-list.test.ts --no-coverage
  ```

- [ ] **Step 3**：修改 `frontend/pages-sub/order/order-list/order-list.wxml`：
  - L24-34 `<view class="order-list__tab ...">` 加 `hover-class="is-clicked" hover-stay-time="100"`
  - L62-69 `<view class="order-card">` 加 `hover-class="is-clicked" hover-stay-time="100"`

- [ ] **Step 4**：确认 GREEN：
  ```bash
  cd /Users/linbinghui/agent-work/seafood-miniapp/frontend
  npx jest src/__tests__/hover-class-order-list.test.ts --no-coverage
  ```

- [ ] **Step 5**：commit：
  ```bash
  git add frontend/pages-sub/order/order-list/order-list.wxml frontend/src/__tests__/hover-class-order-list.test.ts
  git commit -m "feat(mp): S-2 订单列表 tab/卡片加 hover-class is-clicked"
  ```

---

## Task 6：全量快照测试 + 无回归验证

- [ ] **Step 1**：新建全量合约快照 `frontend/src/__tests__/hover-class-contract.test.ts`，锁定 5 个 wxml 文件修改后状态：

```typescript
import * as fs from 'fs';
import * as path from 'path';

const FRONTEND = path.resolve(__dirname, '../../..');

const FILES = [
  'pages/index/index.wxml',
  'pages/category/category.wxml',
  'pages-sub/product/product-detail/product-detail.wxml',
  'pages/cart/cart.wxml',
  'pages-sub/order/order-list/order-list.wxml',
] as const;

function normalize(s: string): string {
  return s.replace(/\r\n/g, '\n').replace(/[ \t]+\n/g, '\n').trim();
}

describe('S-2 hover-class 全量合约快照（防止后续误删）', () => {
  for (const rel of FILES) {
    it(`${rel} 快照`, () => {
      const content = normalize(fs.readFileSync(path.join(FRONTEND, rel), 'utf8'));
      expect(content).toMatchSnapshot();
    });
  }
});
```

- [ ] **Step 2**：首次跑生成快照：
  ```bash
  cd /Users/linbinghui/agent-work/seafood-miniapp/frontend
  npx jest src/__tests__/hover-class-contract.test.ts --no-coverage -u
  ```

- [ ] **Step 3**：跑全量前端测试确认无回归：
  ```bash
  cd /Users/linbinghui/agent-work/seafood-miniapp/frontend
  npm test -- --no-coverage
  ```

- [ ] **Step 4**：commit：
  ```bash
  git add frontend/src/__tests__/hover-class-contract.test.ts \
          frontend/src/__tests__/__snapshots__/hover-class-contract.test.ts.snap
  git commit -m "test(mp): S-2 hover-class 全量合约快照"
  ```

---

## 完成标准

- [ ] 5 个 wxml 文件共 10 处均加上 `hover-class="is-clicked" hover-stay-time="100"`
- [ ] 5 个页面级测试全部 GREEN
- [ ] `hover-class-contract.test.ts` 快照锁定通过
- [ ] `npm test` 全量通过，无新增失败
- [ ] 所有提交已 push 到远端分支
