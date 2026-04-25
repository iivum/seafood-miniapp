# 设计系统规范 (Design System)

> **用途**: AI 编程助手的 UI/UX 实现准则
> **范围**: 微信小程序 + Admin UI
> **约束**: 所有颜色/间距必须用 CSS 变量，禁止硬编码

---

## 速查表

| 场景 | CSS 变量 | 禁止 |
|------|----------|------|
| 主色调 | `--color-primary` | `#hex` |
| 底部安全区 | `--safe-area-bottom` | 固定 `padding-bottom: xxxrpx` |
| Flex 间距 | `margin-left` | `gap` |
| 固定底部栏高度 | `--fixed-bar-height` | `height: 100rpx` |

---

## 微信小程序设计系统

### 1. 设计令牌 (必须使用)

#### 颜色令牌

```css
page {
  --color-primary: #1e3a5f;      /* 主色-Navy */
  --color-accent: #e07a5f;        /* 强调-Coral */
  --color-success: #38a89d;       /* 成功 */
  --color-price: #c45c3e;          /* 价格 */
  --color-bg: #faf8f5;             /* 背景 */
  --color-bg-subtle: #f5f3ef;     /* 背景-浅 */
  --color-text: #2d3748;           /* 文字 */
  --color-text-secondary: #718096;
  --color-text-muted: #a0aec0;
  --color-border: #e2e0dc;
  --color-divider: #edebe7;
}
```

**规则**: 所有颜色必须用 `var(--color-*)`，禁止硬编码 `#hex`

#### 安全区域令牌

```css
page {
  --safe-area-top: env(safe-area-inset-top);
  --safe-area-bottom: env(safe-area-inset-bottom);
  --safe-area-left: env(safe-area-inset-left);
  --safe-area-right: env(safe-area-inset-right);
  --fixed-bar-height: 100rpx;
  --fixed-bar-height-lg: 120rpx;
}
```

### 2. 安全区域适配

**规则**: 固定底部/顶部元素必须适配安全区域

| 元素类型 | 正确写法 | 错误写法 |
|---------|---------|---------|
| 底部固定栏 | `padding-bottom: var(--safe-area-bottom)` | `bottom: 0` |
| 内容容器 | `padding-bottom: calc(140rpx + var(--safe-area-bottom))` | `padding-bottom: 120rpx` |
| 固定按钮 | `bottom: calc(40rpx + var(--safe-area-bottom))` | `bottom: 40rpx` |

#### 安全区域工具类

```css
.safe-area-top { padding-top: var(--safe-area-top); }
.safe-area-bottom { padding-bottom: var(--safe-area-bottom); }
.safe-area-bottom-fixed { padding-bottom: calc(var(--fixed-bar-height) + var(--safe-area-bottom)); }
.safe-area-bottom-content { padding-bottom: calc(var(--fixed-bar-height) + var(--safe-area-bottom) + 20rpx); }
.safe-area-left { padding-left: var(--safe-area-left); }
.safe-area-right { padding-right: var(--safe-area-right); }
```

### 3. 布局规则

#### Flex 替代 Gap

微信小程序 **不支持** `gap` 属性：

```css
/* ❌ 错误 */
.product-meta { display: flex; gap: 10rpx; }

/* ✅ 正确 */
.product-meta { display: flex; flex-wrap: wrap; }
.product-meta > .item + .item { margin-left: 10rpx; }
```

#### Grid 布局

`grid-template-columns` 现代客户端支持，需测试兼容性：

```css
.action-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20rpx; }
```

### 4. 样式规范

#### 禁止写法

```css
/* 禁止: 硬编码颜色 */
color: #ff4d4f;

/* 禁止: 非语义化选择器 */
.address-section > view > text { }

/* 禁止: 非标准属性 */
gap: 20rpx;

/* 禁止: 固定高度 + overflow */
height: 100vh; overflow: auto;
```

#### 推荐写法

```css
/* ✅ 使用设计变量 */
color: var(--color-price);

/* ✅ 语义化类名 */
.product-card, .order-item, .cart-footer;

/* ✅ min-height 配合滚动 */
.container { min-height: 100vh; padding-bottom: calc(120rpx + var(--safe-area-bottom)); }
```

### 5. 组件模板

#### 卡片

```css
.card {
  background-color: var(--color-surface);
  border-radius: 12rpx;
  margin: 20rpx;
  padding: 20rpx;
  box-shadow: 0 2rpx 10rpx rgba(30, 58, 95, 0.08);
  border: 1rpx solid var(--color-border);
}
```

#### 固定底部栏

```css
.fixed-footer {
  position: fixed; bottom: 0; left: 0; right: 0;
  height: var(--fixed-bar-height);
  background-color: var(--color-surface);
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 30rpx;
  padding-bottom: var(--safe-area-bottom);
  box-shadow: 0 -4rpx 20rpx rgba(30, 58, 95, 0.1);
  border-top: 1rpx solid var(--color-border);
}
```

#### 空状态

```css
.empty-state {
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  padding-top: 200rpx;
  color: var(--color-text-muted);
}
```

### 6. 异形屏适配

| 设备类型 | 代表机型 | 适配重点 |
|---------|---------|---------|
| iPhone 无刘海 | iPhone SE, 8 | 基准布局 |
| iPhone 刘海 | iPhone 12-15 | 顶部安全区 + Home Indicator |
| iPhone Dynamic Island | iPhone 14 Pro+ | 状态栏 + Dynamic Island |
| Android 挖孔屏 | Samsung S21, 小米 12 | 居中/角落摄像头 |
| Android 刘海屏 | 华为 Mate | 狭长刘海 |
| Android 无挖孔 | 入门机型 | 默认安全区域 |

`app.json` 配置：

```json
{ "safeArea": { "left": "resizable", "right": "resizable", "top": "resizable", "bottom": "resizable" } }
```

---

## Admin UI 设计系统

**技术栈**: Vue 3 + Element Plus + Pinia + Vue Router 4.x
**位置**: `frontend/admin-design/` + `backend/admin-ui/vue/`

### 1. 设计令牌

#### 颜色令牌 (`frontend/admin-design/tokens.json`)

| 令牌 | 用途 | 色值 |
|-----|------|------|
| `sidebar.bg` | 侧边栏背景 | `#1A1A2E` |
| `sidebar.active` | 激活状态 | `#2A4D8C` |
| `accent.teal` | 强调色-青 | `#4ECDC4` |
| `accent.coral` | 强调色-珊瑚 | `#FF6B6B` |
| `content.bg` | 内容区背景 | `#F7F8FC` |
| `content.surface` | 卡片表面 | `#FFFFFF` |

#### Element Plus 主题变量 (`frontend/admin-design/element-theme.scss`)

```scss
--el-color-primary: #3D66A8;
--el-color-success: #10B981;
--el-color-warning: #F59E0B;
--el-color-danger: #EF4444;
```

### 2. 布局规范

| 规范 | 值 |
|------|-----|
| 侧边栏宽度 | 240px (深色 `#1A1A2E`) |
| 内容区内边距 | 32px |
| 卡片圆角 | 12px |
| 按钮圆角 | 8px |
| 卡片阴影 | `0 4px 6px -1px rgba(26, 26, 46, 0.07)` |

### 3. 组件样式

#### 按钮

```css
.el-button { border-radius: 8px; transition: transform 150ms ease; }
.el-button:hover { transform: scale(1.02); }
```

#### 表格

```css
.el-table { border-radius: 12px; overflow: hidden; }
.el-table__row:hover { background-color: #FAFAFA !important; }
```

---

## 工具模块

### safe-area.js

位置: `frontend/utils/safe-area.js`

| 函数 | 说明 | 返回值 |
|-----|------|------|
| `getSafeArea()` | 获取安全区域 | `{top, bottom, left, right}` |
| `getStatusBarHeight()` | 状态栏高度 | `number` (px) |
| `isNotchedDevice()` | 检测异形屏 | `boolean` |
| `getScreenSize()` | 屏幕尺寸 | `{width, height}` |
| `getBottomSafeAreaHeight()` | 底部安全区 | `number` (px) |
| `px2rpx()` | px→rpx | `number` |
| `rpx2px()` | rpx→px | `number` |

---

## 检查清单

### 开发前检查

- [ ] 确认使用 CSS 变量而非硬编码颜色
- [ ] 确认固定底部栏添加 `padding-bottom: var(--safe-area-bottom)`
- [ ] 确认内容容器留出底部固定栏空间
- [ ] 确认使用 Flex + margin 替代 `gap`
- [ ] 确认类名语义化 (`.card`, `.footer` 非 `.box1`)

### 微信小程序专项

- [ ] `app.wxss` 中定义所有颜色变量
- [ ] 固定元素使用 `env(safe-area-inset-*)`
- [ ] 内容区 `padding-bottom` 动态计算
- [ ] Grid 布局通过兼容性测试

### Admin UI 专项

- [ ] 使用 Element Plus 组件而非原生 HTML
- [ ] 颜色使用 `--sidebar-bg`, `--accent-teal` 等 CSS 变量
- [ ] 圆角: 按钮 8px / 卡片 12px
- [ ] 表格使用斑马纹 `#FAFAFA`
- [ ] 弹窗使用 `el-dialog` 组件

---

*本规范为海鲜商城专用，与 `frontend/admin-design/tokens.json` 保持同步。*
