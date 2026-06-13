# 03 · OD 设计系统(11 token + 3 字体 + 6 圆角 + 3 阴影)

> 源:Open Design 项目 `686e3434-0233-451e-9c99-debee025a336/index.html` 第一节 "Design system"
> + 各 mp-*.html 屏 `:root` 块。本文件把分散在 9 个 HTML 文件的 token 收敛到一张表。

---

## 1. 色彩 Token(11 个)

| # | 中文名 | OD 命名 | OKLch 值 | 角色 | 出现频次 |
|---|---|---|---|---|---|
| 1 | 蛋壳 | `--bg` | `oklch(99% 0.006 60)` | 背景 / 卡片底 | 全屏 |
| 2 | 雪面 | `--surface` | `oklch(100% 0 0)` | 卡片表面 | 卡片 |
| 3 | 暖墨 | `--fg` | `oklch(22% 0.02 40)` | 正文 / 标题 / tab active 文字 | 全屏 |
| 4 | 雾 | `--muted` | `oklch(50% 0.015 40)` | 次要文字 / 数字 badge | 标签 / 副信息 |
| 5 | 淡雾 | `--soft` | `oklch(70% 0.012 40)` | placeholder / 弱化 | placeholder |
| 6 | 边线 | `--border` | `oklch(91% 0.008 40)` | 卡片 / 输入框 border | 卡片 |
| 7 | 边线强 | `--border-strong` | `oklch(85% 0.012 40)` | hover 态 border | hover |
| 8 | 烤虾橙 | `--accent` | `oklch(64% 0.16 38)` | **主操作** / 强调 | 主按钮 / 加号 / 价格 / tab active dot |
| 9 | 烤虾淡 | `--accent-soft` | `oklch(96% 0.05 40)` | accent 软底 | chips bg / 分类 icon 底 |
| 10 | 烤虾深 | `--accent-strong` | `oklch(52% 0.18 40)` | hover 态 accent | hover |
| 11 | 烤虾深2 | `--accent-deep` | `oklch(35% 0.10 38)` | hero banner 暗背景 | hero bg |

### 状态色(4 个,只用于状态不参与品牌叙事)

| # | 中文名 | 命名 | OKLch |
|---|---|---|---|
| 12 | 海带绿 | `--success` | `oklch(58% 0.12 155)` |
| 13 | 沙金 | `--warning` | `oklch(72% 0.15 70)` |
| 14 | 朱砂 | `--error` | `oklch(50% 0.20 15)` |
| 15 | 潮汐蓝 | `--info` | `oklch(58% 0.10 220)` |

(每种状态色都有对应 `-soft` 版本,共 8 个状态色 + soft = 8 派生 token,完整 11+4=15 基础 + 4 派生 = 19 个)

### "shell" token(只用于 device frame)

| 中文名 | 命名 | OKLch | 用途 |
|---|---|---|---|
| 暗色外壳 | `--shell` | `oklch(15% 0.02 40)` | iPhone 15 Pro 黑色边框 / 浏览器标题栏 / 夜间 device 背景 |
| 暗色外壳 2 | `--shell-2` | `oklch(20% 0.018 40)` | 侧栏 |

> `shell` token 不进入小程序产品 token — 它只用于 OD 演示页面的 device frame,生产小程序不会渲染黑色边框

---

## 2. 字体(3 套)

| # | 角色 | 字体栈 | 备注 |
|---|---|---|---|
| 1 | **Display** | `Fraunces`, `Source Serif Pro`, `Iowan Old Style`, Georgia, serif | 衬线 — 用于 hero 标题、价格、卡片主标题;营造"鲜"和"真",不是网红感 |
| 2 | **Body** | `Inter Tight`, `-apple-system`, `BlinkMacSystemFont`, `'SF Pro Text'`, system-ui, sans-serif | 无衬线 — 用于正文、按钮、tab 文字 |
| 3 | **Mono** | `Geist Mono`, `'IBM Plex Mono'`, ui-monospace, Menlo, monospace | 等宽 — 用于订单号 `ORD-20260607-0184`、数量数字、KPI |

**Font size scale**(OD 内出现):

| 用途 | size / line-height |
|---|---|
| Hero h2 | 26 / 1.1(衬线) |
| 卡片标题 | 22 / 1.05(衬线) |
| 段落标题 | 18 / 1.05(衬线) |
| Tab h1 | 22 / 1.25(衬线) |
| Body | 15 / 1.55 |
| 小标签 | 13 / 1.5 |
| Tag pill | 9 / 1(mono,letter-spacing 0.08em) |
| 数字徽章 | 10 / 1(mono,tabular-nums) |

> **字体引入成本待评估**(见 `redesign-requirements.md` § 5 未决问题 3):3 套字体在小程序包大小 / 按需下载 / 全局注入 vs 局部注入的取舍

---

## 3. 圆角(6 档)

| 档 | 值 | 出现位置 |
|---|---|---|
| 1 | `4px` | 标签 tag |
| 2 | `6px` | browser chrome 侧栏 |
| 3 | `10px` | swatch chip |
| 4 | `14px` | 搜索框 / 卡片内元素 |
| 5 | `16px` | 卡片(标准) |
| 6 | `18px` | 大卡片(设计系统演示) |
| 7 | `22px` | hero banner |
| 8 | `28px` | 设备 frame 内部 |
| 9 | `36px` / `45px` / `56px` | 设备 frame 自身 |
| pill | `999px` | chips / 加号按钮 / tab 圆点 |

(实际 9 档 + pill,但 OD 在 design system 卡片里只列 6 档,真实值在小屏里也有)

---

## 4. 阴影(3 档)

| 档 | 值 | 用途 |
|---|---|---|
| sm | `0 1px 2px oklch(22% 0.02 40 / 0.05)` | 搜索框 / 卡片 |
| md | `0 4px 14px oklch(22% 0.02 40 / 0.07)` | 卡片 hover / 浏览器 frame |
| lg | `0 24px 60px oklch(22% 0.02 40 / 0.10)` 或 `0.18` | iPhone frame 落影 |

**关键 posture**:阴影统一**蓝调中性**(22% 0.02 40),**不**再 red-tinted。这是与"原系统差异
化"的关键决策之一。

---

## 5. Design Posture(6 条 OD 显式声明)

| # | Posture | 含义 |
|---|---|---|
| 1 | 主操作是海洋青实色,**不再用 135° 渐变** | 单一品牌色 flat |
| 2 | 阴影统一蓝调中性,**不再 red-tinted** | 视觉冷静,海鲜"鲜"靠衬线 typography + 摄影,不是阴影 |
| 3 | 价格用墨色或 accent,**不喧宾夺主** | 价格 display serif,数字有 culture |
| 4 | 衬线 display 制造"鲜 / 真",**不是网红感** | Fraunces 是关键差异化 |
| 5 | 单一 accent 用到底,**最多出现两次** | 一屏内 accent ≤ 2 次出现 |
| 6 | 状态色**只用于状态**,不参与品牌叙事 | 绿/金/红 不在品牌层出现 |

---

## 6. 跨页面统一交互系统(OD 自带)

> 9 个 mp-*.html + 6 个 ad-*.html 都内嵌同一段 `跨页面统一交互系统 v2` 脚本。落地到小程序
> 时,这部分要重写成 WXS / TS 实现(HTML 演示用 vanilla JS,小程序需要适配)。

### 6.1 Toast 系统

- 位置:顶部 62px(iPhone 顶岛下方)
- 容器:绝对定位,flex column,8px 间距
- 类型:`info` / `success` / `error`,icon 各异
- 时长:3.2s 进入 + 280ms 退出动画
- cubic-bezier: `0.2, 0.9, 0.3, 1.2`(进入有弹性)

### 6.2 数据属性

| 属性 | 行为 |
|---|---|
| `[data-tabs]` | 子元素 `.active` 切换 + toast |
| `[data-toast]` | 点击弹 toast(常用于"开发中"占位) |
| `[data-action]` | 走 ACTION_MSG 表,29 个动作(edit/cancel/pay/ship/...) |
| `[data-pill]` | 筛选 pill,弹"筛选 · {name}" |
| `[data-page]` | 翻页按钮,弹"第 N 页" |
| `[data-toggle]` | 开关切换,弹"已开启/已关闭 · {label}" |
| `[data-tag-remove]` | 移除标签 chip |
| `[data-radio-group]` | 单选(地址默认) |
| `[data-checkbox-group]` | 多选 + 全选 + 计数 |
| `.qty` / `.stepper` | 数量加减,带 min/max 边界 |

### 6.3 死交互修复

- `flashClicked(el)` 通用函数:任何 `[data-action]` / `[data-pill]` / `[data-page]` /
  `[data-toggle]` / `[data-tag-remove]` / `[data-radio-group] label` /
  `[data-checkbox-group] .check-all` / `[data-tabs] a` / `.check-item`
  点击后,加 `is-clicked` class 持续 50ms(0.18s 过渡),然后加 `is-clicked-fade`
  持续 600ms(0.6s 渐出)
- 这是 OD 演示页的"**死交互修复**" — 解决静态 HTML 没有路由/状态变化时,用户点击无反馈
  的问题。落地到小程序时,真实跳转/状态变化会替代这个 fallback

### 6.4 表单校验

- `<form>` submit → `validateForm(form)` → 失败 toast"请检查表单内容" + 字段红框
- `.field[data-required]` + `data-min="N"` + `input[type=tel]` 自动校验 11 位手机号
- 成功 → 800ms 模拟 loading → toast"操作成功" → 跳 `data-redirect`

---

## 7. 与现有前端(`app.wxss` + `frontend/src/shared/tokens/`)的对比

### 7.1 现有色板(从 `app.json` 推 + 当前 pages 截图)

| 角色 | 当前值 | 来源 |
|---|---|---|
| `navigationBarBackgroundColor` | `#1e3a5f` | `app.json` — 经典海军蓝 |
| `navigationBarTextStyle` | `white` | `app.json` |
| `tabBar.color` | `#718096` | `app.json` — 灰 |
| `tabBar.selectedColor` | `#1e3a5f` | `app.json` — 同海军蓝 |
| `tabBar.backgroundColor` | `#faf8f5` | `app.json` — 米白 |

> 当前**没有显式的 OKLch token**;颜色直接写 hex 在 `app.json` 和各 `index.wxss` 里

### 7.2 差距

| 维度 | OD 设计 | 现状 | 影响面 |
|---|---|---|---|
| 色彩空间 | OKLch(感知均匀,可程序化派生 tints/shades) | hex(分散硬编) | 替换为 `--tokens.json` 用 build-time 注入 |
| 主色 | 暖橙 `oklch(64% 0.16 38)` | 海军蓝 `#1e3a5f` | 品牌方向彻底反转 — 需 design owner 拍板 |
| 状态色 | 4 套 + soft 派生 | 散在各 wxss | 收敛到 token |
| 字体 | 3 套(serif / sans / mono) | 1 套系统字体 | 需引入 Fraunces + Inter Tight + Geist Mono,或保留 1 套做 MVP 降级 |
| 圆角 | 9 档 + pill | 散在 wxss(常见 8/12/16) | 统一 6 档语义化命名 |
| 阴影 | 3 档蓝调中性 | 散在 wxss(常见 grey 阴影) | 收敛 + 蓝调化 |
| Posture | 6 条显式声明 | 无显式 posture | 需写入 `docs/DESIGN.md` 作团队共识 |

### 7.3 落地建议(与 `mini-program` spec § "Design-token parity" 衔接)

- `openspec/specs/mini-program/spec.md` § "Design-token parity with admin-ui" 已要求
  "consume same tokens.json as admin UI for color, spacing, typography"
- 建议落地为 `frontend/src/shared/tokens/tokens.json`(JSON → WXSS 变量)+ 同步一份给
  `frontend/admin-design/tokens.json`(admin-ui 启动时使用,实现"parity")
- OKLch → WXSS:微信小程序支持 CSS custom properties 嵌套的 OKLch(`var(--bg)` 解析为
  `oklch(99% 0.006 60)`),不需要额外处理
- Fraunces / Inter Tight / Geist Mono → 通过 `@font-face` + `font-family` 声明;包大小
  评估见未决问题 3

### 7.4 admin-ui 端(规划)

- `admin-ui/tailwind.config.ts` 需配 OKLch 颜色 theme + 字体 theme + 圆角/阴影 theme
- 与 mp 端共享同一份 `docs/redesign/tokens.json` source of truth(由 build step 派生
  `admin-ui/src/shared/tokens/tokens.tailwind.ts`)
- admin-ui 鉴权独立:`JWT_ADMIN_SECRET` 签发 token,与 mp 端 `JWT_SECRET` 完全隔离
- admin-ui 用户角色:**内部运营 / 客服**(单卖家模型,无商家角色)
- 当前 `admin-ui/tailwind.config.ts` 已存在但**未配 OKLch theme** — Sprint 0 末替换

---

## 8. admin-ui 同步细节

### 8.1 单一 source of truth

```
docs/redesign/tokens.json           ← 唯一源(JSON,19 token + 3 字体 + 6 圆角 + 3 阴影)
        ↓
        ├─→ build step → frontend/src/shared/tokens/tokens.wxss       (mp 端 WXSS)
        └─→ build step → admin-ui/src/shared/tokens/tokens.tailwind.ts (admin 端 TS)
```

### 8.2 mp 端 WXSS 消费

```css
/* frontend/src/shared/tokens/tokens.wxss — 由 build step 生成 */
:root {
  --bg: oklch(99% 0.006 60);
  --fg: oklch(22% 0.02 40);
  --accent: oklch(64% 0.16 38);
  /* ...19 token */
}
```

```css
/* 在 app.wxss 顶部 */
@import '/shared/tokens/tokens.wxss';

.product-card {
  background: var(--surface);
  color: var(--fg);
  border-radius: 16px;  /* 圆角不通过 token,用语义化常量 */
}
```

### 8.3 admin-ui 端 Tailwind 消费

```ts
// admin-ui/src/shared/tokens/tokens.tailwind.ts — 由 build step 生成
export const tokens = {
  bg: 'oklch(99% 0.006 60)',
  fg: 'oklch(22% 0.02 40)',
  accent: 'oklch(64% 0.16 38)',
  // ...
};
```

```ts
// admin-ui/tailwind.config.ts
import { tokens } from './src/shared/tokens/tokens.tailwind';

export default {
  theme: {
    extend: {
      colors: {
        bg: tokens.bg,
        surface: tokens.surface,
        fg: tokens.fg,
        muted: tokens.muted,
        border: tokens.border,
        accent: tokens.accent,
        'accent-soft': tokens['accent-soft'],
        'accent-strong': tokens['accent-strong'],
        success: tokens.success,
        warning: tokens.warning,
        error: tokens.error,
        // ...
      },
      fontFamily: {
        display: ['Fraunces', 'Source Serif Pro', 'Iowan Old Style', 'Georgia', 'serif'],
        body: ['Inter Tight', '-apple-system', 'BlinkMacSystemFont', 'SF Pro Text', 'system-ui', 'sans-serif'],
        mono: ['Geist Mono', 'IBM Plex Mono', 'ui-monospace', 'Menlo', 'monospace'],
      },
      borderRadius: {
        sm: '4px', md: '10px', lg: '14px', xl: '16px', '2xl': '18px', '3xl': '22px', pill: '9999px',
      },
      boxShadow: {
        sm: '0 1px 2px oklch(22% 0.02 40 / 0.05)',
        md: '0 4px 14px oklch(22% 0.02 40 / 0.07)',
        lg: '0 24px 60px oklch(22% 0.02 40 / 0.18)',
      },
    },
  },
};
```

```tsx
// admin-ui 使用方式
<button className="bg-accent text-bg font-body px-4 py-2 rounded-pill">
  登录
</button>
```

### 8.4 字体加载(mp + admin 共享)

- mp 端:通过 `@font-face` + `font-family` 声明,字体文件放 `frontend/assets/fonts/`,
  通过 build step 打包到小程序包;或走 CDN 动态加载(权衡包大小 vs 首屏)
- admin 端:同 mp 端,可走 `fontsource` npm 包(Fraunces / Inter Tight / Geist Mono)
  或自托管;Tailwind 配 `fontFamily.display` 等已 ok
- **决策点**(Sprint 0 末):字体子集化(中文字符集大,只取数字 + 英文 + 常用汉字)
  vs 完整字体;走 CDN 还是 bundle

### 8.5 token parity 验证

- 写 1 个 parity 单元测试:从 mp `tokens.wxss` 解析所有 `--xxx` 变量 vs 从 admin
  `tokens.tailwind.ts` 解析所有 `tokens.xxx` 值,断言两者一一对应
- 该测试在 CI 跑(Sprint 0 末加入)

---

## 9. 一句话总结

> 从 `#1e3a5f` 海军蓝白系统(mp)+ 散乱 Tailwind 默认色(admin),迁到 OKLch 暖中性(蛋壳背景)
> + Fraunces 衬线 display(鲜/真)+ 烤虾橙(主操作)的"海洋青实色"系统;**mp + admin 共享同一份
> `tokens.json`** 由 build step 派生两端。**统一 posture**:蓝调阴影,实色无渐变,accent ≤ 2 次/屏,
> 状态色不参与品牌叙事。
