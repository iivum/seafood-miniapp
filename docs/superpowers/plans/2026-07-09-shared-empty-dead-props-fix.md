# shared-empty 死代码修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 index/category/cart/order-list 这 4 个页面的 `<shared-empty>` 调用里传的 `message`/`retry-text`/`bind:retry` 真正生效——目前这 4 页的 `shared-empty` 解析成 `@vant/weapp/empty/index`(没有这些 prop/事件,全是死代码),这次改成全仓库统一指向本地手写组件 `src/shared/components/Empty`(真正支持)。

**Architecture:** 先给本地 Empty 组件新增两个向后兼容的可选 prop(`icon`/`retryText`,带默认值,不影响现有调用方),再逐页把 `usingComponents` 里的 `shared-empty` 指向改掉、把调用点从 vant 专用的 `image=""` + 具名 slot 写法簡化成直接传 `icon="..."` 字符串。

**Tech Stack:** WeChat 小程序原生 WXML/WXSS/JSON,本地自定义组件(`Component()`),Jest(`ts-jest`,源码文本断言)。

## Global Constraints

- `src/shared/components/Empty` 新增的 `icon`/`retryText` 两个 prop 必须给和现状(硬编码值)完全一致的默认值(`icon` 默认 `'search'`,`retryText` 默认 `'重试'`)——`ProductCard`/`ProductList` 现有调用不传这两个新 prop,必须零改动、零回归
- 4 个页面的 `shared-empty` 一律改指向 `/src/shared/components/Empty/index`(和 `ProductCard`/`ProductList` 已有写法完全一致的绝对路径字符串)
- 调用点去掉 vant 专用的 `image=""` 属性和 `<van-icon slot="image">` 子节点,改成直接传 `icon="..."` 字符串 prop;`message`/`retry-text`/`bind:retry` 三个属性名不变(它们本来就是对的)
- 每个文件严格 TDD:先写断言、跑 RED、再改代码、跑 GREEN
- 本次改动这几个页面的 `van-icon` 注册(PR #48 加的)不能删——同页面其他直接用 `<van-icon>` 的地方还需要它
- 提交信息:Conventional Commits + 中文 subject,footer 带 `Co-Authored-By: Claude <noreply@anthropic.com>`

---

### Task 1: 本地 Empty 组件新增 `icon`/`retryText` 两个 prop

**Files:**
- Modify: `frontend/src/shared/components/Empty/index.js`
- Modify: `frontend/src/shared/components/Empty/index.ts`
- Modify: `frontend/src/shared/components/Empty/index.wxml`
- Modify: `frontend/src/shared/components/Empty/index.test.ts`

**Interfaces:**
- Consumes: 无
- Produces: `icon`(String,默认 `'search'`)、`retryText`(String,默认 `'重试'`)两个新 properties,供 Task 2-5 的页面调用点使用

- [ ] **Step 1: 写失败测试**

在 `frontend/src/shared/components/Empty/index.test.ts` 的 `describe` 块内,紧跟在现有两个 `it` 之后追加:

```typescript
  it('index.js 新增 icon(默认 "search")和 retryText(默认 "重试")两个 properties', () => {
    const js = fs.readFileSync(path.resolve(__dirname, 'index.js'), 'utf8');
    expect(js).toMatch(/icon:\s*\{\s*type:\s*String,\s*value:\s*'search',?\s*\}/);
    expect(js).toMatch(/retryText:\s*\{\s*type:\s*String,\s*value:\s*'重试',?\s*\}/);
  });

  it('index.wxml 用 {{icon}}/{{retryText}} 绑定渲染,不再硬编码图标名/按钮文案', () => {
    const wxml = fs.readFileSync(WXML, 'utf8');
    expect(wxml).toMatch(/<van-icon\s+class="empty__icon"\s+name="\{\{icon\}\}"/);
    expect(wxml).toMatch(/>\{\{retryText\}\}<\/button>/);
  });
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && TZ=UTC npx jest src/shared/components/Empty/index.test.ts`
Expected: 新增的 2 个 `it` FAIL(`index.js` 还没有 `icon`/`retryText` properties,`index.wxml` 还是硬编码 `name="search"` 和字面量"重试")。原有 2 个 `it` 仍 PASS。

- [ ] **Step 3: 改 `frontend/src/shared/components/Empty/index.js`**

原:
```javascript
Component({
  options: {
    multipleSlots: true,
  },
  properties: {
    message: { type: String, value: '暂无数据' },
    retryable: { type: Boolean, value: false },
  },
  methods: {
    onRetry() {
      this.triggerEvent('retry');
    },
  },
});
```
改为:
```javascript
Component({
  options: {
    multipleSlots: true,
  },
  properties: {
    message: { type: String, value: '暂无数据' },
    retryable: { type: Boolean, value: false },
    icon: { type: String, value: 'search' },
    retryText: { type: String, value: '重试' },
  },
  methods: {
    onRetry() {
      this.triggerEvent('retry');
    },
  },
});
```

- [ ] **Step 4: 改 `frontend/src/shared/components/Empty/index.wxml`**

原:
```wxml
<!-- shared/components/Empty/index.wxml -->
<view class="empty">
  <van-icon class="empty__icon" name="search" size="48px" />
  <view class="empty__message">{{message}}</view>
  <button
    wx:if="{{retryable}}"
    class="empty__retry btn"
    bindtap="onRetry"
  >重试</button>
</view>
```
改为:
```wxml
<!-- shared/components/Empty/index.wxml -->
<view class="empty">
  <van-icon class="empty__icon" name="{{icon}}" size="48px" />
  <view class="empty__message">{{message}}</view>
  <button
    wx:if="{{retryable}}"
    class="empty__retry btn"
    bindtap="onRetry"
  >{{retryText}}</button>
</view>
```

- [ ] **Step 5: 改 `frontend/src/shared/components/Empty/index.ts`**

原:
```typescript
// Shared `<empty />` component TypeScript type re-exports.
// The runtime lives in `index.js` (WeChat mini-program component).
export interface EmptyProps {
  message: string;
  retryable?: boolean;
}

export const EMPTY_DEFAULT_MESSAGE = '暂无数据';
```
改为:
```typescript
// Shared `<empty />` component TypeScript type re-exports.
// The runtime lives in `index.js` (WeChat mini-program component).
export interface EmptyProps {
  message: string;
  retryable?: boolean;
  icon?: string;
  retryText?: string;
}

export const EMPTY_DEFAULT_MESSAGE = '暂无数据';
```

- [ ] **Step 6: 跑测试确认通过 + 回归检查**

Run: `cd frontend && TZ=UTC npx jest src/shared/components/Empty/index.test.ts`
Expected: 全部 4 个 `it` PASS

再跑一次消费方(`ProductCard`/`ProductList`)的回归测试,确认新增的可选 prop 不影响现有调用(它们不传 `icon`/`retryText`,走默认值,行为应完全不变):

Run: `cd frontend && TZ=UTC npx jest src/features/product/`
Expected: 全部 PASS

- [ ] **Step 7: Commit**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/.claude/worktrees/mp-icon-emoji-replacement
git add frontend/src/shared/components/Empty/index.js frontend/src/shared/components/Empty/index.ts frontend/src/shared/components/Empty/index.wxml frontend/src/shared/components/Empty/index.test.ts
git commit -m "$(cat <<'EOF'
feat(mp): Empty 组件新增 icon/retryText 可选 prop,向后兼容

为下一步把 4 个页面的 shared-empty 从 vant 的 van-empty(没有
message/retry 支持)切到本地组件做准备。新增 prop 都给了和现状
一致的默认值,ProductCard/ProductList 现有调用零改动。

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: 首页 `pages/index/index.wxml` 切换 shared-empty 指向

**Files:**
- Modify: `frontend/pages/index/index.wxml:144-165`
- Modify: `frontend/pages/index/index.json`
- Modify: `frontend/src/__tests__/icon-emoji-index.test.ts`

**Interfaces:**
- Consumes: Task 1 产出的 `icon`/`retryText` prop(本任务只需要知道它们存在、有默认值,不需要 Task 1 的具体实现细节)
- Produces: 无

- [ ] **Step 1: 改测试**

打开 `frontend/src/__tests__/icon-emoji-index.test.ts`,把现有这两个 `it`(第 30-46 行,断言"死 icon prop 已删 + 用具名 slot")整体替换成:

```typescript
  it('错误态 shared-empty 用 icon="warning-o" prop(本地组件真正支持,不再需要 vant 的 image=""/slot 绕过写法)', () => {
    const blocks = wxml.match(/<shared-empty[\s\S]*?<\/shared-empty>/g) ?? [];
    const errorBlock = blocks.find((b) => b.includes('errorMessage'));
    expect(errorBlock).toBeDefined();
    expect(errorBlock).not.toMatch(/image=""/);
    expect(errorBlock).not.toMatch(/slot="image"/);
    expect(errorBlock).toMatch(/icon="warning-o"/);
  });

  it('筛选后空态 shared-empty 用 icon="search" prop', () => {
    const blocks = wxml.match(/<shared-empty[\s\S]*?<\/shared-empty>/g) ?? [];
    const emptyBlock = blocks.find((b) => b.includes('该分类暂无商品'));
    expect(emptyBlock).toBeDefined();
    expect(emptyBlock).not.toMatch(/image=""/);
    expect(emptyBlock).not.toMatch(/slot="image"/);
    expect(emptyBlock).toMatch(/icon="search"/);
  });

  it('index.json 的 shared-empty 指向本地组件(不再是 vant 的 van-empty,message/retry-text/bind:retry 现在真正生效)', () => {
    const parsed = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
    expect(parsed.usingComponents['shared-empty']).toBe('/src/shared/components/Empty/index');
  });
```

文件末尾原有的 `it('index.json 注册了 van-icon 组件', ...)` 保留不动(页面自己直接用的 `<van-icon>` 还需要这个注册)。

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && TZ=UTC npx jest src/__tests__/icon-emoji-index.test.ts`
Expected: 新的 3 个 `it` FAIL(wxml/json 还没改)

- [ ] **Step 3: 改 `pages/index/index.wxml`**

第 143-153 行(错误态),原:
```wxml
  <!-- 错误状态 -->
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
改为:
```wxml
  <!-- 错误状态 -->
  <view class="home-state" wx:elif="{{isError}}">
    <shared-empty
      icon="warning-o"
      message="{{errorMessage || '加载失败'}}"
      retry-text="重新加载"
      bind:retry="onRetry"
    ></shared-empty>
  </view>
```

第 155-165 行(空状态),原:
```wxml
  <!-- 空状态(过滤后无产品) -->
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
改为:
```wxml
  <!-- 空状态(过滤后无产品) -->
  <view class="home-state" wx:elif="{{isEmpty}}">
    <shared-empty
      icon="search"
      message="该分类暂无商品"
      retry-text="查看全部"
      bind:retry="onClearFilter"
    ></shared-empty>
  </view>
```

- [ ] **Step 4: 改 `pages/index/index.json`**

原:
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
改为:
```json
{
  "navigationBarTitleText": "海鲜生鲜商城",
  "usingComponents": {
    "shared-empty": "/src/shared/components/Empty/index",
    "shared-loading": "@vant/weapp/loading/index",
    "van-icon": "@vant/weapp/icon/index"
  },
  "enablePullDownRefresh": true,
  "backgroundTextStyle": "dark",
  "navigationStyle": "custom"
}
```

- [ ] **Step 5: 跑测试确认通过**

Run: `cd frontend && TZ=UTC npx jest src/__tests__/icon-emoji-index.test.ts pages/index/__tests__/`
Expected: 全部 PASS

- [ ] **Step 6: Commit**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/.claude/worktrees/mp-icon-emoji-replacement
git add frontend/pages/index/index.wxml frontend/pages/index/index.json frontend/src/__tests__/icon-emoji-index.test.ts
git commit -m "$(cat <<'EOF'
fix(mp): 首页 shared-empty 改指向本地组件,message/retry 真正生效

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: 分类页 `pages/category/category.wxml` 切换 shared-empty 指向

**Files:**
- Modify: `frontend/pages/category/category.wxml:56-74`
- Modify: `frontend/pages/category/category.json`
- Modify: `frontend/src/__tests__/icon-emoji-category.test.ts`

**Interfaces:**
- Consumes: Task 1 产出的 `icon`/`retryText` prop
- Produces: 无

- [ ] **Step 1: 改测试**

打开 `frontend/src/__tests__/icon-emoji-category.test.ts`,把现有这两个 `it`(第 22-38 行)整体替换成:

```typescript
  it('错误态 shared-empty 用 icon="warning-o" prop(本地组件真正支持,不再需要 vant 的 image=""/slot 绕过写法)', () => {
    const blocks = wxml.match(/<shared-empty[\s\S]*?<\/shared-empty>/g) ?? [];
    const errorBlock = blocks.find((b) => b.includes('errorMessage'));
    expect(errorBlock).toBeDefined();
    expect(errorBlock).not.toMatch(/image=""/);
    expect(errorBlock).not.toMatch(/slot="image"/);
    expect(errorBlock).toMatch(/icon="warning-o"/);
  });

  it('空状态 shared-empty 用 icon="search" prop', () => {
    const blocks = wxml.match(/<shared-empty[\s\S]*?<\/shared-empty>/g) ?? [];
    const emptyBlock = blocks.find((b) => b.includes('该分类暂无商品'));
    expect(emptyBlock).toBeDefined();
    expect(emptyBlock).not.toMatch(/image=""/);
    expect(emptyBlock).not.toMatch(/slot="image"/);
    expect(emptyBlock).toMatch(/icon="search"/);
  });

  it('category.json 的 shared-empty 指向本地组件', () => {
    const parsed = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
    expect(parsed.usingComponents['shared-empty']).toBe('/src/shared/components/Empty/index');
  });
```

第 40-43 行现有的 `it('category.json 注册了 van-icon 组件', ...)` 保留不动。

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && TZ=UTC npx jest src/__tests__/icon-emoji-category.test.ts`
Expected: 新的 3 个 `it` FAIL

- [ ] **Step 3: 改 `pages/category/category.wxml`**

第 55-65 行(错误态),原:
```wxml
      <!-- 错误状态 -->
      <view class="cat-state" wx:elif="{{isError}}">
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
改为:
```wxml
      <!-- 错误状态 -->
      <view class="cat-state" wx:elif="{{isError}}">
        <shared-empty
          icon="warning-o"
          message="{{errorMessage || '加载失败'}}"
          retry-text="重新加载"
          bind:retry="onRetry"
        ></shared-empty>
      </view>
```

第 67-74 行(空状态,注意这个没有 retry-text/bind:retry),原:
```wxml
      <!-- 空状态 -->
      <view class="cat-state" wx:elif="{{isEmpty}}">
        <shared-empty
          image=""
          message="该分类暂无商品"
        >
          <van-icon slot="image" name="search" size="48px" />
        </shared-empty>
      </view>
```
改为:
```wxml
      <!-- 空状态 -->
      <view class="cat-state" wx:elif="{{isEmpty}}">
        <shared-empty
          icon="search"
          message="该分类暂无商品"
        ></shared-empty>
      </view>
```

- [ ] **Step 4: 改 `pages/category/category.json`**

原:
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
改为:
```json
{
  "navigationBarTitleText": "商品分类",
  "usingComponents": {
    "shared-empty": "/src/shared/components/Empty/index",
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
cd /Users/linbinghui/agent-work/seafood-miniapp/.claude/worktrees/mp-icon-emoji-replacement
git add frontend/pages/category/category.wxml frontend/pages/category/category.json frontend/src/__tests__/icon-emoji-category.test.ts
git commit -m "$(cat <<'EOF'
fix(mp): 分类页 shared-empty 改指向本地组件,message/retry 真正生效

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: 购物车 `pages/cart/cart.wxml` 切换 shared-empty 指向

**Files:**
- Modify: `frontend/pages/cart/cart.wxml:26-35`
- Modify: `frontend/pages/cart/cart.json`
- Modify: `frontend/src/__tests__/icon-emoji-cart.test.ts`

**Interfaces:**
- Consumes: Task 1 产出的 `icon`/`retryText` prop
- Produces: 无

- [ ] **Step 1: 改测试**

打开 `frontend/src/__tests__/icon-emoji-cart.test.ts`,把现有这个 `it`(第 18-24 行)替换成:

```typescript
  it('空购物车 shared-empty 用 icon="cart-o" prop(本地组件真正支持,不再需要 vant 的 image=""/slot 绕过写法)', () => {
    const blocks = wxml.match(/<shared-empty[\s\S]*?<\/shared-empty>/g) ?? [];
    expect(blocks.length).toBe(1);
    expect(blocks[0]).not.toMatch(/image=""/);
    expect(blocks[0]).not.toMatch(/slot="image"/);
    expect(blocks[0]).toMatch(/icon="cart-o"/);
  });

  it('cart.json 的 shared-empty 指向本地组件', () => {
    const parsed = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
    expect(parsed.usingComponents['shared-empty']).toBe('/src/shared/components/Empty/index');
  });
```

其余 `it`(不再包含裸 emoji/勾选字符、收货地址占位行、checkbox 勾选态、cart.json 注册 van-icon)全部保留不动。

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && TZ=UTC npx jest src/__tests__/icon-emoji-cart.test.ts`
Expected: 新的 2 个 `it` FAIL,其余保留的 `it` 仍 PASS

- [ ] **Step 3: 改 `pages/cart/cart.wxml`**

第 25-35 行,原:
```wxml
  <!-- 空购物车 -->
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
改为:
```wxml
  <!-- 空购物车 -->
  <view class="cart-empty" wx:if="{{cartItems.length === 0}}">
    <shared-empty
      icon="cart-o"
      message="购物车空空如也"
      retry-text="去逛逛"
      bind:retry="onGoShopping"
    ></shared-empty>
  </view>
```

- [ ] **Step 4: 改 `pages/cart/cart.json`**

原:
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
改为:
```json
{
  "navigationBarTitleText": "购物车",
  "usingComponents": {
    "shared-empty": "/src/shared/components/Empty/index",
    "shared-loading": "@vant/weapp/loading/index",
    "van-icon": "@vant/weapp/icon/index"
  },
  "navigationStyle": "custom"
}
```

- [ ] **Step 5: 跑测试确认通过**

Run: `cd frontend && TZ=UTC npx jest src/__tests__/icon-emoji-cart.test.ts pages/cart/__tests__/`
Expected: 全部 PASS

- [ ] **Step 6: Commit**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/.claude/worktrees/mp-icon-emoji-replacement
git add frontend/pages/cart/cart.wxml frontend/pages/cart/cart.json frontend/src/__tests__/icon-emoji-cart.test.ts
git commit -m "$(cat <<'EOF'
fix(mp): 购物车 shared-empty 改指向本地组件,message/retry 真正生效

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: 订单列表 `pages-sub/order/order-list/order-list.wxml` 切换 shared-empty 指向

**Files:**
- Modify: `frontend/pages-sub/order/order-list/order-list.wxml:44-64`
- Modify: `frontend/pages-sub/order/order-list/order-list.json`
- Modify: `frontend/src/__tests__/icon-emoji-order-list.test.ts`

**Interfaces:**
- Consumes: Task 1 产出的 `icon`/`retryText` prop
- Produces: 无

- [ ] **Step 1: 改测试**

打开 `frontend/src/__tests__/icon-emoji-order-list.test.ts`,把现有这两个 `it`(第 26-42 行)整体替换成:

```typescript
  it('错误态 shared-empty 用 icon="warning-o" prop(本地组件真正支持,不再需要 vant 的 image=""/slot 绕过写法)', () => {
    const blocks = wxml.match(/<shared-empty[\s\S]*?<\/shared-empty>/g) ?? [];
    const errorBlock = blocks.find((b) => b.includes('errorMessage'));
    expect(errorBlock).toBeDefined();
    expect(errorBlock).not.toMatch(/image=""/);
    expect(errorBlock).not.toMatch(/slot="image"/);
    expect(errorBlock).toMatch(/icon="warning-o"/);
  });

  it('空订单列表 shared-empty 用 icon="orders-o" prop', () => {
    const blocks = wxml.match(/<shared-empty[\s\S]*?<\/shared-empty>/g) ?? [];
    const emptyBlock = blocks.find((b) => b.includes('还没有相关订单哦'));
    expect(emptyBlock).toBeDefined();
    expect(emptyBlock).not.toMatch(/image=""/);
    expect(emptyBlock).not.toMatch(/slot="image"/);
    expect(emptyBlock).toMatch(/icon="orders-o"/);
  });

  it('order-list.json 的 shared-empty 指向本地组件', () => {
    const parsed = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
    expect(parsed.usingComponents['shared-empty']).toBe('/src/shared/components/Empty/index');
  });
```

第 44-47 行现有的 `it('order-list.json 注册了 van-icon 组件', ...)` 保留不动。

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && TZ=UTC npx jest src/__tests__/icon-emoji-order-list.test.ts`
Expected: 新的 3 个 `it` FAIL

- [ ] **Step 3: 改 `pages-sub/order/order-list/order-list.wxml`**

第 43-53 行(错误态),原:
```wxml
  <!-- 错误状态 -->
  <view class="order-list__state" wx:elif="{{isError}}">
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
改为:
```wxml
  <!-- 错误状态 -->
  <view class="order-list__state" wx:elif="{{isError}}">
    <shared-empty
      icon="warning-o"
      message="{{errorMessage || '加载失败'}}"
      retry-text="重新加载"
      bind:retry="onRetry"
    ></shared-empty>
  </view>
```

第 55-64 行(空状态),原:
```wxml
  <!-- 空状态 -->
  <view class="order-list__state" wx:elif="{{filteredOrders.length === 0}}">
    <shared-empty
      image=""
      message="还没有相关订单哦"
      retry-text="去逛逛"
      bind:retry="onGoShopping"
    >
      <van-icon slot="image" name="orders-o" size="48px" />
    </shared-empty>
  </view>
```
改为:
```wxml
  <!-- 空状态 -->
  <view class="order-list__state" wx:elif="{{filteredOrders.length === 0}}">
    <shared-empty
      icon="orders-o"
      message="还没有相关订单哦"
      retry-text="去逛逛"
      bind:retry="onGoShopping"
    ></shared-empty>
  </view>
```

- [ ] **Step 4: 改 `pages-sub/order/order-list/order-list.json`**

原:
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
改为:
```json
{
  "navigationBarTitleText": "我的订单",
  "usingComponents": {
    "shared-empty": "/src/shared/components/Empty/index",
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
cd /Users/linbinghui/agent-work/seafood-miniapp/.claude/worktrees/mp-icon-emoji-replacement
git add frontend/pages-sub/order/order-list/order-list.wxml frontend/pages-sub/order/order-list/order-list.json frontend/src/__tests__/icon-emoji-order-list.test.ts
git commit -m "$(cat <<'EOF'
fix(mp): 订单列表页 shared-empty 改指向本地组件,message/retry 真正生效

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: 全量回归 + 收尾

**Files:**
- 无固定文件(纯验证)

**Interfaces:**
- Consumes: Task 1-5 的全部改动
- Produces: 无

- [ ] **Step 1: 跑全量前端测试套件**

Run: `cd frontend && TZ=UTC npx jest`
Expected: 全部 PASS,0 failures(尤其确认 `src/features/product/` 下依赖 `shared-empty` 组件的测试、以及 4 个页面各自的既有 wxml-contract/behavior 测试都还是绿的)

- [ ] **Step 2: 确认没有遗漏的 `image=""` / `slot="image"` 残留**

Run:
```bash
cd frontend && grep -rn 'image=""' pages/index/index.wxml pages/category/category.wxml pages/cart/cart.wxml pages-sub/order/order-list/order-list.wxml
```
Expected: 无输出(grep 无匹配,exit code 1)——确认 4 个页面的 vant 专用写法已经全部清干净
