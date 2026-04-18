# 海鲜商城 UI 美化设计文档

**日期**: 2026/04/19
**版本**: v1.0
**状态**: 已确认

---

## 1. 设计目标

为海鲜商城小程序和 Admin-UI 建立统一的**活力现代**设计语言，在两个平台间保持品牌一致性，同时各自适配使用场景：

- **微信小程序**：面向顾客，注重吸引力和购买转化
- **Admin-UI**：面向运营人员，注重效率和清晰度

---

## 2. 设计语言

### 色彩系统

| 角色 | 色值 | 用途 |
|------|------|------|
| Primary | `#FF6B6B` 珊瑚橙 | 主按钮、强调、CTA |
| Secondary | `#4ECDC4` 青碧 | 次要操作、图标、hover 态 |
| Accent | `#FFE66D` 暖黄 | 高亮徽章、价格标签 |
| Dark | `#1A1A2E` 深紫黑 | 侧边栏、标题文字 |
| Surface | `#FFFFFF` 纯白 | 卡片、输入框背景 |
| Background | `#F7F8FC` 淡灰白 | 页面背景 |
| Text Primary | `#2D3436` 深灰 | 正文 |
| Text Secondary | `#95A5A6` 中灰 | 次要文字 |

### 动效规范

- 过渡时长：300ms
- 缓动函数：`ease-out`
- 卡片悬浮：scale 1.02 + box-shadow 增强
- 按钮触摸：scale 0.96 + opacity 变化

### 圆角规范

- 卡片：`border-radius: 16rpx`（小程序）/ `border-radius: 12px`（Admin）
- 按钮：`border-radius: 8rpx`（小程序）/ `border-radius: 8px`（Admin）
- 输入框：`border-radius: 30rpx`（小程序搜索框）/ `border-radius: 8px`（Admin）
- 胶囊标签：`border-radius: 999px`

### 阴影规范

```css
/* 小程序卡片阴影 */
box-shadow: 0 4rpx 20rpx rgba(255, 107, 107, 0.12);

/* Admin 卡片阴影 */
box-shadow: 0 2px 12px rgba(26, 26, 46, 0.08);

/* 悬浮阴影（两者通用） */
box-shadow: 0 8rpx 30rpx rgba(255, 107, 107, 0.18);
```

---

## 3. 微信小程序改动

### 3.1 全局样式 (app.wxss)

更新 CSS 变量为新色彩系统，保留原有 safe-area 变量：

```css
page {
  --color-primary: #FF6B6B;
  --color-secondary: #4ECDC4;
  --color-accent: #FFE66D;
  --color-dark: #1A1A2E;
  --color-surface: #ffffff;
  --color-bg: #F7F8FC;
  --color-text: #2D3436;
  --color-text-secondary: #95A5A6;
  --color-border: #E8EAED;
  --color-divider: #F0F1F5;
  --color-price: #FF6B6B;
  /* ... safe-area 变量保留 ... */
}
```

### 3.2 首页 (pages/index/)

**搜索栏**
- 背景：`var(--color-surface)`
- 圆角：`30rpx`
- 内阴影：`inset 0 2rpx 8rpx rgba(0,0,0,0.06)`
- focus 边框：`2rpx solid var(--color-primary)`

**Banner 轮播**
- 圆角：`24rpx`（加大）
- 阴影：`0 8rpx 30rpx rgba(255, 107, 107, 0.2)`
- 渐变背景（替代纯色占位）：`linear-gradient(135deg, #FF6B6B 0%, #4ECDC4 100%)`

**分类入口**
- 图标圆形：`80rpx × 80rpx`，背景 `var(--color-bg)`
- hover 态：背景变为 `var(--color-primary)` 浅色，阴影加深
- 间距均匀，使用 flex + `space-around`

**商品卡片**
- 圆角：`16rpx`
- 阴影：`0 4rpx 20rpx rgba(255, 107, 107, 0.12)`
- hover/press：`transform: scale(0.98)` + 阴影减弱（按压反馈）
- 商品名称：`font-size: 28rpx`，超出省略
- 价格：`var(--color-price)` = `#FF6B6B`，`font-weight: 700`

**"加入购物车"按钮**
- 背景：`linear-gradient(135deg, #FF6B6B 0%, #FF8E8E 100%)`
- 圆角：`20rpx`
- 字体白色，触摸时 scale 0.96

### 3.3 分类页 (pages/category/)

- 分类网格：2 列布局，`gap: 20rpx`（用 margin 替代，微信小程序不支持 gap）
- 分类卡片：圆角 16rpx，轻阴影，图标居中
- 商品列表：与首页商品卡片样式统一

### 3.4 购物车页 (pages/cart/)

- 商品项卡片：统一商品卡片样式
- 底部结算栏：`var(--color-dark)` 背景，Primary 色结算按钮

### 3.5 个人中心 (pages/profile/)

- 顶部用户信息区：渐变背景 `#FF6B6B → #4ECDC4`
- 菜单项：左侧图标 Primary 色，右侧箭头灰色

---

## 4. Admin-UI 改动 (Vaadin)

### 4.1 全局样式

在 `MainLayout.java` 中统一管理头部和侧边栏样式，使用新色彩系统。

### 4.2 主布局 (MainLayout)

**头部导航栏**
- 背景：`linear-gradient(135deg, #1A1A2E 0%, #2D3452 100%)`
- 品牌文字白色，副标题透明度 0.7

**侧边栏**
- 背景：`var(--color-surface)` = `#FFFFFF`
- 导航项 hover：`background: rgba(255, 107, 107, 0.08)` + `border-left: 3px solid var(--color-primary)`
- 导航项 active：`background: rgba(255, 107, 107, 0.12)` + `color: var(--color-primary)`
- 圆角：`8px`，过渡：`all 0.2s ease`

### 4.3 仪表盘 (DashboardView)

**统计卡片**
- 背景：`#FFFFFF`
- 顶部边条：`4px solid var(--color-primary)`
- 阴影：`0 2px 12px rgba(26, 26, 46, 0.08)`
- 圆角：`12px`
- 数值文字：`font-size: 1.85rem`，`font-weight: 800`，`color: #1A1A2E`
- 标签文字：大写、间距 `letter-spacing: 0.08em`

**状态徽章**
- 背景：对应状态色 + `15%` 透明度（如 `#FF6B6B15`）
- 边框：`1px solid` 对应状态色 + `40%` 透明度
- 圆角：`10px`
- 字体：`font-weight: 600`

**进度条**
- 高度：`10px`，圆角：`6px`
- 颜色使用 Secondary (`#4ECDC4`)

### 4.4 商品列表 (ProductListView)

**工具栏按钮**
- 背景：`linear-gradient(135deg, #FF6B6B 0%, #FF8E8E 100%)`
- 圆角：`8px`
- hover：亮度提升 5%

**搜索框**
- 圆角：`8px`
- focus 边框：`2px solid var(--color-primary)`

**数据表格**
- 表头背景：`#F7F8FC`
- 斑马纹：奇数行 `#FAFBFC`
- hover 行：`rgba(78, 205, 196, 0.06)`

### 4.5 订单列表 (OrderListView)

- 筛选栏布局整齐，间距 `16px`
- 表格与商品列表样式统一

### 4.6 登录页 (LoginView)

- 背景渐变：`linear-gradient(135deg, #1A1A2E 0%, #2D3452 50%, #4ECDC4 100%)`
- 登录卡片：`backdrop-filter: blur(10px)` + `rgba(255,255,255,0.1)` 半透明白
- 圆角：`16px`
- 表单内边距：`40px`
- Vaadin Login Form 组件通过 i18n 定制中文文字

### 4.7 404 错误页 (NotFoundView)

- 与登录页背景统一风格
- 显示品牌 Logo 和返回首页链接

---

## 5. 实施顺序

建议按以下顺序实施：

1. **Phase 1** — 设计系统落地：CSS 变量（小程序 app.wxss）、Vaadin 样式常量（MainLayout）
2. **Phase 2** — 小程序全面美化：首页 → 分类页 → 购物车 → 个人中心
3. **Phase 3** — Admin-UI 全面美化：Dashboard → 商品/订单列表 → 登录页
4. **Phase 4** — 动效和细节：悬浮态、过渡动画、触摸反馈

---

## 6. 验收标准

- [ ] 小程序所有页面配色与设计系统一致
- [ ] Admin-UI 所有视图配色统一
- [ ] 按钮均有渐变背景和圆角
- [ ] 卡片均有阴影和圆角
- [ ] 悬浮/按压态有视觉反馈
- [ ] 状态徽章样式统一（胶囊形 + 浅色背景）
- [ ] 两个平台品牌调性一致（活力现代）
