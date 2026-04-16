# 微信小程序设计准则

本文档为海鲜商城小程序提供 UI/UX 设计规范和实现准则。

---

## 设计系统

### 设计令牌 (Design Tokens)

所有颜色、间距、字体大小必须使用 CSS 变量，定义在 `app.wxss` 的 `page` 块中。

#### 颜色系统

```css
page {
  /* 主色调 - Navy */
  --color-primary: #1e3a5f;

  /* 强调色 - Coral */
  --color-accent: #e07a5f;

  /* 成功色 */
  --color-success: #38a89d;

  /* 价格色 */
  --color-price: #c45c3e;

  /* 背景色 */
  --color-bg: #faf8f5;
  --color-bg-subtle: #f5f3ef;

  /* 文字色 */
  --color-text: #2d3748;
  --color-text-secondary: #718096;
  --color-text-muted: #a0aec0;

  /* 边框/分割线 */
  --color-border: #e2e0dc;
  --color-divider: #edebe7;
}
```

**使用规则**：
- 所有页面必须使用 CSS 变量，禁止硬编码颜色值
- 颜色语义化命名：`--color-*` 而非 `#hex`
- 主题色变更只需修改 `app.wxss` 一处

#### 安全区域变量

```css
page {
  /* 安全区域 - env() 由微信小程序注入 */
  --safe-area-top: env(safe-area-inset-top);
  --safe-area-bottom: env(safe-area-inset-bottom);
  --safe-area-left: env(safe-area-inset-left);
  --safe-area-right: env(safe-area-inset-right);

  /* 固定栏高度 */
  --fixed-bar-height: 100rpx;
  --fixed-bar-height-lg: 120rpx;
}
```

#### 安全区域工具类

```css
.safe-area-top { padding-top: var(--safe-area-top); }
.safe-area-bottom { padding-bottom: var(--safe-area-bottom); }
.safe-area-bottom-fixed { padding-bottom: calc(var(--fixed-bar-height) + var(--safe-area-bottom)); }
.safe-area-bottom-content { padding-bottom: calc(var(--fixed-bar-height) + var(--safe-area-bottom) + 20rpx); }
.safe-area-left { padding-left: var(--safe-area-left); }
.safe-area-right { padding-right: var(--safe-area-right); }
```

---

## 布局规则

### 1. 安全区域适配

**所有固定在底部或顶部的元素必须适配安全区域**：

```css
/* 错误示例 */
.footer {
  position: fixed;
  bottom: 0;
}

/* 正确示例 */
.footer {
  position: fixed;
  bottom: 0;
  padding-bottom: var(--safe-area-bottom);
  height: var(--fixed-bar-height);
}
```

**内容容器需要留出底部固定栏的空间**：

```css
/* 错误示例 - 底部内容被遮挡 */
.container {
  padding-bottom: 120rpx;
}

/* 正确示例 - 动态计算安全区域 */
.container {
  padding-bottom: calc(140rpx + var(--safe-area-bottom));
}
```

**需要适配的页面元素**：
| 元素类型 | 示例 | 适配方式 |
|---------|------|---------|
| 底部 Tab Bar | 购物车结算栏 | `padding-bottom: var(--safe-area-bottom)` |
| 固定按钮 | 添加地址按钮 | `bottom: calc(40rpx + var(--safe-area-bottom))` |
| 模态框 | 商品管理弹窗 | `modal-content` 底部添加安全区域 |
| 全屏遮罩 | 登录加载遮罩 | 确保覆盖状态栏区域 |

### 2. 固定底部栏规范

```css
.footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: var(--fixed-bar-height);
  background-color: var(--color-surface);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 30rpx;
  padding-bottom: var(--safe-area-bottom);
  box-shadow: 0 -4rpx 20rpx rgba(30, 58, 95, 0.1);
  border-top: 1rpx solid var(--color-border);
}
```

### 3. Flex 布局替代 Gap

微信小程序 **不支持** `gap` 属性，使用 Flex + margin 替代：

```css
/* 错误示例 */
.product-meta {
  display: flex;
  gap: 10rpx;
}

/* 正确示例 */
.product-meta {
  display: flex;
  flex-wrap: wrap;
}

.product-meta > .product-price + .product-stock,
.product-meta > .product-stock + .product-category {
  margin-left: 10rpx;
}
```

### 4. Grid 布局

`grid-template-columns` 在现代微信小程序客户端支持，但需测试兼容性：

```css
/* 可用的 Grid 布局 */
.action-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20rpx;
}
```

---

## 响应式布局

### 异形屏适配设备

| 设备类型 | 代表机型 | 适配重点 |
|---------|---------|---------|
| iPhone 无刘海 | iPhone SE, iPhone 8 | 基准布局 |
| iPhone 刘海 | iPhone 12-15 | 顶部安全区 + 底部 Home Indicator |
| iPhone Dynamic Island | iPhone 14 Pro+ | 状态栏区域 + Dynamic Island |
| Android 挖孔屏 | Samsung S21, 小米 12 | 居中/角落摄像头处理 |
| Android 刘海屏 | 华为 Mate 系列 | 狭长刘海处理 |
| Android 无挖孔 | 入门机型 | 默认安全区域 |

### app.json 安全区域配置

```json
{
  "safeArea": {
    "left": "resizable",
    "right": "resizable",
    "top": "resizable",
    "bottom": "resizable"
  }
}
```

---

## 样式编写规范

### 文件组织

```
pages/
├── index/
│   ├── index.wxml      # 结构
│   ├── index.wxss       # 样式
│   └── index.js         # 逻辑
├── cart/
├── category/
└── profile/
```

### CSS 变量命名

| 变量 | 用途 | 示例值 |
|-----|------|-------|
| `--color-primary` | 主色调 | `#1e3a5f` |
| `--color-accent` | 强调色 | `#e07a5f` |
| `--color-success` | 成功色 | `#38a89d` |
| `--color-price` | 价格色 | `#c45c3e` |
| `--color-bg` | 背景色 | `#faf8f5` |
| `--color-surface` | 卡片/容器背景 | `#ffffff` |
| `--color-text` | 主文字 | `#2d3748` |
| `--color-text-secondary` | 次要文字 | `#718096` |
| `--color-text-muted` | 弱化文字 | `#a0aec0` |
| `--color-border` | 边框色 | `#e2e0dc` |
| `--color-divider` | 分割线色 | `#edebe7` |

### 避免的写法

```css
/* 禁止：硬编码颜色 */
color: #ff4d4f;
background-color: #f5f5f5;

/* 禁止：非语义化选择器 */
.address-section > view > text { }

/* 禁止：非标准 CSS 属性 */
gap: 20rpx;  /* 小程序不支持 */

/* 禁止：固定高度配合内容滚动 */
height: 100vh;
overflow: auto;
```

### 推荐写法

```css
/* 推荐：使用设计变量 */
color: var(--color-price);
background-color: var(--color-bg-subtle);

/* 推荐：语义化类名 */
.address-info, .order-card, .product-item;

/* 推荐：使用 flex 替代 gap */
display: flex;
.item + .item { margin-left: 20rpx; }

/* 推荐：滚动区域使用 min-height */
.container {
  min-height: 100vh;
  padding-bottom: calc(120rpx + var(--safe-area-bottom));
}
```

---

## 常见组件样式模板

### 卡片组件

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

### 按钮组件

```css
.btn {
  background-color: var(--color-primary);
  color: #fff;
  border-radius: 8rpx;
  padding: 20rpx;
  text-align: center;
}

.btn-accent {
  background-color: var(--color-accent);
}

.btn-success {
  background-color: var(--color-success);
}
```

### 固定底部栏

```css
.fixed-footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: var(--fixed-bar-height);
  background-color: var(--color-surface);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 30rpx;
  padding-bottom: var(--safe-area-bottom);
  box-shadow: 0 -4rpx 20rpx rgba(30, 58, 95, 0.1);
  border-top: 1rpx solid var(--color-border);
}
```

### 空状态

```css
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-top: 200rpx;
  color: var(--color-text-muted);
}

.empty-state text {
  margin: 30rpx 0;
}
```

---

## 工具模块

### safe-area.js

位置：`frontend/utils/safe-area.js`

提供以下函数：

| 函数 | 说明 | 返回值 |
|-----|------|-------|
| `getSafeArea()` | 获取安全区域 | `{top, bottom, left, right}` |
| `getStatusBarHeight()` | 获取状态栏高度 | `number` (px) |
| `isNotchedDevice()` | 检测是否为异形屏 | `boolean` |
| `getScreenSize()` | 获取屏幕尺寸 | `{width, height}` |
| `getBottomSafeAreaHeight()` | 获取底部安全区高度 | `number` (px) |
| `px2rpx()` | px 转 rpx | `number` |
| `rpx2px()` | rpx 转 px | `number` |

---

## 检查清单

新增或修改页面样式时：

- [ ] 是否使用了 CSS 变量而非硬编码颜色？
- [ ] 固定底部栏是否添加了 `padding-bottom: var(--safe-area-bottom)`？
- [ ] 内容容器是否留出了底部固定栏的空间？
- [ ] 是否使用了 Flex 替代不支持的 `gap` 属性？
- [ ] 类名是否语义化（如 `.card`, `.footer` 而非 `.box1`）？
- [ ] 颜色是否使用了设计令牌（`--color-*`）？

---

*本准则为海鲜商城小程序专用，与项目 design tokens 保持同步更新。*
