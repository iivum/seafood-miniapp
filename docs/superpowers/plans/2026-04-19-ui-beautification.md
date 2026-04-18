# 海鲜商城 UI 美化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为微信小程序和 Admin-UI 实施"活力现代"设计系统，统一配色为珊瑚橙 `#FF6B6B` + 青碧 `#4ECDC4` + 暖黄 `#FFE66D`

**Architecture:** 改动分为 3 个 Phase：(1) 设计系统落地，(2) 小程序全页面美化，(3) Admin-UI 全视图美化。所有样式改动通过 CSS 变量（小程序）和内联 Vaadin Style（Admin）实现，不引入新依赖。

**Tech Stack:** 微信小程序 WXSS + Vaadin Flow (Java)

---

## Phase 1: 设计系统落地

### Task 1: 小程序全局 CSS 变量 — `app.wxss`

**Files:**
- Modify: `frontend/app.wxss:1-123`

- [ ] **Step 1: 更新 page {} 中的 CSS 变量块**

替换现有的 `/* Ocean Market Palette */` 变量块（行 14-26）为新色彩系统，保留 safe-area 变量和 fixed-bar-height 变量：

```wxss
  /* Vibrant Modern Palette */
  --color-primary: #FF6B6B;
  --color-secondary: #4ECDC4;
  --color-accent: #FFE66D;
  --color-dark: #1A1A2E;
  --color-surface: #ffffff;
  --color-bg: #F7F8FC;
  --color-bg-subtle: #F0F1F5;
  --color-text: #2D3436;
  --color-text-secondary: #95A5A6;
  --color-text-muted: #B4B8BC;
  --color-border: #E8EAED;
  --color-divider: #F0F1F5;
  --color-price: #FF6B6B;
```

- [ ] **Step 2: 更新全局辅助类**

替换 `.primary-color`、`.bg-primary`、`.accent-color`、`.bg-accent`、`.success-color`、`.price-color` 的色值为新变量引用（已引用正确，但确认 `--color-accent` 暖黄用于 `.accent-color`）。

替换 `.btn` 背景为渐变：
```wxss
.btn {
  background: linear-gradient(135deg, #FF6B6B 0%, #FF8E8E 100%);
  color: #fff;
  border-radius: 8rpx;
  padding: 20rpx;
  text-align: center;
}
```

替换 `.btn-accent`（当前是重复的 `.btn` 样式，保持一致但改 `background` 为 `#4ECDC4`）：
```wxss
.btn-accent {
  background: linear-gradient(135deg, #4ECDC4 0%, #6FE3DB 100%);
  color: #fff;
  border-radius: 8rpx;
  padding: 20rpx;
  text-align: center;
}
```

替换 `.card` 的阴影和圆角：
```wxss
.card {
  background-color: var(--color-surface);
  border-radius: 16rpx;
  margin: 20rpx;
  padding: 20rpx;
  box-shadow: 0 4rpx 20rpx rgba(255, 107, 107, 0.12);
  border: 1rpx solid var(--color-border);
}
```

- [ ] **Step 3: 提交**
```bash
cd /Users/iivum/.openclaw/workspace/seafood-miniapp
git add frontend/app.wxss && git commit -m "feat(frontend): apply new color system in app.wxss

- Update CSS variables to vibrant modern palette
- Primary #FF6B6B, Secondary #4ECDC4, Accent #FFE66D
- Add gradient to .btn, update card shadows

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Phase 2: 小程序页面美化

### Task 2: 首页样式 — `pages/index/index.wxss`

**Files:**
- Modify: `frontend/pages/index/index.wxss:1-238`

- [ ] **Step 1: 更新搜索栏 `.search-input` 样式**

替换当前 `.search-input` 块（行 18-25）：
```wxss
.search-input {
  flex: 1;
  display: flex;
  align-items: center;
  background-color: var(--color-bg-subtle);
  border-radius: 30rpx;
  padding: 10rpx 20rpx;
  box-shadow: inset 0 2rpx 8rpx rgba(0, 0, 0, 0.06);
}
```

在 `.search-bar` 的底部加 focus 效果（行 15 后添加）：
```wxss
.search-bar {
  /* ...existing styles... */
  /* 新增 focus 内阴影效果（通过 focus 态 class 控制） */
}
```

在 `.search-input` 后添加 focus 态：
```wxss
.search-input:focus-within {
  box-shadow: inset 0 2rpx 8rpx rgba(0, 0, 0, 0.06), 0 0 0 2rpx var(--color-primary);
}
```

- [ ] **Step 2: 更新 Banner 轮播样式**

替换 `.banner` 块（行 96-102）：
```wxss
.banner {
  height: 300rpx;
  margin: 20rpx;
  border-radius: 24rpx;
  overflow: hidden;
  box-shadow: 0 8rpx 30rpx rgba(255, 107, 107, 0.2);
}
```

替换 `.banner-placeholder` 背景渐变（行 104-112）：
```wxss
.banner-placeholder {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #FF6B6B 0%, #4ECDC4 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
```

- [ ] **Step 3: 更新分类入口 `.nav-item`**

替换 `.category-nav` 块（行 133-139）：
```wxss
.category-nav {
  display: flex;
  justify-content: space-around;
  padding: 30rpx 0;
  background-color: var(--color-surface);
  margin-bottom: 20rpx;
  border-radius: 16rpx;
  margin: 0 20rpx 20rpx;
}
```

替换 `.nav-item` 块（行 141-158）中的 image 样式（行 147-153）：
```wxss
.nav-item image {
  width: 80rpx;
  height: 80rpx;
  margin-bottom: 10rpx;
  border-radius: 50%;
  background-color: var(--color-bg);
  box-shadow: 0 4rpx 12rpx rgba(255, 107, 107, 0.15);
  transition: transform 0.3s ease-out, box-shadow 0.3s ease-out;
}

.nav-item:active image {
  transform: scale(0.92);
  box-shadow: 0 2rpx 8rpx rgba(255, 107, 107, 0.1);
}
```

- [ ] **Step 4: 更新商品卡片阴影和按钮样式**

替换 `.product-info` 块（行 194-200）：
```wxss
.product-info {
  background-color: var(--color-surface);
  padding: 16rpx;
  border-radius: 0 0 16rpx 16rpx;
  border: 1rpx solid var(--color-border);
  border-top: none;
  box-shadow: 0 4rpx 16rpx rgba(255, 107, 107, 0.1);
}
```

替换 `.add-cart-btn`（行 228-237）：
```wxss
/* Add to Cart Button - Vibrant Gradient Coral */
.add-cart-btn {
  background: linear-gradient(135deg, #FF6B6B 0%, #FF8E8E 100%);
  color: #fff;
  font-size: 24rpx;
  font-weight: 600;
  padding: 8rpx 24rpx;
  border-radius: 20rpx;
  margin-top: 10rpx;
  text-align: center;
  box-shadow: 0 4rpx 12rpx rgba(255, 107, 107, 0.3);
  transition: transform 0.3s ease-out, box-shadow 0.3s ease-out, opacity 0.3s ease-out;
}

.add-cart-btn:active {
  transform: scale(0.96);
  opacity: 0.85;
  box-shadow: 0 2rpx 6rpx rgba(255, 107, 107, 0.2);
}
```

在文件末尾添加商品卡片 press 反馈：
```wxss
.product-item:active {
  opacity: 0.9;
}
```

- [ ] **Step 5: 提交**
```bash
cd /Users/iivum/.openclaw/workspace/seafood-miniapp
git add frontend/pages/index/index.wxss && git commit -m "feat(frontend): beautify home page styles

- Banner: gradient background + larger radius + enhanced shadow
- Category nav: rounded container + icon shadow + press feedback
- Product card: enhanced shadows + gradient add-to-cart button
- Search input: focus ring with primary color

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 3: 分类页样式 — `pages/category/category.wxss`

**Files:**
- Modify: `frontend/pages/category/category.wxss`（新建或修改）
- Check: `frontend/pages/category/category.wxml` 结构确认选择器

- [ ] **Step 1: 读取现有 `category.wxss` 内容**

```bash
cat /Users/iivum/.openclaw/workspace/seafood-miniapp/frontend/pages/category/category.wxss
```

- [ ] **Step 2: 更新样式**

如果文件存在，用新色彩和圆角替换；如果为空或不存在，写入：
```wxss
/* Category Page - Vibrant Modern */
.category-container {
  background-color: var(--color-bg);
  min-height: 100vh;
  padding-bottom: 50rpx;
}

/* Category Grid */
.category-grid {
  padding: 20rpx;
}

.category-grid-inner {
  display: flex;
  flex-wrap: wrap;
  margin: -10rpx;
}

.category-item {
  width: calc(50% - 20rpx);
  margin: 10rpx;
  background-color: var(--color-surface);
  border-radius: 16rpx;
  padding: 30rpx 20rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 4rpx 20rpx rgba(255, 107, 107, 0.12);
  border: 1rpx solid var(--color-border);
  transition: transform 0.3s ease-out, box-shadow 0.3s ease-out;
}

.category-item:active {
  transform: scale(0.97);
  box-shadow: 0 2rpx 10rpx rgba(255, 107, 107, 0.08);
}

.category-icon-wrapper {
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #FF6B6B 0%, #4ECDC4 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16rpx;
  box-shadow: 0 4rpx 16rpx rgba(255, 107, 107, 0.25);
}

.category-icon {
  font-size: 48rpx;
}

.category-name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 8rpx;
}

.category-desc {
  font-size: 22rpx;
  color: var(--color-text-secondary);
  text-align: center;
}

/* Category Header */
.category-header {
  display: flex;
  align-items: center;
  padding: 20rpx;
  background-color: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.category-header-left {
  display: flex;
  align-items: center;
  color: var(--color-text-secondary);
  font-size: 26rpx;
}

.back-arrow {
  margin-right: 8rpx;
  color: var(--color-primary);
  font-weight: 600;
}

.category-title {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--color-dark);
  margin-left: 16rpx;
}

/* Products View */
.products-view {
  padding-bottom: 120rpx;
}

.product-list {
  display: flex;
  flex-wrap: wrap;
  padding: 10rpx;
  background-color: var(--color-bg);
}

.product-item {
  display: flex;
  align-items: center;
}

.product-card {
  flex: 1;
}

.product-img {
  width: 100%;
  height: 350rpx;
  border-radius: 16rpx 16rpx 0 0;
  background-color: var(--color-bg-subtle);
}

.product-info {
  background-color: var(--color-surface);
  padding: 16rpx;
  border-radius: 0 0 16rpx 16rpx;
  border: 1rpx solid var(--color-border);
  border-top: none;
  box-shadow: 0 4rpx 16rpx rgba(255, 107, 107, 0.1);
}

.product-name {
  font-size: 28rpx;
  color: var(--color-text);
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-price {
  font-size: 30rpx;
  color: var(--color-price);
  font-weight: 700;
  margin-top: 8rpx;
}

/* States */
.loading-state,
.error-state,
.empty-state {
  text-align: center;
  padding: 100rpx 40rpx;
  color: var(--color-text-secondary);
  font-size: 28rpx;
}

.error-text {
  color: var(--color-primary);
  display: block;
  margin-bottom: 20rpx;
}

.retry-btn {
  display: inline-block;
  background: linear-gradient(135deg, #FF6B6B 0%, #FF8E8E 100%);
  color: #fff;
  padding: 16rpx 40rpx;
  border-radius: 30rpx;
  font-size: 26rpx;
  margin-top: 20rpx;
}

.loading-more,
.no-more {
  text-align: center;
  padding: 30rpx;
  color: var(--color-text-muted);
  font-size: 24rpx;
}
```

- [ ] **Step 3: 提交**
```bash
git add frontend/pages/category/category.wxss && git commit -m "feat(frontend): beautify category page styles

- Category cards: gradient icon wrapper + rounded shadows + press feedback
- Product cards: unified with home page card styles
- Consistent color palette with vibrant modern system

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 4: 购物车页样式 — `pages/cart/cart.wxss`

**Files:**
- Modify: `frontend/pages/cart/cart.wxss`（新建或修改）

- [ ] **Step 1: 读取现有 `cart.wxss`**

```bash
cat /Users/iivum/.openclaw/workspace/seafood-miniapp/frontend/pages/cart/cart.wxss
```

- [ ] **Step 2: 更新样式**

```wxss
/* Cart Page - Vibrant Modern */
.cart-container {
  background-color: var(--color-bg);
  min-height: 100vh;
  padding-bottom: 140rpx;
}

.cart-empty {
  text-align: center;
  padding: 160rpx 40rpx;
}

.cart-empty icon {
  color: var(--color-text-muted);
  margin-bottom: 30rpx;
}

.cart-empty text {
  display: block;
  font-size: 28rpx;
  color: var(--color-text-secondary);
  margin-bottom: 40rpx;
}

/* Address Section */
.address-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-radius: 16rpx;
  margin: 20rpx;
  padding: 24rpx;
  box-shadow: 0 4rpx 20rpx rgba(255, 107, 107, 0.12);
}

.address-info {
  flex: 1;
}

.address-header {
  display: flex;
  align-items: center;
  margin-bottom: 10rpx;
}

.address-header .name {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text);
  margin-right: 16rpx;
}

.address-header .phone {
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.address-text {
  font-size: 26rpx;
  color: var(--color-text-secondary);
  line-height: 1.4;
}

.address-empty {
  display: flex;
  align-items: center;
  color: var(--color-text-secondary);
  font-size: 28rpx;
}

.address-empty icon {
  margin-right: 10rpx;
  color: var(--color-primary);
}

.arrow-icon {
  color: var(--color-text-muted);
}

/* Select All Bar */
.select-all-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx;
  background-color: var(--color-surface);
  margin: 0 20rpx;
  border-radius: 12rpx;
}

.select-all {
  display: flex;
  align-items: center;
  font-size: 28rpx;
  color: var(--color-text);
}

.selected-info {
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

/* Cart Item */
.cart-list {
  padding: 0 20rpx;
}

.cart-item {
  display: flex;
  align-items: center;
  padding: 20rpx;
  margin-bottom: 16rpx;
  background-color: var(--color-surface);
  border-radius: 16rpx;
  box-shadow: 0 4rpx 16rpx rgba(255, 107, 107, 0.1);
  border: 1rpx solid var(--color-border);
}

.item-checkbox {
  margin-right: 16rpx;
}

.item-img {
  width: 160rpx;
  height: 160rpx;
  border-radius: 12rpx;
  margin-right: 16rpx;
  background-color: var(--color-bg-subtle);
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-name {
  font-size: 28rpx;
  color: var(--color-text);
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 8rpx;
}

.item-price {
  font-size: 30rpx;
  color: var(--color-price);
  font-weight: 700;
  display: block;
  margin-bottom: 12rpx;
}

.quantity-control {
  display: flex;
  align-items: center;
}

.quantity-control .minus,
.quantity-control .plus {
  width: 56rpx;
  height: 56rpx;
  line-height: 56rpx;
  text-align: center;
  background-color: var(--color-bg-subtle);
  border-radius: 8rpx;
  color: var(--color-text);
  font-size: 28rpx;
  padding: 0;
}

.quantity-control .num {
  width: 80rpx;
  text-align: center;
  font-size: 28rpx;
  color: var(--color-text);
  margin: 0 8rpx;
  background-color: var(--color-bg-subtle);
  border-radius: 8rpx;
  height: 56rpx;
}

.remove-btn {
  color: var(--color-text-muted);
  font-size: 36rpx;
  padding: 0 16rpx;
  background: none;
}

/* Order Info */
.order-info {
  margin: 20rpx;
  padding: 20rpx;
  background-color: var(--color-surface);
  border-radius: 16rpx;
  box-shadow: 0 4rpx 16rpx rgba(255, 107, 107, 0.1);
}

.info-item {
  display: flex;
  justify-content: space-between;
  padding: 10rpx 0;
  font-size: 28rpx;
}

.info-item .label {
  color: var(--color-text-secondary);
}

.info-item .value {
  color: var(--color-text);
  font-weight: 500;
}

.info-item .free {
  color: var(--color-secondary);
  font-weight: 600;
}

/* Footer */
.footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 30rpx;
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  background-color: var(--color-dark);
  z-index: 100;
}

.total-info {
  display: flex;
  align-items: center;
  color: #fff;
  font-size: 28rpx;
}

.total-price {
  font-size: 36rpx;
  font-weight: 800;
  color: var(--color-accent);
  margin-left: 8rpx;
}

.checkout-btn {
  background: linear-gradient(135deg, #FF6B6B 0%, #FF8E8E 100%);
  color: #fff;
  font-size: 30rpx;
  font-weight: 600;
  padding: 20rpx 50rpx;
  border-radius: 40rpx;
  box-shadow: 0 4rpx 20rpx rgba(255, 107, 107, 0.4);
  border: none;
  transition: transform 0.3s ease-out, box-shadow 0.3s ease-out;
}

.checkout-btn:active {
  transform: scale(0.96);
  box-shadow: 0 2rpx 10rpx rgba(255, 107, 107, 0.3);
}

.checkout-btn[disabled] {
  background: #ccc;
  box-shadow: none;
}
```

- [ ] **Step 3: 提交**
```bash
git add frontend/pages/cart/cart.wxss && git commit -m "feat(frontend): beautify cart page styles

- Dark footer with gradient checkout button
- Card-style cart items with enhanced shadows
- Gradient price and accent color usage

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 5: 个人中心样式 — `pages/profile/profile.wxss`

**Files:**
- Modify: `frontend/pages/profile/profile.wxss`（新建或修改）

- [ ] **Step 1: 读取现有 `profile.wxss`**

```bash
cat /Users/iivum/.openclaw/workspace/seafood-miniapp/frontend/pages/profile/profile.wxss
```

- [ ] **Step 2: 更新样式**

```wxss
/* Profile Page - Vibrant Modern */
.profile-container {
  background-color: var(--color-bg);
  min-height: 100vh;
  padding-bottom: 50rpx;
}

/* User Header */
.user-header {
  display: flex;
  align-items: center;
  padding: 60rpx 40rpx 40rpx;
  background: linear-gradient(135deg, #FF6B6B 0%, #4ECDC4 100%);
  border: none;
  border-radius: 0;
  width: 100%;
  box-shadow: 0 4rpx 20rpx rgba(255, 107, 107, 0.3);
}

.user-header::after {
  border: none;
}

.avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  border: 4rpx solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.15);
  margin-right: 30rpx;
}

.nickname {
  font-size: 36rpx;
  font-weight: 700;
  color: #fff;
  display: block;
  margin-bottom: 10rpx;
}

.role-tag {
  display: inline-block;
  background-color: rgba(255, 255, 255, 0.25);
  color: #fff;
  font-size: 22rpx;
  padding: 4rpx 16rpx;
  border-radius: 20rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.3);
}

/* Menu Card */
.menu-list {
  margin-top: 0 !important;
  border-radius: 0 !important;
  box-shadow: none !important;
  border-left: none !important;
  border-right: none !important;
}

.menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 30rpx 20rpx;
  border-bottom: 1rpx solid var(--color-divider);
  background-color: var(--color-surface);
  transition: background-color 0.2s ease;
}

.menu-item:last-child {
  border-bottom: none;
}

.menu-item:active {
  background-color: var(--color-bg-subtle);
}

.menu-item text:first-child {
  font-size: 30rpx;
  color: var(--color-text);
}

.menu-item .arrow {
  color: var(--color-text-muted);
  font-size: 28rpx;
}

/* Order Nav */
.section-title {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--color-text);
  padding: 30rpx 20rpx 20rpx;
}

.order-nav {
  display: flex;
  justify-content: space-around;
  padding: 20rpx 0;
  background-color: var(--color-surface);
  border-radius: 16rpx;
  margin: 0 20rpx 20rpx;
  box-shadow: 0 4rpx 16rpx rgba(255, 107, 107, 0.1);
}

.nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16rpx 24rpx;
  transition: transform 0.3s ease-out;
}

.nav-item:active {
  transform: scale(0.95);
}

.nav-item icon {
  font-size: 48rpx;
  margin-bottom: 10rpx;
  color: var(--color-primary);
}

.nav-item text {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

/* Merchant Section */
.merchant-nav .btn {
  display: block;
  background: linear-gradient(135deg, #FF6B6B 0%, #FF8E8E 100%);
  color: #fff;
  text-align: center;
  padding: 24rpx;
  border-radius: 16rpx;
  font-size: 30rpx;
  font-weight: 600;
  box-shadow: 0 4rpx 16rpx rgba(255, 107, 107, 0.3);
  margin: 0 20rpx;
}

/* Card overrides */
.profile-container .card {
  background-color: var(--color-surface);
  border-radius: 0;
  margin: 0;
  padding: 0;
  box-shadow: 0 2rpx 12rpx rgba(255, 107, 107, 0.08);
  border: none;
}
```

- [ ] **Step 3: 提交**
```bash
git add frontend/pages/profile/profile.wxss && git commit -m "feat(frontend): beautify profile page styles

- Gradient user header with avatar border
- Order nav icons in primary color
- Gradient merchant entry button

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Phase 3: Admin-UI 美化

### Task 6: 主布局导航样式 — `MainLayout.java`

**Files:**
- Modify: `backend/admin-ui/src/main/java/com/seafood/admin/views/main/MainLayout.java:1-121`

- [ ] **Step 1: 更新头部渐变背景**

替换 `createHeader()` 方法中的 header 样式（行 64-66）：
```java
header.getStyle()
    .set("background", "linear-gradient(135deg, #1A1A2E 0%, #2D3452 100%)")
    .set("min-height", "56px");
```

- [ ] **Step 2: 更新侧边栏导航项样式**

替换 `createNavLink()` 方法（行 103-119）中的样式为：
```java
private <T extends com.vaadin.flow.component.Component> RouterLink createNavLink(Icon icon, String label, Class<T> target) {
    icon.setSize("18px");
    icon.getStyle().set("margin-right", "10px");
    RouterLink link = new RouterLink();
    link.add(icon, new com.vaadin.flow.component.Text(label));
    link.setRoute(target);
    link.getStyle()
        .set("display", "flex")
        .set("align-items", "center")
        .set("padding", "10px 20px")
        .set("border-radius", "8px")
        .set("color", "#2D3436")
        .set("text-decoration", "none")
        .set("font-weight", "500")
        .set("font-size", "0.9rem")
        .set("transition", "all 0.2s ease")
        .set("margin", "2px 8px");
    return link;
}
```

- [ ] **Step 3: 提交**
```bash
git add backend/admin-ui/src/main/java/com/seafood/admin/views/main/MainLayout.java && git commit -m "refactor(admin): update MainLayout navigation styles

- Header: aligned gradient background
- Sidebar nav: consistent spacing and transition
- Lighter text color for inactive items

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 7: 仪表盘视图 — `DashboardView.java`

**Files:**
- Modify: `backend/admin-ui/src/main/java/com/seafood/admin/views/dashboard/DashboardView.java:1-277`

- [ ] **Step 1: 更新页面背景色**

替换 `setSizeFull()` 后的 `getStyle()`（行 35-38）：
```java
getStyle()
    .set("background", "#F7F8FC")
    .set("min-height", "100vh")
    .set("padding", "28px 32px");
```

- [ ] **Step 2: 更新统计卡片顶部边条色**

替换 `createStatCard()` 中的 `border-top` 样式（行 127-128）：
```java
card.getStyle()
    .set("border-top", "4px solid #FF6B6B");
```

- [ ] **Step 3: 更新状态徽章背景色**

替换 `createStatusBadge()` 中的 `background` 和 `border`（行 216-222）：
```java
badge.getStyle()
    .set("background", color + "20")
    .set("border", "1px solid " + color + "50")
    .set("border-radius", "10px")
    .set("padding", "14px 18px")
    .set("min-width", "100px")
    .set("text-align", "center");
```

- [ ] **Step 4: 提交**
```bash
git add backend/admin-ui/src/main/java/com/seafood/admin/views/dashboard/DashboardView.java && git commit -m "feat(admin): beautify dashboard styles

- Page background: light gray (#F7F8FC)
- Stat card top border: coral primary (#FF6B6B)
- Status badge: 20% opacity background, 50% opacity border

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 8: 商品列表视图 — `ProductListView.java`

**Files:**
- Modify: `backend/admin-ui/src/main/java/com/seafood/admin/views/product/ProductListView.java:1-165`

- [ ] **Step 1: 更新"添加商品"按钮为渐变**

替换 `getToolbar()` 中的 `addProductButton`（行 110-112）：
```java
Button addProductButton = new Button("添加商品");
addProductButton.getStyle()
    .set("background", "linear-gradient(135deg, #FF6B6B 0%, #FF8E8E 100%)")
    .set("border-radius", "8px")
    .set("box-shadow", "0 2px 8px rgba(255, 107, 107, 0.3)");
addProductButton.addClickListener(click -> addProduct());
```

移除 `addProductButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);`（如果存在）。

- [ ] **Step 2: 更新搜索框样式**

在 `getToolbar()` 方法中，searchField 设置后添加：
```java
searchField.setPlaceholder("搜索商品...");
searchField.setClearButtonVisible(true);
searchField.setValueChangeMode(ValueChangeMode.LAZY);
searchField.getStyle()
    .set("border-radius", "8px")
    .set("--lumo-border-radius", "8px");
searchField.addValueChangeListener(e -> updateList());
```

- [ ] **Step 3: 提交**
```bash
git add backend/admin-ui/src/main/java/com/seafood/admin/views/product/ProductListView.java && git commit -m "feat(admin): beautify product list styles

- Add button: gradient background with shadow
- Search field: rounded corners styling

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 9: 订单列表视图 — `OrderListView.java`

**Files:**
- Modify: `backend/admin-ui/src/main/java/com/seafood/admin/views/order/OrderListView.java:1-213`

- [ ] **Step 1: 更新页面背景**

在构造函数中替换 `getStyle().set("padding", "28px 32px")` 后面添加背景色：
```java
getStyle()
    .set("padding", "28px 32px")
    .set("background", "#F7F8FC");
```

- [ ] **Step 2: 更新发货按钮为渐变**

替换 `configureGrid()` 中的 `shipButton`（行 138-149）：
```java
grid.addComponentColumn(order -> {
    Button shipButton = new Button("发货");
    shipButton.getStyle()
        .set("background", "linear-gradient(135deg, #FF6B6B 0%, #FF8E8E 100%)")
        .set("border-radius", "8px")
        .set("color", "#ffffff")
        .set("box-shadow", "0 2px 8px rgba(255, 107, 107, 0.3)");
    shipButton.setEnabled("PAID".equals(order.getDisplayStatus()));
    shipButton.addClickListener(click -> {
        try {
            orderClient.updateOrderStatus(order.getId(), "SHIPPED");
            Notification.show("订单 " + order.getId() + " 已发货");
            updateList();
        } catch (Exception e) {
            Notification.show("发货失败: " + e.getMessage());
        }
    });
    return shipButton;
}).setHeader("操作");
```

- [ ] **Step 3: 提交**
```bash
git add backend/admin-ui/src/main/java/com/seafood/admin/views/order/OrderListView.java && git commit -m "feat(admin): beautify order list styles

- Page background: light gray (#F7F8FC)
- Ship button: gradient background with shadow

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 10: 用户列表视图 — `UserListView.java`

**Files:**
- Modify: `backend/admin-ui/src/main/java/com/seafood/admin/views/user/UserListView.java:1-190`

- [ ] **Step 1: 更新页面背景**

在构造函数中添加背景色（紧跟 `getStyle().set("padding", "28px 32px")` 后）：
```java
getStyle()
    .set("padding", "28px 32px")
    .set("background", "#F7F8FC");
```

- [ ] **Step 2: 更新"设为管理员"按钮**

替换 `configureGrid()` 中的 `adminButton` 样式（行 126-137）：
```java
adminButton.getStyle()
    .set("background", "linear-gradient(135deg, #4ECDC4 0%, #6FE3DB 100%)")
    .set("border-radius", "8px")
    .set("color", "#ffffff")
    .set("box-shadow", "0 2px 8px rgba(78, 205, 196, 0.3)");
```

- [ ] **Step 3: 提交**
```bash
git add backend/admin-ui/src/main/java/com/seafood/admin/views/user/UserListView.java && git commit -m "feat(admin): beautify user list styles

- Page background: light gray (#F7F8FC)
- Admin button: teal gradient background

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 11: 登录页 — `LoginView.java`

**Files:**
- Modify: `backend/admin-ui/src/main/java/com/seafood/admin/views/login/LoginView.java:1-123`

- [ ] **Step 1: 更新背景渐变**

替换 `getStyle()` 中的背景渐变（行 63-65）：
```java
getStyle()
    .set("background", "linear-gradient(135deg, #1A1A2E 0%, #2D3452 50%, #4ECDC4 100%)")
    .set("min-height", "100vh");
```

- [ ] **Step 2: 更新登录卡片样式**

替换 `loginForm.getStyle()` 块（行 105-110）：
```java
loginForm.getStyle()
    .set("background", "rgba(255,255,255,0.12)")
    .set("border-radius", "16px")
    .set("padding", "40px")
    .set("backdrop-filter", "blur(10px)")
    .set("box-shadow", "0 8px 32px rgba(0,0,0,0.25)")
    .set("border", "1px solid rgba(255,255,255,0.15)");
```

- [ ] **Step 3: 提交**
```bash
git add backend/admin-ui/src/main/java/com/seafood/miniapp/backend/admin-ui/src/main/java/com/seafood/admin/views/login/LoginView.java 2>/dev/null || git add backend/admin-ui/src/main/java/com/seafood/admin/views/login/LoginView.java && git commit -m "feat(admin): beautify login page styles

- Background: three-color gradient (dark + teal accent)
- Login card: enhanced blur + subtle border

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 12: 404 错误页 — `NotFoundView.java`

**Files:**
- Modify: `backend/admin-ui/src/main/java/com/seafood/admin/views/error/NotFoundView.java:1-76`

- [ ] **Step 1: 更新背景渐变**

替换 `setErrorParameter()` 中的背景（行 20-22）：
```java
getStyle()
    .set("background", "linear-gradient(135deg, #1A1A2E 0%, #2D3452 50%, #4ECDC4 100%)")
    .set("min-height", "100vh");
```

- [ ] **Step 2: 更新容器样式**

替换 `container.getStyle()` 块（行 25-31）：
```java
container.getStyle()
    .set("text-align", "center")
    .set("padding", "40px")
    .set("background", "rgba(255,255,255,0.12)")
    .set("border-radius", "16px")
    .set("backdrop-filter", "blur(10px)")
    .set("box-shadow", "0 8px 32px rgba(0,0,0,0.25)")
    .set("border", "1px solid rgba(255,255,255,0.15)");
```

- [ ] **Step 3: 提交**
```bash
git add backend/admin-ui/src/main/java/com/seafood/admin/views/error/NotFoundView.java && git commit -m "feat(admin): beautify 404 error page styles

- Background: three-color gradient matching login page
- Container: consistent blur + border treatment

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 13: 商品表单 — `ProductForm.java`

**Files:**
- Modify: `backend/admin-ui/src/main/java/com/seafood/admin/views/product/ProductForm.java:1-188`

- [ ] **Step 1: 更新保存按钮为渐变**

替换 `createButtonsLayout()` 中的 `save` 按钮设置（行 85）：
```java
save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
save.getStyle()
    .set("background", "linear-gradient(135deg, #FF6B6B 0%, #FF8E8E 100%)")
    .set("border-radius", "8px")
    .set("box-shadow", "0 2px 8px rgba(255, 107, 107, 0.3)");
```

同样更新 `delete` 按钮（行 86）：
```java
delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
delete.getStyle()
    .set("border-radius", "8px");
```

- [ ] **Step 2: 更新图片预览框**

替换 `imagePreview.getStyle()`（行 47-55）：
```java
imagePreview.getStyle()
    .set("border", "2px solid #E8EAED")
    .set("border-radius", "12px")
    .set("display", "flex")
    .set("align-items", "center")
    .set("justify-content", "center")
    .set("background", "#F7F8FC")
    .set("overflow", "hidden")
    .set("margin-top", "8px");
```

- [ ] **Step 3: 提交**
```bash
git add backend/admin-ui/src/main/java/com/seafood/admin/views/product/ProductForm.java && git commit -m "feat(admin): beautify product form styles

- Save button: gradient background + shadow
- Image preview: rounded with light background

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## 最终提交

- [ ] **合并提交（可选）**: 在所有任务完成后，可以合并为一次提交：

```bash
git fetch origin main && git merge --no-ff -m "feat: complete UI beautification for mini-program and admin-ui

Implemented vibrant modern design system across all views:
- Mini-program: new color palette, gradient buttons, enhanced shadows
- Admin-UI: unified gradient buttons, consistent card styling, light bg

Design system: #FF6B6B + #4ECDC4 + #FFE66D

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## 验收确认

完成所有任务后，确认：

- [ ] `app.wxss` — 所有 CSS 变量为新色彩系统
- [ ] 首页 Banner — 渐变背景 + 24rpx 圆角
- [ ] 商品卡片 — 阴影 + 渐变加入购物车按钮
- [ ] 分类/购物车/个人中心 — 样式统一
- [ ] Admin-UI 所有视图 — 背景 `#F7F8FC`
- [ ] 仪表盘 — 统计卡片顶部 4px Primary 边条
- [ ] 登录/404 页 — 三色渐变背景 + 毛玻璃卡片
- [ ] 所有按钮 — 渐变背景 + 8px 圆角
