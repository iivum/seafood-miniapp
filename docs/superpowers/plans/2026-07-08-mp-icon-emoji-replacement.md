# mp UI 微图标 emoji → van-icon 替换 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 mp 前端 9 个文件里充当"图标"用途的纯 Unicode 字符(emoji + dingbat 符号)全部替换成基于 `@vant/weapp` 的 `van-icon` 图标字体渲染,并顺手修复 `shared-empty`(实际是 `van-empty`)组件上一批从未生效过的死 `icon` prop。

**Architecture:** 纯前端 WXML/JSON/WXSS 静态标记改动,不涉及后端、不新增依赖(`@vant/weapp` 已是本仓既有依赖)。每个文件一个任务,任务内先写"不再含指定字符 + 含指定 van-icon name"的失败测试,再改 wxml,测试转绿。最后一个任务用本仓已有的 C5 视觉验证流程截图核实图标尺寸/颜色/是否和既有手绘 CSS 圆圈重叠。

**Tech Stack:** WeChat 小程序原生 WXML/WXSS/JSON,`@vant/weapp@1.11.7`(`van-icon`/`van-empty`),Jest(`ts-jest`,纯源码文本断言,不渲染)。

## Global Constraints

- 范围仅限设计文档 `docs/superpowers/specs/2026-07-08-mp-icon-emoji-replacement-design.md` 里列出的 9 个文件的 UI 微图标;不动分类导航缩略图(emoji→照片)、不动 Banner `emoji` 领域字段——这两类是明确的 Non-Goal
- 不引入任何新图标资源文件或图标字体生成工具链;全部复用 `@vant/weapp@1.11.7` 已安装的图标字体,图标名必须是本仓 `node_modules/@vant/weapp/lib/icon/index.wxss` 里真实存在的名字
- `van-empty`(`shared-empty`)的具名 slot 用法必须显式传 `image=""`——不传的话默认值 `'default'` 会同时触发一次外部 CDN(`img.yzcdn.cn`)图片请求,这是必须避免的外部网络依赖
- 每个改动文件严格 TDD:先写断言、跑 RED、再改 wxml、跑 GREEN、再 commit——不允许先改代码再补测试
- 提交信息用 Conventional Commits + 中文 subject,footer 带 `Co-Authored-By: Claude <noreply@anthropic.com>`(仓库 CLAUDE.md 全局约定)

---

### Task 1: 首页 `pages/index/index.wxml`

**Files:**
- Modify: `frontend/pages/index/index.wxml:17,26,30,144-151,154-161`
- Modify: `frontend/pages/index/index.json`
- Test: `frontend/src/__tests__/icon-emoji-index.test.ts`

**Interfaces:**
- Consumes: 无(纯静态标记,不依赖其他任务的产出)
- Produces: 无(其他任务不依赖本任务的产出;`van-icon` 组件注册模式在 Task 1-9 之间是重复模式,不是接口依赖)

- [ ] **Step 1: 写失败测试**

创建 `frontend/src/__tests__/icon-emoji-index.test.ts`:

```typescript
import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, '../../pages/index/index.wxml');
const JSON_PATH = path.resolve(__dirname, '../../pages/index/index.json');

describe('mp-01 首页 UI 微图标 emoji → van-icon(mp-icon-emoji-replacement)', () => {
  let wxml: string;

  beforeAll(() => {
    wxml = fs.readFileSync(WXML, 'utf8');
  });

  it('不再包含裸 emoji 图标字符(定位/铃铛/搜索/警告/无结果)', () => {
    expect(wxml).not.toMatch(/📍|🔔|🔍|⚠️|🦐/);
  });

  it('定位图标用 van-icon name="location-o"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="home-location__icon"\s+name="location-o"/);
  });

  it('通知铃铛图标用 van-icon name="bell"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="home-bell__icon"\s+name="bell"/);
  });

  it('搜索图标用 van-icon name="search"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="home-search__icon"\s+name="search"/);
  });

  it('错误态 shared-empty 不再传死 icon prop,改用具名 slot 塞 van-icon name="warning-o",且显式 image=""', () => {
    const blocks = wxml.match(/<shared-empty[\s\S]*?<\/shared-empty>/g) ?? [];
    const errorBlock = blocks.find((b) => b.includes('errorMessage'));
    expect(errorBlock).toBeDefined();
    expect(errorBlock).not.toMatch(/\bicon="/);
    expect(errorBlock).toMatch(/image=""/);
    expect(errorBlock).toMatch(/<van-icon\s+slot="image"\s+name="warning-o"/);
  });

  it('筛选后空态 shared-empty 不再传死 icon prop,改用具名 slot 塞 van-icon name="search",且显式 image=""', () => {
    const blocks = wxml.match(/<shared-empty[\s\S]*?<\/shared-empty>/g) ?? [];
    const emptyBlock = blocks.find((b) => b.includes('该分类暂无商品'));
    expect(emptyBlock).toBeDefined();
    expect(emptyBlock).not.toMatch(/\bicon="/);
    expect(emptyBlock).toMatch(/image=""/);
    expect(emptyBlock).toMatch(/<van-icon\s+slot="image"\s+name="search"/);
  });

  it('index.json 注册了 van-icon 组件', () => {
    const parsed = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
    expect(parsed.usingComponents['van-icon']).toBe('@vant/weapp/icon/index');
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && TZ=UTC npx jest src/__tests__/icon-emoji-index.test.ts`
Expected: 6 个 `it` 全部 FAIL(wxml 里 emoji 还在、van-icon 还没出现、json 还没注册 van-icon)

- [ ] **Step 3: 改 `pages/index/index.wxml`**

第 17 行,原:
```wxml
        <text class="home-location__icon">📍</text>
```
改为:
```wxml
        <van-icon class="home-location__icon" name="location-o" size="18px" />
```

第 26 行,原:
```wxml
        <text class="home-bell__icon">🔔</text>
```
改为:
```wxml
        <van-icon class="home-bell__icon" name="bell" size="18px" />
```

第 30 行,原:
```wxml
      <text class="home-search__icon">🔍</text>
```
改为:
```wxml
      <van-icon class="home-search__icon" name="search" size="18px" />
```

第 144-151 行(错误态),原:
```wxml
  <view class="home-state" wx:elif="{{isError}}">
    <shared-empty
      icon="⚠️"
      message="{{errorMessage || '加载失败'}}"
      retry-text="重新加载"
      bind:retry="onRetry"
    ></shared-empty>
  </view>
```
改为:
```wxml
  <view class="home-state" wx:elif="{{isError}}">
    <shared-empty
      image=""
      message="{{errorMessage || '加载失败'}}"
      retry-text="重新加载"
      bind:retry="onRetry"
    >
      <van-icon slot="image" name="warning-o" size="48px" />
    </shared-empty>
  </view>
```

第 154-161 行(空状态),原:
```wxml
  <view class="home-state" wx:elif="{{isEmpty}}">
    <shared-empty
      icon="🦐"
      message="该分类暂无商品"
      retry-text="查看全部"
      bind:retry="onClearFilter"
    ></shared-empty>
  </view>
```
改为:
```wxml
  <view class="home-state" wx:elif="{{isEmpty}}">
    <shared-empty
      image=""
      message="该分类暂无商品"
      retry-text="查看全部"
      bind:retry="onClearFilter"
    >
      <van-icon slot="image" name="search" size="48px" />
    </shared-empty>
  </view>
```

- [ ] **Step 4: 改 `pages/index/index.json`**

原:
```json
{
  "navigationBarTitleText": "海鲜生鲜商城",
  "usingComponents": {
    "shared-empty": "@vant/weapp/empty/index",
    "shared-loading": "@vant/weapp/loading/index"
  },
  "enablePullDownRefresh": true,
  "backgroundTextStyle": "dark",
  "navigationStyle": "custom"
}
```
改为:
```json
{
  "navigationBarTitleText": "海鲜生鲜商城",
  "usingComponents": {
    "shared-empty": "@vant/weapp/empty/index",
    "shared-loading": "@vant/weapp/loading/index",
    "van-icon": "@vant/weapp/icon/index"
  },
  "enablePullDownRefresh": true,
  "backgroundTextStyle": "dark",
  "navigationStyle": "custom"
}
```

- [ ] **Step 5: 跑测试确认通过**

Run: `cd frontend && TZ=UTC npx jest src/__tests__/icon-emoji-index.test.ts`
Expected: 6 个 `it` 全部 PASS

再跑一次本文件既有的回归测试确认没有破坏别的断言:

Run: `cd frontend && TZ=UTC npx jest pages/index/__tests__/`
Expected: 全部 PASS(`index-wxml-contract.test.js` 的 bindtap 契约断言不受本次改动影响,因为没碰任何 bindtap/catchtap)

- [ ] **Step 6: Commit**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp
git add frontend/pages/index/index.wxml frontend/pages/index/index.json frontend/src/__tests__/icon-emoji-index.test.ts
git commit -m "$(cat <<'EOF'
fix(mp): 首页 UI 微图标改用 van-icon,不再用 emoji

定位/铃铛/搜索三个微图标 + shared-empty(实为 van-empty)错误态/空态
之前传的 icon="⚠️"/icon="🦐" 从未生效(van-empty 没有 icon 这个 prop),
借这次机会用具名 slot 塞 van-icon 让它们真正渲染。

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: 分类页 `pages/category/category.wxml`

**Files:**
- Modify: `frontend/pages/category/category.wxml:22,57-62,67-70`
- Modify: `frontend/pages/category/category.json`
- Test: `frontend/src/__tests__/icon-emoji-category.test.ts`

**Interfaces:**
- Consumes: 无
- Produces: 无

- [ ] **Step 1: 写失败测试**

创建 `frontend/src/__tests__/icon-emoji-category.test.ts`:

```typescript
import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, '../../pages/category/category.wxml');
const JSON_PATH = path.resolve(__dirname, '../../pages/category/category.json');

describe('mp-02 分类页 UI 微图标 emoji → van-icon(mp-icon-emoji-replacement)', () => {
  let wxml: string;

  beforeAll(() => {
    wxml = fs.readFileSync(WXML, 'utf8');
  });

  it('不再包含裸 emoji 图标字符(搜索/警告/无结果)', () => {
    expect(wxml).not.toMatch(/🔍|⚠️|🦐/);
  });

  it('顶部搜索图标用 van-icon name="search"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="cat-topbar__search-icon"\s+name="search"/);
  });

  it('错误态 shared-empty 不再传死 icon prop,改用具名 slot 塞 van-icon name="warning-o",且显式 image=""', () => {
    const blocks = wxml.match(/<shared-empty[\s\S]*?<\/shared-empty>/g) ?? [];
    const errorBlock = blocks.find((b) => b.includes('errorMessage'));
    expect(errorBlock).toBeDefined();
    expect(errorBlock).not.toMatch(/\bicon="/);
    expect(errorBlock).toMatch(/image=""/);
    expect(errorBlock).toMatch(/<van-icon\s+slot="image"\s+name="warning-o"/);
  });

  it('空状态 shared-empty 不再传死 icon prop,改用具名 slot 塞 van-icon name="search",且显式 image=""', () => {
    const blocks = wxml.match(/<shared-empty[\s\S]*?<\/shared-empty>/g) ?? [];
    const emptyBlock = blocks.find((b) => b.includes('该分类暂无商品'));
    expect(emptyBlock).toBeDefined();
    expect(emptyBlock).not.toMatch(/\bicon="/);
    expect(emptyBlock).toMatch(/image=""/);
    expect(emptyBlock).toMatch(/<van-icon\s+slot="image"\s+name="search"/);
  });

  it('category.json 注册了 van-icon 组件', () => {
    const parsed = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
    expect(parsed.usingComponents['van-icon']).toBe('@vant/weapp/icon/index');
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && TZ=UTC npx jest src/__tests__/icon-emoji-category.test.ts`
Expected: 5 个 `it` 全部 FAIL

- [ ] **Step 3: 改 `pages/category/category.wxml`**

第 22 行,原:
```wxml
      <text class="cat-topbar__search-icon">🔍</text>
```
改为:
```wxml
      <van-icon class="cat-topbar__search-icon" name="search" size="18px" />
```

第 57-62 行(错误态),原:
```wxml
        <shared-empty
          icon="⚠️"
          message="{{errorMessage || '加载失败'}}"
          retry-text="重新加载"
          bind:retry="onRetry"
        ></shared-empty>
```
改为:
```wxml
        <shared-empty
          image=""
          message="{{errorMessage || '加载失败'}}"
          retry-text="重新加载"
          bind:retry="onRetry"
        >
          <van-icon slot="image" name="warning-o" size="48px" />
        </shared-empty>
```

第 67-70 行(空状态),原:
```wxml
        <shared-empty
          icon="🦐"
          message="该分类暂无商品"
        ></shared-empty>
```
改为:
```wxml
        <shared-empty
          image=""
          message="该分类暂无商品"
        >
          <van-icon slot="image" name="search" size="48px" />
        </shared-empty>
```

- [ ] **Step 4: 改 `pages/category/category.json`**

原:
```json
{
  "navigationBarTitleText": "商品分类",
  "usingComponents": {
    "shared-empty": "@vant/weapp/empty/index",
    "shared-loading": "@vant/weapp/loading/index"
  },
  "navigationStyle": "custom"
}
```
改为:
```json
{
  "navigationBarTitleText": "商品分类",
  "usingComponents": {
    "shared-empty": "@vant/weapp/empty/index",
    "shared-loading": "@vant/weapp/loading/index",
    "van-icon": "@vant/weapp/icon/index"
  },
  "navigationStyle": "custom"
}
```

- [ ] **Step 5: 跑测试确认通过**

Run: `cd frontend && TZ=UTC npx jest src/__tests__/icon-emoji-category.test.ts pages/category/__tests__/`
Expected: 全部 PASS

- [ ] **Step 6: Commit**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp
git add frontend/pages/category/category.wxml frontend/pages/category/category.json frontend/src/__tests__/icon-emoji-category.test.ts
git commit -m "$(cat <<'EOF'
fix(mp): 分类页 UI 微图标改用 van-icon,不再用 emoji

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: 购物车 `pages/cart/cart.wxml`

**Files:**
- Modify: `frontend/pages/cart/cart.wxml:26-33,48,57,79`
- Modify: `frontend/pages/cart/cart.json`
- Modify: `frontend/pages/cart/cart.wxss:95-98`
- Test: `frontend/src/__tests__/icon-emoji-cart.test.ts`

**Interfaces:**
- Consumes: 无
- Produces: 无

- [ ] **Step 1: 写失败测试**

创建 `frontend/src/__tests__/icon-emoji-cart.test.ts`:

```typescript
import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, '../../pages/cart/cart.wxml');
const JSON_PATH = path.resolve(__dirname, '../../pages/cart/cart.json');

describe('mp-04 购物车 UI 微图标 emoji → van-icon(mp-icon-emoji-replacement)', () => {
  let wxml: string;

  beforeAll(() => {
    wxml = fs.readFileSync(WXML, 'utf8');
  });

  it('不再包含裸 emoji/勾选字符(购物车/定位/勾选)', () => {
    expect(wxml).not.toMatch(/🛒|📍|✓/);
  });

  it('空购物车 shared-empty 不再传死 icon prop,改用具名 slot 塞 van-icon name="cart-o",且显式 image=""', () => {
    const blocks = wxml.match(/<shared-empty[\s\S]*?<\/shared-empty>/g) ?? [];
    expect(blocks.length).toBe(1);
    expect(blocks[0]).not.toMatch(/\bicon="/);
    expect(blocks[0]).toMatch(/image=""/);
    expect(blocks[0]).toMatch(/<van-icon\s+slot="image"\s+name="cart-o"/);
  });

  it('收货地址占位行用 van-icon name="location-o" + 独立文本节点(不再是 emoji 拼在一个 text 里)', () => {
    expect(wxml).toMatch(/<van-icon\s+name="location-o"[^/]*\/>\s*<text>请选择收货地址<\/text>/);
  });

  it('全选 checkbox 和单品 checkbox 的勾选态都用 van-icon name="success"', () => {
    const matches = [...wxml.matchAll(/<van-icon\s+wx:if="\{\{[^}]+\}\}"\s+class="cart-checkbox__icon"\s+name="success"/g)];
    expect(matches.length).toBe(2);
  });

  it('cart.json 注册了 van-icon 组件', () => {
    const parsed = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
    expect(parsed.usingComponents['van-icon']).toBe('@vant/weapp/icon/index');
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && TZ=UTC npx jest src/__tests__/icon-emoji-cart.test.ts`
Expected: 5 个 `it` 全部 FAIL

- [ ] **Step 3: 改 `pages/cart/cart.wxml`**

第 26-33 行(空购物车),原:
```wxml
  <view class="cart-empty" wx:if="{{cartItems.length === 0}}">
    <shared-empty
      icon="🛒"
      message="购物车空空如也"
      retry-text="去逛逛"
      bind:retry="onGoShopping"
    ></shared-empty>
  </view>
```
改为:
```wxml
  <view class="cart-empty" wx:if="{{cartItems.length === 0}}">
    <shared-empty
      image=""
      message="购物车空空如也"
      retry-text="去逛逛"
      bind:retry="onGoShopping"
    >
      <van-icon slot="image" name="cart-o" size="48px" />
    </shared-empty>
  </view>
```

第 47-49 行(收货地址占位),原:
```wxml
      <view class="address-card__empty" wx:else>
        <text class="address-card__placeholder">📍 请选择收货地址</text>
      </view>
```
改为:
```wxml
      <view class="address-card__empty" wx:else>
        <view class="address-card__placeholder">
          <van-icon name="location-o" size="14px" />
          <text>请选择收货地址</text>
        </view>
      </view>
```

第 57 行,原:
```wxml
          <text wx:if="{{isAllSelected}}" class="cart-checkbox__icon">✓</text>
```
改为:
```wxml
          <van-icon wx:if="{{isAllSelected}}" class="cart-checkbox__icon" name="success" size="16px" />
```

第 79 行,原:
```wxml
            <text wx:if="{{item.selected}}" class="cart-checkbox__icon">✓</text>
```
改为:
```wxml
            <van-icon wx:if="{{item.selected}}" class="cart-checkbox__icon" name="success" size="16px" />
```

- [ ] **Step 4: 改 `pages/cart/cart.wxss` 让 `.address-card__placeholder` 支持 icon+text 横排**

第 95-98 行,原:
```css
.address-card__placeholder {
  font-size: 28rpx;
  color: var(--muted, #5a5451);
}
```
改为:
```css
.address-card__placeholder {
  font-size: 28rpx;
  color: var(--muted, #5a5451);
  display: flex;
  align-items: center;
  gap: 8rpx;
}
```

- [ ] **Step 5: 改 `pages/cart/cart.json`**

查看当前内容:
```bash
cat frontend/pages/cart/cart.json
```
原:
```json
{
  "navigationBarTitleText": "购物车",
  "usingComponents": {
    "shared-empty": "@vant/weapp/empty/index",
    "shared-loading": "@vant/weapp/loading/index"
  },
  "navigationStyle": "custom"
}
```
改为:
```json
{
  "navigationBarTitleText": "购物车",
  "usingComponents": {
    "shared-empty": "@vant/weapp/empty/index",
    "shared-loading": "@vant/weapp/loading/index",
    "van-icon": "@vant/weapp/icon/index"
  },
  "navigationStyle": "custom"
}
```

- [ ] **Step 6: 跑测试确认通过**

Run: `cd frontend && TZ=UTC npx jest src/__tests__/icon-emoji-cart.test.ts pages/cart/__tests__/`
Expected: 全部 PASS(`cart-wxml-contract.test.js` 只检查 bindtap/catchtap 契约,不受影响)

- [ ] **Step 7: Commit**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp
git add frontend/pages/cart/cart.wxml frontend/pages/cart/cart.json frontend/pages/cart/cart.wxss frontend/src/__tests__/icon-emoji-cart.test.ts
git commit -m "$(cat <<'EOF'
fix(mp): 购物车 UI 微图标改用 van-icon,不再用 emoji

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: 订单确认 `pages-sub/order/order-confirm/order-confirm.wxml`

**Files:**
- Modify: `frontend/pages-sub/order/order-confirm/order-confirm.wxml:26,53,89,100,111`
- Modify: `frontend/pages-sub/order/order-confirm/order-confirm.json`
- Modify: `frontend/pages-sub/order/order-confirm/order-confirm.wxss:130-133`
- Test: `frontend/src/__tests__/icon-emoji-order-confirm.test.ts`

**Interfaces:**
- Consumes: 无
- Produces: 无

- [ ] **Step 1: 写失败测试**

创建 `frontend/src/__tests__/icon-emoji-order-confirm.test.ts`:

```typescript
import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, '../../pages-sub/order/order-confirm/order-confirm.wxml');
const JSON_PATH = path.resolve(__dirname, '../../pages-sub/order/order-confirm/order-confirm.json');

describe('mp-06 订单确认 UI 微图标 emoji → van-icon(mp-icon-emoji-replacement)', () => {
  let wxml: string;

  beforeAll(() => {
    wxml = fs.readFileSync(WXML, 'utf8');
  });

  it('不再包含裸返回箭头/定位/勾选字符', () => {
    expect(wxml).not.toMatch(/‹|📍|✓/);
  });

  it('顶部返回按钮用 van-icon name="arrow-left"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="confirm-topbar__back-icon"\s+name="arrow-left"/);
  });

  it('收货地址占位行用 van-icon name="location-o" + 独立文本节点', () => {
    expect(wxml).toMatch(/<van-icon\s+name="location-o"[^/]*\/>\s*<text>请选择收货地址<\/text>/);
  });

  it('三个配送方式的选中态勾选都用 van-icon name="success"', () => {
    const matches = [...wxml.matchAll(/<van-icon\s+wx:if="\{\{shippingMethod === '[A-Z]+'\}\}"\s+class="delivery-option__check"\s+name="success"/g)];
    expect(matches.length).toBe(3);
  });

  it('order-confirm.json 注册了 van-icon 组件', () => {
    const parsed = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
    expect(parsed.usingComponents['van-icon']).toBe('@vant/weapp/icon/index');
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && TZ=UTC npx jest src/__tests__/icon-emoji-order-confirm.test.ts`
Expected: 5 个 `it` 全部 FAIL

- [ ] **Step 3: 改 `pages-sub/order/order-confirm/order-confirm.wxml`**

第 26 行,原:
```wxml
      <text class="confirm-topbar__back-icon">‹</text>
```
改为:
```wxml
      <van-icon class="confirm-topbar__back-icon" name="arrow-left" size="20px" />
```

第 52-54 行(收货地址占位),原:
```wxml
      <view class="address-card__empty">
        <text class="address-card__placeholder">📍 请选择收货地址</text>
      </view>
```
改为:
```wxml
      <view class="address-card__empty">
        <view class="address-card__placeholder">
          <van-icon name="location-o" size="14px" />
          <text>请选择收货地址</text>
        </view>
      </view>
```

第 89 行,原:
```wxml
        <view wx:if="{{shippingMethod === 'FREE'}}" class="delivery-option__check">✓</view>
```
改为:
```wxml
        <van-icon wx:if="{{shippingMethod === 'FREE'}}" class="delivery-option__check" name="success" size="18px" />
```

第 100 行,原:
```wxml
        <view wx:if="{{shippingMethod === 'SF'}}" class="delivery-option__check">✓</view>
```
改为:
```wxml
        <van-icon wx:if="{{shippingMethod === 'SF'}}" class="delivery-option__check" name="success" size="18px" />
```

第 111 行,原:
```wxml
        <view wx:if="{{shippingMethod === 'ZTO'}}" class="delivery-option__check">✓</view>
```
改为:
```wxml
        <van-icon wx:if="{{shippingMethod === 'ZTO'}}" class="delivery-option__check" name="success" size="18px" />
```

- [ ] **Step 4: 改 `pages-sub/order/order-confirm/order-confirm.wxss` 让 `.address-card__placeholder` 支持 icon+text 横排**

第 130-133 行,原:
```css
.address-card__placeholder {
  font-size: 28rpx;
  color: var(--muted, #5a5451);
}
```
改为:
```css
.address-card__placeholder {
  font-size: 28rpx;
  color: var(--muted, #5a5451);
  display: flex;
  align-items: center;
  gap: 8rpx;
}
```

- [ ] **Step 5: 改 `pages-sub/order/order-confirm/order-confirm.json`**

原(该文件此前完全没有 `usingComponents`):
```json
{
  "navigationBarTitleText": "订单确认",
  "navigationStyle": "custom"
}
```
改为:
```json
{
  "navigationBarTitleText": "订单确认",
  "navigationStyle": "custom",
  "usingComponents": {
    "van-icon": "@vant/weapp/icon/index"
  }
}
```

- [ ] **Step 6: 跑测试确认通过**

Run: `cd frontend && TZ=UTC npx jest src/__tests__/icon-emoji-order-confirm.test.ts pages-sub/order/order-confirm/__tests__/`
Expected: 全部 PASS

- [ ] **Step 7: Commit**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp
git add frontend/pages-sub/order/order-confirm/order-confirm.wxml frontend/pages-sub/order/order-confirm/order-confirm.json frontend/pages-sub/order/order-confirm/order-confirm.wxss frontend/src/__tests__/icon-emoji-order-confirm.test.ts
git commit -m "$(cat <<'EOF'
fix(mp): 订单确认页 UI 微图标改用 van-icon,不再用裸符号

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: 订单列表 `pages-sub/order/order-list/order-list.wxml`

**Files:**
- Modify: `frontend/pages-sub/order/order-list/order-list.wxml:18,45-50,55-60,77`
- Modify: `frontend/pages-sub/order/order-list/order-list.json`
- Test: `frontend/src/__tests__/icon-emoji-order-list.test.ts`

**Interfaces:**
- Consumes: 无
- Produces: 无

- [ ] **Step 1: 写失败测试**

创建 `frontend/src/__tests__/icon-emoji-order-list.test.ts`:

```typescript
import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, '../../pages-sub/order/order-list/order-list.wxml');
const JSON_PATH = path.resolve(__dirname, '../../pages-sub/order/order-list/order-list.json');

describe('mp-08 订单列表 UI 微图标 emoji → van-icon(mp-icon-emoji-replacement)', () => {
  let wxml: string;

  beforeAll(() => {
    wxml = fs.readFileSync(WXML, 'utf8');
  });

  it('不再包含裸搜索/店铺符号/emoji 字符', () => {
    expect(wxml).not.toMatch(/⌕|⌂|⚠️|📦/);
  });

  it('顶部搜索图标用 van-icon name="search"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="order-list__search-icon"\s+name="search"/);
  });

  it('订单卡片商家行图标用 van-icon name="shop-o"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="order-card__shop-icon"\s+name="shop-o"/);
  });

  it('错误态 shared-empty 不再传死 icon prop,改用具名 slot 塞 van-icon name="warning-o",且显式 image=""', () => {
    const blocks = wxml.match(/<shared-empty[\s\S]*?<\/shared-empty>/g) ?? [];
    const errorBlock = blocks.find((b) => b.includes('errorMessage'));
    expect(errorBlock).toBeDefined();
    expect(errorBlock).not.toMatch(/\bicon="/);
    expect(errorBlock).toMatch(/image=""/);
    expect(errorBlock).toMatch(/<van-icon\s+slot="image"\s+name="warning-o"/);
  });

  it('空订单列表 shared-empty 不再传死 icon prop,改用具名 slot 塞 van-icon name="orders-o",且显式 image=""', () => {
    const blocks = wxml.match(/<shared-empty[\s\S]*?<\/shared-empty>/g) ?? [];
    const emptyBlock = blocks.find((b) => b.includes('还没有相关订单哦'));
    expect(emptyBlock).toBeDefined();
    expect(emptyBlock).not.toMatch(/\bicon="/);
    expect(emptyBlock).toMatch(/image=""/);
    expect(emptyBlock).toMatch(/<van-icon\s+slot="image"\s+name="orders-o"/);
  });

  it('order-list.json 注册了 van-icon 组件', () => {
    const parsed = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
    expect(parsed.usingComponents['van-icon']).toBe('@vant/weapp/icon/index');
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && TZ=UTC npx jest src/__tests__/icon-emoji-order-list.test.ts`
Expected: 6 个 `it` 全部 FAIL

- [ ] **Step 3: 改 `pages-sub/order/order-list/order-list.wxml`**

第 18 行,原:
```wxml
      <text class="order-list__search-icon">⌕</text>
```
改为:
```wxml
      <van-icon class="order-list__search-icon" name="search" size="18px" />
```

第 45-50 行(错误态),原:
```wxml
    <shared-empty
      icon="⚠️"
      message="{{errorMessage || '加载失败'}}"
      retry-text="重新加载"
      bind:retry="onRetry"
    ></shared-empty>
```
改为:
```wxml
    <shared-empty
      image=""
      message="{{errorMessage || '加载失败'}}"
      retry-text="重新加载"
      bind:retry="onRetry"
    >
      <van-icon slot="image" name="warning-o" size="48px" />
    </shared-empty>
```

第 55-60 行(空状态),原:
```wxml
    <shared-empty
      icon="📦"
      message="还没有相关订单哦"
      retry-text="去逛逛"
      bind:retry="onGoShopping"
    ></shared-empty>
```
改为:
```wxml
    <shared-empty
      image=""
      message="还没有相关订单哦"
      retry-text="去逛逛"
      bind:retry="onGoShopping"
    >
      <van-icon slot="image" name="orders-o" size="48px" />
    </shared-empty>
```

第 77 行,原:
```wxml
          <text class="order-card__shop-icon">⌂</text>
```
改为:
```wxml
          <van-icon class="order-card__shop-icon" name="shop-o" size="16px" />
```

- [ ] **Step 4: 改 `pages-sub/order/order-list/order-list.json`**

查看当前内容:
```bash
cat frontend/pages-sub/order/order-list/order-list.json
```
原:
```json
{
  "navigationBarTitleText": "我的订单",
  "usingComponents": {
    "shared-empty": "@vant/weapp/empty/index",
    "shared-loading": "@vant/weapp/loading/index",
    "order-action-row": "../../../src/features/order/components/OrderActionRow/index"
  }
}
```
改为:
```json
{
  "navigationBarTitleText": "我的订单",
  "usingComponents": {
    "shared-empty": "@vant/weapp/empty/index",
    "shared-loading": "@vant/weapp/loading/index",
    "order-action-row": "../../../src/features/order/components/OrderActionRow/index",
    "van-icon": "@vant/weapp/icon/index"
  }
}
```

- [ ] **Step 5: 跑测试确认通过**

Run: `cd frontend && TZ=UTC npx jest src/__tests__/icon-emoji-order-list.test.ts pages-sub/order/order-list/__tests__/`
Expected: 全部 PASS

- [ ] **Step 6: Commit**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp
git add frontend/pages-sub/order/order-list/order-list.wxml frontend/pages-sub/order/order-list/order-list.json frontend/src/__tests__/icon-emoji-order-list.test.ts
git commit -m "$(cat <<'EOF'
fix(mp): 订单列表页 UI 微图标改用 van-icon,不再用裸符号/emoji

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: 地址管理 `pages-sub/user/address/address-list.wxml`

**Files:**
- Modify: `frontend/pages-sub/user/address/address-list.wxml:33,82,92,98,105`
- Modify: `frontend/pages-sub/user/address/address-list.json`
- Test: `frontend/src/__tests__/icon-emoji-address-list.test.ts`

**Interfaces:**
- Consumes: 无
- Produces: 无

- [ ] **Step 1: 写失败测试**

创建 `frontend/src/__tests__/icon-emoji-address-list.test.ts`:

```typescript
import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, '../../pages-sub/user/address/address-list.wxml');
const JSON_PATH = path.resolve(__dirname, '../../pages-sub/user/address/address-list.json');

describe('mp-07 地址管理 UI 微图标 emoji → van-icon(mp-icon-emoji-replacement)', () => {
  let wxml: string;

  beforeAll(() => {
    wxml = fs.readFileSync(WXML, 'utf8');
  });

  it('不再包含裸返回箭头/编辑/删除/勾选/邮筒字符', () => {
    expect(wxml).not.toMatch(/‹|✏️|🗑️|✓|📭/);
  });

  it('顶部返回按钮用 van-icon name="arrow-left"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="addr-topbar__back-icon"\s+name="arrow-left"/);
  });

  it('编辑按钮用 van-icon name="edit"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="address-card__action-icon"\s+name="edit"/);
  });

  it('删除按钮用 van-icon name="delete-o"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="address-card__action-icon"\s+name="delete-o"/);
  });

  it('选择模式 radio 勾选态用 van-icon name="success"', () => {
    expect(wxml).toMatch(/<van-icon\s+wx:if="\{\{selectedId === item\.id\}\}"\s+class="address-card__radio-check"\s+name="success"/);
  });

  it('空地址列表图标用 van-icon name="location-o"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="empty-state__icon"\s+name="location-o"/);
  });

  it('address-list.json 注册了 van-icon 组件', () => {
    const parsed = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
    expect(parsed.usingComponents['van-icon']).toBe('@vant/weapp/icon/index');
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && TZ=UTC npx jest src/__tests__/icon-emoji-address-list.test.ts`
Expected: 7 个 `it` 全部 FAIL

- [ ] **Step 3: 改 `pages-sub/user/address/address-list.wxml`**

第 33 行,原:
```wxml
      <text class="addr-topbar__back-icon">‹</text>
```
改为:
```wxml
      <van-icon class="addr-topbar__back-icon" name="arrow-left" size="20px" />
```

第 82 行,原:
```wxml
          <text class="address-card__action-icon">✏️</text>
```
改为:
```wxml
          <van-icon class="address-card__action-icon" name="edit" size="16px" />
```

第 92 行,原:
```wxml
          <text class="address-card__action-icon">🗑️</text>
```
改为:
```wxml
          <van-icon class="address-card__action-icon" name="delete-o" size="16px" />
```

第 98 行,原:
```wxml
        <text wx:if="{{selectedId === item.id}}" class="address-card__radio-check">✓</text>
```
改为:
```wxml
        <van-icon wx:if="{{selectedId === item.id}}" class="address-card__radio-check" name="success" size="16px" />
```

第 105 行,原:
```wxml
    <text class="empty-state__icon">📭</text>
```
改为:
```wxml
    <van-icon class="empty-state__icon" name="location-o" size="48px" />
```

- [ ] **Step 4: 改 `pages-sub/user/address/address-list.json`**

原(该文件此前完全没有 `usingComponents`):
```json
{
  "navigationBarTitleText": "地址管理",
  "navigationStyle": "custom"
}
```
改为:
```json
{
  "navigationBarTitleText": "地址管理",
  "navigationStyle": "custom",
  "usingComponents": {
    "van-icon": "@vant/weapp/icon/index"
  }
}
```

- [ ] **Step 5: 跑测试确认通过**

Run: `cd frontend && TZ=UTC npx jest src/__tests__/icon-emoji-address-list.test.ts pages-sub/user/address/__tests__/`
Expected: 全部 PASS(`address-list-wxml-contract.test.js` 只检查 bindtap 契约,不受影响)

- [ ] **Step 6: Commit**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp
git add frontend/pages-sub/user/address/address-list.wxml frontend/pages-sub/user/address/address-list.json frontend/src/__tests__/icon-emoji-address-list.test.ts
git commit -m "$(cat <<'EOF'
fix(mp): 地址管理页 UI 微图标改用 van-icon,不再用裸符号/emoji

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: 登录页 `pages-sub/user/login/login.wxml`

**Files:**
- Modify: `frontend/pages-sub/user/login/login.wxml:47`
- Modify: `frontend/pages-sub/user/login/login.json`
- Modify: `frontend/pages-sub/user/login/login.wxss:220-225`
- Modify: `frontend/src/__tests__/login-flow.test.ts`(追加断言,不新建文件)

**Interfaces:**
- Consumes: 无
- Produces: 无

- [ ] **Step 1: 在 `login-flow.test.ts` 追加失败测试**

在文件末尾(`describe('login.wxml/js 对齐 OD mp-10-login...`这个 describe 块内,紧跟在最后一个 `it` 之后、闭合 `});` 之前)追加:

```typescript
  it('Step2 微信授权成功提示不再用裸 ✓ 字符,改用 van-icon name="success"(mp-icon-emoji-replacement)', () => {
    const src = readLoginWxml();
    expect(src).not.toMatch(/✓/);
    expect(src).toMatch(/<van-icon\s+name="success"[^/]*\/>\s*<text>微信授权成功<\/text>/);
  });
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && TZ=UTC npx jest src/__tests__/login-flow.test.ts`
Expected: 新增的这个 `it` FAIL,其余既有 `it` 仍 PASS

- [ ] **Step 3: 改 `pages-sub/user/login/login.wxml`**

第 47 行,原:
```wxml
          <text class="login-avatar-sub">✓ 微信授权成功</text>
```
改为:
```wxml
          <view class="login-avatar-sub">
            <van-icon name="success" size="14px" />
            <text>微信授权成功</text>
          </view>
```

- [ ] **Step 4: 改 `pages-sub/user/login/login.wxss` 让 `.login-avatar-sub` 支持 icon+text 横排**

第 220-225 行,原:
```css
.login-avatar-sub {
  display: block;
  font-size: 21rpx;
  color: var(--success, #318f5a);
  margin-top: 4rpx;
}
```
改为:
```css
.login-avatar-sub {
  display: flex;
  align-items: center;
  gap: 4rpx;
  font-size: 21rpx;
  color: var(--success, #318f5a);
  margin-top: 4rpx;
}
```

- [ ] **Step 5: 改 `pages-sub/user/login/login.json`**

原(该文件此前完全没有 `usingComponents`):
```json
{
  "navigationBarTitleText": "登录",
  "navigationBarBackgroundColor": "#64230d",
  "navigationBarTextStyle": "white",
  "backgroundColor": "#fffbf8",
  "enablePullDownRefresh": false
}
```
改为:
```json
{
  "navigationBarTitleText": "登录",
  "navigationBarBackgroundColor": "#64230d",
  "navigationBarTextStyle": "white",
  "backgroundColor": "#fffbf8",
  "enablePullDownRefresh": false,
  "usingComponents": {
    "van-icon": "@vant/weapp/icon/index"
  }
}
```

- [ ] **Step 6: 跑测试确认通过**

Run: `cd frontend && TZ=UTC npx jest src/__tests__/login-flow.test.ts src/__tests__/login-page-behavior.test.js`
Expected: 全部 PASS(`login-page-behavior.test.js` 是行为测试,不读 wxml 源码,不受影响)

- [ ] **Step 7: Commit**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp
git add frontend/pages-sub/user/login/login.wxml frontend/pages-sub/user/login/login.json frontend/pages-sub/user/login/login.wxss frontend/src/__tests__/login-flow.test.ts
git commit -m "$(cat <<'EOF'
fix(mp): 登录页 Step2 成功提示改用 van-icon,不再用裸勾选字符

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: 我的收藏 `pages-sub/user/favorites/favorites-list.wxml`

**Files:**
- Modify: `frontend/pages-sub/user/favorites/favorites-list.wxml:4,32,38`
- Modify: `frontend/pages-sub/user/favorites/favorites-list.json`
- Test: `frontend/src/__tests__/icon-emoji-favorites-list.test.ts`

**Interfaces:**
- Consumes: 无
- Produces: 无

- [ ] **Step 1: 写失败测试**

创建 `frontend/src/__tests__/icon-emoji-favorites-list.test.ts`:

```typescript
import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, '../../pages-sub/user/favorites/favorites-list.wxml');
const JSON_PATH = path.resolve(__dirname, '../../pages-sub/user/favorites/favorites-list.json');

describe('我的收藏 UI 微图标 emoji → van-icon(mp-icon-emoji-replacement)', () => {
  let wxml: string;

  beforeAll(() => {
    wxml = fs.readFileSync(WXML, 'utf8');
  });

  it('不再包含裸返回箭头/实心爱心/空心爱心字符', () => {
    expect(wxml).not.toMatch(/‹|♥|🤍/);
  });

  it('顶部返回按钮用 van-icon name="arrow-left"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="favorites-topbar__back-icon"\s+name="arrow-left"/);
  });

  it('取消收藏按钮用 van-icon name="like"(实心,区分空态用的描边 like-o)', () => {
    expect(wxml).toMatch(/<van-icon\s+name="like"\s+size="18px"\s*\/>/);
  });

  it('空收藏列表图标用 van-icon name="like-o"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="empty-state__icon"\s+name="like-o"/);
  });

  it('favorites-list.json 注册了 van-icon 组件', () => {
    const parsed = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
    expect(parsed.usingComponents['van-icon']).toBe('@vant/weapp/icon/index');
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && TZ=UTC npx jest src/__tests__/icon-emoji-favorites-list.test.ts`
Expected: 5 个 `it` 全部 FAIL

- [ ] **Step 3: 改 `pages-sub/user/favorites/favorites-list.wxml`**

第 4 行,原:
```wxml
      <text class="favorites-topbar__back-icon">‹</text>
```
改为:
```wxml
      <van-icon class="favorites-topbar__back-icon" name="arrow-left" size="20px" />
```

第 32 行,原:
```wxml
        <text>♥</text>
```
改为:
```wxml
        <van-icon name="like" size="18px" />
```

第 38 行,原:
```wxml
    <text class="empty-state__icon">🤍</text>
```
改为:
```wxml
    <van-icon class="empty-state__icon" name="like-o" size="48px" />
```

- [ ] **Step 4: 改 `pages-sub/user/favorites/favorites-list.json`**

原(该文件此前完全没有 `usingComponents`):
```json
{
  "navigationBarTitleText": "我的收藏",
  "navigationStyle": "custom",
  "enablePullDownRefresh": true
}
```
改为:
```json
{
  "navigationBarTitleText": "我的收藏",
  "navigationStyle": "custom",
  "enablePullDownRefresh": true,
  "usingComponents": {
    "van-icon": "@vant/weapp/icon/index"
  }
}
```

- [ ] **Step 5: 跑测试确认通过**

Run: `cd frontend && TZ=UTC npx jest src/__tests__/icon-emoji-favorites-list.test.ts pages-sub/user/favorites/__tests__/`
Expected: 全部 PASS

- [ ] **Step 6: Commit**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp
git add frontend/pages-sub/user/favorites/favorites-list.wxml frontend/pages-sub/user/favorites/favorites-list.json frontend/src/__tests__/icon-emoji-favorites-list.test.ts
git commit -m "$(cat <<'EOF'
fix(mp): 我的收藏页 UI 微图标改用 van-icon,不再用裸符号/emoji

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 9: 通用空状态组件 `src/shared/components/Empty`

**Files:**
- Modify: `frontend/src/shared/components/Empty/index.wxml:3`
- Create: `frontend/src/shared/components/Empty/index.json`
- Test: `frontend/src/shared/components/Empty/index.test.ts`

**Interfaces:**
- Consumes: 无
- Produces: 无(这个组件通过 `ProductCard`/`ProductList` 各自的 `usingComponents` 以 `shared-empty` 别名引用,本任务不改动那两个文件——它们没有传任何 `icon=` prop,不存在死代码要清理)

**背景**:这个组件此前完全没有 `index.json`,WeChat 自定义组件必须有 `{"component": true}` 才能被框架识别为 Component 而非 Page——这次一并补上。

- [ ] **Step 1: 写失败测试**

创建 `frontend/src/shared/components/Empty/index.test.ts`:

```typescript
import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, 'index.wxml');
const JSON_PATH = path.resolve(__dirname, 'index.json');

describe('shared/components/Empty 通用空状态组件(mp-icon-emoji-replacement)', () => {
  it('index.wxml 不再包含裸邮筒 emoji,改用 van-icon name="search"', () => {
    const wxml = fs.readFileSync(WXML, 'utf8');
    expect(wxml).not.toMatch(/📭/);
    expect(wxml).toMatch(/<van-icon\s+class="empty__icon"\s+name="search"/);
  });

  it('index.json 存在且声明 component:true、注册了 van-icon', () => {
    expect(fs.existsSync(JSON_PATH)).toBe(true);
    const parsed = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
    expect(parsed.component).toBe(true);
    expect(parsed.usingComponents['van-icon']).toBe('@vant/weapp/icon/index');
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && TZ=UTC npx jest src/shared/components/Empty/index.test.ts`
Expected: 2 个 `it` 全部 FAIL(第二个 FAIL 是因为 `index.json` 还不存在,`fs.readFileSync` 抛异常)

- [ ] **Step 3: 改 `src/shared/components/Empty/index.wxml`**

第 3 行,原:
```wxml
  <view class="empty__icon">📭</view>
```
改为:
```wxml
  <van-icon class="empty__icon" name="search" size="48px" />
```

- [ ] **Step 4: 新建 `src/shared/components/Empty/index.json`**

```json
{
  "component": true,
  "usingComponents": {
    "van-icon": "@vant/weapp/icon/index"
  }
}
```

- [ ] **Step 5: 跑测试确认通过**

Run: `cd frontend && TZ=UTC npx jest src/shared/components/Empty/index.test.ts`
Expected: 全部 PASS

再跑一次用到这个组件的既有测试确认没有破坏别的东西:

Run: `cd frontend && TZ=UTC npx jest src/features/product/`
Expected: 全部 PASS

- [ ] **Step 6: Commit**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp
git add frontend/src/shared/components/Empty/index.wxml frontend/src/shared/components/Empty/index.json frontend/src/shared/components/Empty/index.test.ts
git commit -m "$(cat <<'EOF'
fix(mp): 通用空状态组件改用 van-icon,补齐缺失的 index.json

组件此前没有 index.json(缺 component:true 声明),这次一并补上,
顺便注册 van-icon 替换硬编码的 📭 emoji。

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 10: 视觉验证与收尾

**Files:**
- 无固定文件(按视觉验证结果决定要不要微调 Task 1-9 里任何一个文件的 `size`/新增 `custom-class` + 对应 wxss 颜色规则)

**Interfaces:**
- Consumes: Task 1-9 的全部改动(需要它们已落地才能截图验证)
- Produces: 无

**背景**:设计文档 D5 明确标注一个只能实机渲染后才能确认的风险——`success`/`cart-o` 等自带填充圆形背景的 `van-icon`,可能和购物车复选框/收货方式单选/地址单选这几处已有的手绘 CSS 圆圈叠成"双层圆圈"。这一步不是可选的收尾,是兑现 D5 承诺的验证步骤。

- [ ] **Step 1: 跑本仓已有的 C5 感知 diff 视觉验证**

Run:
```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/frontend && npm run test:visual
```
Expected: 输出每个 mp 截图 vs OD golden 的 diff 百分比。重点看 `mp-01-home`(定位/铃铛/搜索/空态图标)、`mp-04-cart`(checkbox 勾选)、`mp-06-order-confirm`(配送方式勾选)、`mp-07-address`(radio 勾选)、`mp-08-order-list`(空态/商家图标)这几屏——如果视觉验证需要后端起着 + seed 数据,按 `frontend/e2e/tools/README.md` 里记录的步骤起后端(`seafood-backend:jvm` 镜像,tag 见 CLAUDE.md 视觉验证章节)。

- [ ] **Step 2: 人工核查截图,判断是否出现 D5 预警的双层圆圈问题**

打开 `frontend/e2e/screenshots/mp-04-cart-actual.png`、`mp-06-order-confirm-actual.png` 等截图(或用 `mcp__weapp-dev__mp_screenshot` 在 DevTools 里实时截),检查:
1. 购物车全选/单品 checkbox 勾选态是否出现"外层手绘圆圈 + van-icon 自带填充圆" 的双层视觉
2. 收货方式选中态 / 地址 radio 选中态同上
3. 所有新图标的尺寸/颜色是否明显突兀(比如太大出框、颜色和周围文字对比度过低)

- [ ] **Step 3: 如发现问题,按需修正**

若确认出现双层圆圈,对该 `van-icon` 加 `custom-class`,并在对应页面 `.wxss` 里加规则裁剪掉图标自带的圆形背景(具体写法取决于 `van-icon` 渲染出的 DOM 结构,实现时用 `mcp__weapp-dev__element_getWxml` 在 DevTools 里查实际渲染结构再定裁剪方式)。若发现颜色对比度不够,给对应 `custom-class` 加 `color` 规则,统一用 `var(--muted, ...)` / `var(--accent, ...)` 等本仓已有 token。

若一切正常(截图和之前视觉验证的基线相比,diff 百分比没有异常上升,人工核查也没发现双层圆圈),这一步不需要改任何代码,直接进 Step 4。

- [ ] **Step 4: 如有修正,补 commit;跑一次全量前端测试收尾**

若 Step 3 有改动:
```bash
cd /Users/linbinghui/agent-work/seafood-miniapp
git add -A
git commit -m "$(cat <<'EOF'
fix(mp): 修正 van-icon 视觉验证发现的图标尺寸/重叠问题

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

跑全量前端测试确认 Task 1-9 + 本任务的所有改动整体无回归:
```bash
cd frontend && npm test
```
Expected: 全部 PASS,0 failures
