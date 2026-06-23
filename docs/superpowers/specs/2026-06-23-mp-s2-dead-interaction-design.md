# mp S-2 死交互修复设计文档

> Sprint 2 MoSCoW：为所有可点击元素补加 `hover-class="is-clicked"` 0.18s 闪动反馈。
> `app.wxss` 已有 `.is-clicked { transform: scale(0.96); opacity: 0.8; transition: all 0.18s; }`，`profile.wxml` 已有示例。

---

## 1. 现状扫描结果

### 1.1 已完全覆盖（无需修改）

| 页面 | 文件 | 说明 |
|------|------|------|
| mp-06 订单确认 | `order-confirm.wxml` | 地址、3 个配送选项、提交按钮均已加 `hover-class="is-clicked"` |
| mp-07 地址管理 | `address-list.wxml` | 选择卡、编辑、删除、添加按钮均已加 |
| mp-07 地址编辑 | `address-edit.wxml` | 无 `bindtap`/`catchtap`，表单由系统 input 处理，无需加 |

### 1.2 需要修改的页面（5 个文件）

#### mp-01 首页（`frontend/pages/index/index.wxml`）

| 行号 | 元素 | 事件 | 缺少 | 备注 |
|------|------|------|------|------|
| L19 | `<swiper-item>` | `bindtap="onBannerTap"` | `hover-class` | `swiper-item` 支持 hover-class |
| L32-41 | `<view class="home-chip">` | `bindtap="onCategoryTap"` | `hover-class` | 在 `scroll-view` 内，需外层 view 包裹或直接加（测试确认可行） |
| L99-103 | `<view class="home-grid__add">` | `catchtap="addToCart"` | — | 已有 `home-grid__add--hover`，**保留专属 hover，不替换** |

**需修改**：L19 的 `swiper-item`（加 `hover-class="is-clicked" hover-stay-time="100"`）+ L32-41 的 `home-chip`（外层加 hover 属性）

#### mp-02 分类页（`frontend/pages/category/category.wxml`）

| 行号 | 元素 | 事件 | 缺少 | 备注 |
|------|------|------|------|------|
| L10-19 | `<view class="cat-sidebar__item">` | `bindtap="onCategoryTap"` | `hover-class` | 在 `scroll-view scroll-y` 内，scroll-view 内直接加有风险 |
| L75-81 | `<view class="cat-grid__add">` | `catchtap="addToCart"` | — | 已有 `cat-grid__add--hover`，**保留专属 hover** |

**需修改**：`cat-sidebar__item` 加 `hover-class="is-clicked" hover-stay-time="100"`

#### mp-03 商品详情（`frontend/pages-sub/product/product-detail/product-detail.wxml`）

| 行号 | 元素 | 事件 | 缺少 | 备注 |
|------|------|------|------|------|
| L34-37 | stepper `-` | `bindtap="onDecrement"` | — | 已有 `detail-stepper__btn--hover`，**保留** |
| L40-44 | stepper `+` | `bindtap="onIncrement"` | — | 同上 |
| L63-74 | `<view class="detail-recommend__item">` | `bindtap="goToProductDetail"` | `hover-class` | 在 `scroll-view scroll-x` 内 |
| L86,90 | 客服/收藏侧边按钮 | `bindtap` | — | 已有 `detail-footer__side-btn--hover` |
| L96-100 | 加入购物车 | `bindtap="onAddToCart"` | — | 已有 `detail-footer__btn--hover` |
| L101-105 | 立即购买 | `bindtap="onBuyNow"` | — | 同上 |

**需修改**：L63-74 的 `detail-recommend__item`（scroll-view 内，见 §2 策略）

#### mp-04 购物车（`frontend/pages/cart/cart.wxml`）

| 行号 | 元素 | 事件 | 缺少 | 备注 |
|------|------|------|------|------|
| L20 | `<navigator class="cart-address">` | `bindtap="selectAddress"` | `hover-class` | navigator 支持 hover-class |
| L38-43 | `<view class="cart-bar__select-all">` | `bindtap="onSelectAllTap"` | `hover-class` | — |
| L54-58 | `<view class="cart-item__check">` | `catchtap="onItemCheckTap"` | `hover-class` | — |
| L74-80 | stepper `-` | `catchtap="onMinus"` | — | 已有 `cart-stepper__btn--hover` |
| L82-88 | stepper `+` | `catchtap="onPlus"` | — | 同上 |
| L93-98 | 删除 `×` | `catchtap="onRemove"` | — | 已有 `cart-item__remove--hover` |
| L123-126 | `去结算` | `bindtap="onCheckout"` | `hover-class` | — |

**需修改**：L20 navigator、L38 全选、L54 checkbox、L123 去结算按钮

#### mp-08 订单列表（`frontend/pages-sub/order/order-list/order-list.wxml`）

| 行号 | 元素 | 事件 | 缺少 | 备注 |
|------|------|------|------|------|
| L13 | `<view class="order-list__back">` | `bindtap="onBack"` | — | 已有 `order-list__back--hover`，**保留** |
| L17 | `<view class="order-list__search">` | `bindtap="onSearch"` | — | 已有 `order-list__search--hover`，**保留** |
| L24-34 | `<view class="order-list__tab">` | `bindtap="onTabTap"` | `hover-class` | 在 `scroll-view scroll-x` 内 |
| L62-69 | `<view class="order-card">` | `bindtap="onOrderTap"` | `hover-class` | 订单整卡点击，不在 scroll-view 内 |

**需修改**：L24-34 tab（scroll-view 内）+ L62-69 订单卡片

---

## 2. scroll-view 内部元素处理策略

微信小程序官方文档指出：`scroll-view` 内直接子元素的 `hover-class` 在滚动时可能不触发（滚动手势会中断 hover 状态）。实测影响程度与方向有关：

| 场景 | 风险 | 推荐策略 |
|------|------|---------|
| `scroll-x` 内水平项（chips、tabs、recommend） | 低（竖向 tap 不冲突） | **直接加** `hover-class`，不包裹 |
| `scroll-y` 内垂直项（cat-sidebar） | 中（同向滚动可能消抖） | **直接加**，hover-stay-time=100ms 够短，实测无感 |
| 嵌套 scroll-view 内 | 高 | 需外层 view 包裹 |

**结论**：本次所有 scroll-view 内元素均直接加属性，不额外包裹。理由：
1. `hover-stay-time="100"` 极短，触发条件是"tap 不滑动"，用户点击场景下不会发生滚动冲突
2. 包裹额外 view 会破坏当前 flex/grid 布局，引入回归风险
3. profile.wxml 已有案例（scroll-view 内 hover-class），项目自身已验证可行

---

## 3. 修改汇总表

| 文件 | 新增 hover-class 数 | 备注 |
|------|---------------------|------|
| `pages/index/index.wxml` | 2（banner + chip） | swiper-item + home-chip |
| `pages/category/category.wxml` | 1（侧边栏项） | cat-sidebar__item |
| `pages-sub/product/product-detail/product-detail.wxml` | 1（推荐商品项） | scroll-view 内 |
| `pages/cart/cart.wxml` | 4（地址/全选/checkbox/去结算） | navigator 也支持 hover-class |
| `pages-sub/order/order-list/order-list.wxml` | 2（tab + 订单卡片） | tab 在 scroll-view 内 |
| **合计** | **10 处** | — |

---

## 4. 测试策略

### 4.1 快照测试（TDD 主策略）

每个页面新建测试文件 `src/features/<domain>/__tests__/<page>-hover.test.ts`，使用 `fs.readFileSync` 读取 WXML 字符串，用正则断言：
1. 含 `bindtap`/`catchtap` 的元素同时含 `hover-class`
2. `hover-stay-time="100"` 存在（验证全局统一）

```ts
// 示例断言（index 首页 chip）
const wxml = fs.readFileSync(INDEX_WXML, 'utf8');
// 每个 onCategoryTap 元素都必须有 hover-class
const chipMatches = wxml.match(/bindtap="onCategoryTap"[^>]*/g) ?? [];
for (const el of chipMatches) {
  expect(el).toMatch(/hover-class=/);
  expect(el).toMatch(/hover-stay-time="100"/);
}
```

### 4.2 全量快照锁定

复用 `visual-v2-snapshots.test.ts` 的模式，新建 `src/__tests__/hover-class-contract.test.ts`：
- 读取全部 5 个需修改的 wxml 文件
- `toMatchSnapshot()` 锁定修改后状态，防止后续误删 hover 属性

### 4.3 视觉回归（非本次覆盖）

与 C5 感知 diff 工具联动：确认 hover 动效（scale 0.96）不引入布局偏移，CI 单独跑（不阻塞本次合并）。

---

## 5. 不修改项

- `app.wxss` — `.is-clicked` 定义已正确，不动
- 任何 JS/TS 业务逻辑 — 纯属性变更
- 专属 hover class 元素（`*--hover` 命名已有语义的不替换）：
  - `home-grid__add--hover`、`cat-grid__add--hover`（add 按钮）
  - `detail-stepper__btn--hover`、`cart-stepper__btn--hover`（stepper）
  - `detail-footer__side-btn--hover`、`detail-footer__btn--hover`（footer 按钮）
  - `cart-item__remove--hover`（删除）
  - `order-list__back--hover`、`order-list__search--hover`（header 按钮）
