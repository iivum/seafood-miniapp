# 设计系统规范 (Design System v2)

> **用途**: AI 编程助手的 UI/UX 实现准则
> **范围**: 微信小程序 + Admin UI
> **约束**: 所有颜色/间距必须用 v2 CSS 变量,禁止硬编码 hex / 嵌套 token
> **OpenSpec**: `openspec/changes/v2-visual-redesign/specs/visual-design-system/`

---

## 速查表

| 场景 | v2 CSS 变量 | v1 旧写法(弃用) | 禁止 |
|------|-------------|------------------|------|
| 主色 / accent | `--accent` | `--color-primary` | `#FF6B6B` / `#1e3a5f` |
| 表面 / surface | `--surface` | `--color-surface` | `#fff` |
| 背景 / bg | `--bg` | `--color-bg` | `#faf8f5` |
| 文字 / fg | `--fg` | `--color-text` | `#2D3436` |
| 弱文字 / muted | `--muted` | `--color-text-secondary` | `#6b7280` |
| 弱化文字 / soft | `--soft` | `--color-text-muted` | `#a0aec0` |
| 边框 | `--border` / `--border-strong` | `--color-border` | `#E5E7EB` |
| 强调深 | `--accent-strong` | `--color-primary-soft` | — |
| 强调背景 | `--accent-soft` | — | — |
| 状态 success | `--success` + `--success-soft` | `--color-success` | — |
| 状态 warning | `--warning` + `--warning-soft` | `--color-warning` | — |
| 状态 error | `--error` + `--error-soft` | `--color-error` | — |
| 状态 info | `--info` + `--info-soft` | `--color-info` | — |
| pill 圆角 | `--radius-pill` | `--radius-full` | — |
| 蓝调阴影 | `--shadow-sm` / `--shadow-md` / `--shadow-lg` | `rgba(0,0,0,0.05)` | 红色阴影 |
| 衬线 display | `--font-display` (Fraunces) | system serif | system-ui |
| 正文字体 | `--font-body` (Inter Tight) | — | -apple-system |
| 等宽 mono | `--font-mono` (Geist Mono) | — | ui-monospace |
| 底部安全区 | `--safe-area-bottom` | — | 固定 `padding-bottom` |

> **关键差异**:v1 用嵌套命名(`--color-primary` / `--color-price`),v2 用扁平语义(`--accent` / `--surface` / `--shadow-md`)。所有 v1 引用必须按「主语义重新映射」,不能 1:1 改名。

---

## 1. 设计令牌(Design Tokens)

**单一源**:`docs/redesign/tokens.json` — 19 个颜色 + 3 个字体族 + 6 个圆角 + 3 个阴影 + 6 条 posture。
**派生链**:
```
docs/redesign/tokens.json  (唯一 source of truth)
       ↓ scripts/build-tokens.js (无依赖,Node.js)
       ├──→ frontend/src/shared/tokens/tokens.wxss  (mp 端 WXSS 变量)
       └──→ admin-ui/src/shared/tokens/tokens.tailwind.ts  (admin 端 Tailwind theme)
```
**Parity 守门**:`scripts/__tests__/build-tokens.test.js` 跑 9/9 单元测试 + `.github/workflows/ci.yml` 的 `tokens` job 守两端 key 1:1 对应。

### 1.1 颜色(19 个,全部 OKLch)

| Key | 值 | 用途 |
|---|---|---|
| `--bg` | `oklch(99% 0.006 60)` | 页面背景(蛋壳) |
| `--surface` | `oklch(100% 0 0)` | 卡片 / sheet 表面 |
| `--fg` | `oklch(22% 0.02 40)` | 主文字(墨) |
| `--muted` | `oklch(50% 0.015 40)` | 次文字(雾) |
| `--soft` | `oklch(70% 0.012 40)` | 弱化文字 / 占位 |
| `--border` | `oklch(91% 0.008 40)` | 1rpx 分隔线 |
| `--border-strong` | `oklch(85% 0.012 40)` | 描边 / focus 环 |
| `--accent` | `oklch(64% 0.16 38)` | **主色:烤虾橙**(perceptual approx `#C9744A` 用于 app.json nav bar) |
| `--accent-soft` | `oklch(96% 0.05 40)` | 强调背景 / Empty 图标容器 |
| `--accent-strong` | `oklch(52% 0.18 40)` | ghost 按钮 / 强调文字 |
| `--accent-deep` | `oklch(35% 0.10 38)` | REFUNDED 状态色 |
| `--success` | `oklch(58% 0.12 155)` | 成功状态 |
| `--warning` | `oklch(72% 0.15 70)` | 警告 / 待付款 |
| `--error` | `oklch(50% 0.20 15)` | 错误 / 退款 |
| `--info` | `oklch(58% 0.10 220)` | 信息 / 已付款 |
| `--success-soft` / `--warning-soft` / `--error-soft` / `--info-soft` | OKLch 95% variants | 状态 pill 背景 |

> 微信 WebView(8.0+)支持 OKLch;老客户端自动 fallback 到 system 色。
> `app.json` 的 `navigationBarBackgroundColor` / `tabBar.*` **必须** 用 hex 副本(微信原生 nav bar 不支持 CSS 变量),用 `oklch(64% 0.16 38)` 的 perceptual approx `#C9744A`。

### 1.2 字体(3 个族,子集化)

```css
--font-display: Fraunces, 'Source Serif Pro', 'Iowan Old Style', Georgia, serif;
--font-body:    Inter Tight, -apple-system, BlinkMacSystemFont, 'SF Pro Text', system-ui, sans-serif;
--font-mono:    Geist Mono, 'IBM Plex Mono', ui-monospace, Menlo, monospace;
```

**加载策略**(决策 3):mp 端 `frontend/src/shared/tokens/fonts.wxss` 用 `@font-face` 引 woff2 子集;admin 端用 `fontsource` npm 包。子集范围 = 数字 + 英文 + 常用汉字 ~500 字(1d 字频统计 spike)。

### 1.3 圆角(6 档)

```
--radius-sm: 4px     内嵌 / stepper 按钮
--radius-md: 6px     旧的兼容值(几乎不用)
--radius-lg: 10px    卡片小元素
--radius-xl: 14px    卡片(主用)
--radius-2xl: 16px   大卡片 / sheet
--radius-3xl: 18px   弹窗
--radius-pill: 9999px 主按钮 / chip / status 标签
```

### 1.4 阴影(3 档,蓝调,非红调)

```
--shadow-sm: 0 1px 2px  oklch(22% 0.02 40 / 0.05)
--shadow-md: 0 4px 14px oklch(22% 0.02 40 / 0.07)
--shadow-lg: 0 24px 60px oklch(22% 0.02 40 / 0.10)
```

阴影颜色取自 `--fg` 的 OKLch,带 alpha 调制 — 这是「蓝调阴影」posture 的具体实现。

---

## 2. 六条设计 posture(团队共识)

> 摘自 `docs/redesign/tokens.json § postures`,design owner 拍板 accept,写入 `docs/DESIGN.md` 供全员 review 时引用。

### Posture 1:Primary action 是实色 accent,不是 135° 渐变

```css
/* ❌ 错:v1 渐变按钮 */
.btn { background: linear-gradient(135deg, #FF6B6B 0%, #FF8E8E 100%); }

/* ✅ 对:v2 实色 accent + 蓝调阴影 */
.btn { background: var(--accent); box-shadow: var(--shadow-md); }
```

### Posture 2:Shadow 是蓝调中性,不是红色

```css
/* ❌ 错:红调阴影 */
.card { box-shadow: 0 4rpx 20rpx rgba(255, 107, 107, 0.12); }

/* ✅ 对:蓝调阴影 */
.card { box-shadow: 0 4rpx 14rpx oklch(22% 0.02 40 / 0.07); /* == --shadow-md */ }
```

### Posture 3:价格用 ink 或 accent,不抢视觉权重

```css
/* ❌ 错:价格用粗体大色块,跟标题抢戏 */
.price { font-size: 36px; color: var(--color-price); font-weight: 800; }

/* ✅ 对:衬线 Fraunces + accent,¥ 前缀缩 */
.price { font-family: var(--font-display); color: var(--accent); font-weight: 600; }
.price::before { content: '¥'; font-size: 0.7em; font-weight: 500; }
```

### Posture 4:衬线 display 表达「鲜 / 真」,不堆潮流

```css
/* ❌ 错:全 sans,丢品牌差异 */
.title { font-family: -apple-system, sans-serif; }

/* ✅ 对:衬线 display 用于价格 / Empty / 大标题 */
.title { font-family: var(--font-display); }
```

### Posture 5:整屏 accent 至多 2 处,不刷屏

- 1 处主操作(CTA 按钮)
- 1 处价格 / Empty 容器 / chip 选中态

任一处再出现 accent 视为违反 posture。**review 时一眼可辨**。

### Posture 6:State 色只用于 state,不混入品牌叙事

```css
/* ❌ 错:warning 用作品牌主色(本来是状态色) */
.brand { color: var(--warning); }

/* ✅ 对:warning 只用于「待付款」标签 */
.status--PENDING { color: var(--warning); background: var(--warning-soft); }
```

---

## 3. 安全区域(不变,沿用 v1)

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

工具类:`.safe-area-top` / `.safe-area-bottom` / `.safe-area-bottom-fixed` / `.safe-area-bottom-content` / `.safe-area-left` / `.safe-area-right`。
**规则**:固定底部/顶部元素必须适配安全区,见 v1 表里正确 vs 错误写法。

---

## 4. 布局规则(不变)

微信小程序 **不支持** `gap` 属性 — 用 `flex` + `margin-left/+view { margin-left: 10rpx }` 替代。
`grid-template-columns` 现代客户端支持,但需要单独兼容性测试。

---

## 5. 组件模板(全部 v2)

### 5.1 卡片

```css
.card {
  background-color: var(--surface);
  border-radius: var(--radius-xl);  /* 14px */
  margin: 20rpx;
  padding: 24rpx;
  box-shadow: 0 2rpx 10rpx oklch(22% 0.02 40 / 0.05);  /* == --shadow-sm */
}
```

### 5.2 主按钮(pill + 实色 accent)

```css
.btn-primary {
  background: var(--accent);
  color: var(--surface);
  border-radius: var(--radius-pill);
  padding: 20rpx 36rpx;
  font-weight: 600;
  font-family: var(--font-body);
  box-shadow: 0 6rpx 20rpx oklch(22% 0.02 40 / 0.08);
}
```

### 5.3 状态 pill 标签

```css
.status--PENDING   { color: var(--warning);   background: var(--warning-soft);   }
.status--PAID      { color: var(--info);      background: var(--info-soft);      }
.status--SHIPPED   { color: var(--accent-strong); background: var(--accent-soft); }
.status--COMPLETED { color: var(--success);   background: var(--success-soft);   }
.status--CANCELLED { color: var(--soft);      background: var(--bg);             }
.status--REFUNDING { color: var(--error);     background: var(--error-soft);     }
.status--REFUNDED  { color: var(--accent-deep); background: var(--bg);           }
```

### 5.4 衬线价格(Fraunces)

```css
.price {
  font-family: var(--font-display);
  color: var(--accent);
  font-size: 36rpx;
  font-weight: 600;
  letter-spacing: 0.4rpx;
}
.price::before { content: '¥'; font-size: 0.7em; font-weight: 500; }
```

### 5.5 死交互 / active 反馈

```css
[data-action]:active, [data-toggle]:active {
  /* 0.18s 闪 + 0.6s fade-out,见 task 3.16 */
  transform: scale(0.97);
  box-shadow: 0 2rpx 8rpx oklch(22% 0.02 40 / 0.06);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}
```

---

## 6. Admin UI 设计系统

> **2026-06-13 决策启动**:admin-ui 是 **单卖家内部运营**,不做外部商家接入 / 多 seller / 自助门户 / 结算分账。
> 技术栈:React 18 + shadcn/ui + Vite,Tailwind 3 主题由 `admin-ui/src/shared/tokens/tokens.tailwind.ts` 注入(由 `docs/redesign/tokens.json` 派生)。
> 部署:第 3 独立 image(nginx:1.27-alpine serve),`docker-compose.yml` 加 `admin-ui` 服务(详见 task 5.1-5.3)。

admin 端使用 v2 flat class(`text-accent` / `text-muted` / `text-error` / `bg-bg` / `bg-surface` 等),**不**沿用 v1 嵌套 class(`text-primary-500` / `text-app-muted` / `text-feedback-error`)。
迁移期:1.22 标 BLOCKED,需独立「admin-ui v1→v2 迁移 PR」逐 page 改写。

---

## 7. 工具模块(沿用)

`frontend/utils/safe-area.js` — `getSafeArea()` / `getStatusBarHeight()` / `isNotchedDevice()` / `getScreenSize()` / `getBottomSafeAreaHeight()` / `px2rpx()` / `rpx2px()`,功能不变。

---

## 8. 检查清单

### 8.1 开发前(必查)

- [ ] 确认使用 v2 CSS 变量(`--accent` / `--surface` / `--shadow-md` 等)而非 v1
- [ ] 确认引用通过 `frontend/src/shared/tokens/tokens.wxss`(mp)或 `admin-ui/src/shared/tokens/tokens.tailwind.ts`(admin)
- [ ] 确认固定底部栏加 `padding-bottom: var(--safe-area-bottom)`
- [ ] 确认内容容器留出底部固定栏空间
- [ ] 确认用 Flex + margin 替代 `gap`
- [ ] 确认类名语义化(`.card` / `.order-item-row` / `.cart-item-row`)

### 8.2 视觉契约(必查)

- [ ] **posture 1**:主操作是实色 accent,无 `linear-gradient(135deg, ...)`
- [ ] **posture 2**:阴影 OKLch 蓝调,无 `rgba(0,0,0,0.05)` 或 `rgba(255,107,107,...)`
- [ ] **posture 3**:价格用 `--font-display` + `::before` ¥ 缩字
- [ ] **posture 4**:衬线 display 用 Fraunces,无 `font-family: -apple-system` 写大标题
- [ ] **posture 5**:整屏 accent ≤ 2 处(主按钮 + 1 处状态/价格/Empty)
- [ ] **posture 6**:state 色只用于 state pill / 状态文字

### 8.3 微信小程序专项

- [ ] `app.wxss` 顶部 `@import '/shared/tokens/tokens.wxss'` + `@import '/shared/tokens/fonts.wxss'`
- [ ] 固定元素使用 `env(safe-area-inset-*)`
- [ ] 内容区 `padding-bottom` 动态计算
- [ ] Grid 布局通过兼容性测试
- [ ] `app.json` 的 nav/tab bar hex 与 `--accent` OKLch 保持 perceptual 一致(目前 `#C9744A`)

### 8.4 Admin UI 专项

- [ ] Tailwind class 走 v2 flat 命名(`text-accent` / `bg-bg` / `text-muted`)
- [ ] 状态色用 v2(`bg-success-soft` / `text-error`),非 v1 嵌套
- [ ] shadcn/ui 组件套而非原生 HTML
- [ ] 字体走 `fontsource` npm(决策 3)
- [ ] httpOnly Cookie 鉴权,Sprint 1 启动就走

### 8.5 v1 → v2 迁移期(本次 change 软切完成,但未硬切)

- [ ] 7 个组件 v1 `--color-*` 全部替换为 v2 `--*`(本 change 1.6-1.12 已完成)
- [ ] v1 `--space-*` 仍保留(暂未替换,见 `=== TOKENS:BEGIN ===` 块)
- [ ] admin-ui 6 个 page v1→v2 迁移(1.22 BLOCKED,待独立 PR)
- [ ] 主线「硬切」决策点:Sprint 1 末由 design owner 拍板,删除 v1 块

---

*本规范为海鲜商城专用,token 单一源 `docs/redesign/tokens.json`,与 `openspec/changes/v2-visual-redesign/` 6 份新 spec 保持同步。*
