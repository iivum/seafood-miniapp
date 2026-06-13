# 04 · 现状 vs OD 差距分析(mp-01~08 + ad-01~06)

> 把 14 屏一对一映射到现有 wxml / 后端端点 + 设计层差异 + 功能层差异,共 8+6=14 行表。
> mp 范围限定 frontend 用户端;ad 范围限定后端已有端点 + admin-ui 脚手架。
> 后端缺 API 已在 `01-functional-mp.md` 各屏"API 依赖"和 `02-functional-ad.md` § 9 标出。

---

## 1. 总体盘点

### 1.1 现有 frontend 结构(CLAUDE.md 更新后)

```
frontend/src/
├── features/             # 4 个: product / cart / order / user
│   ├── product/         (api.ts, types.ts, components/ProductCard, ProductList)
│   ├── cart/            (api.ts, types.ts, store.ts, components/CartItemRow)
│   ├── order/           (api.ts, types.ts, store.ts, components/OrderItemRow)
│   └── user/            (api.ts)
├── shared/
│   ├── components/      (Button, Empty, Loading)
│   ├── hooks/
│   ├── api/
│   ├── tokens/          (无 OKLch token 源)
│   ├── types/
│   └── utils/
├── api/                 (legacy?)
├── modules/             (recommendation, payment, productList)
└── app.tsx              (无文件 — 实际 app.json 在 frontend/ 根)
```

### 1.2 现有 admin-ui 脚手架(在 CLAUDE.md §9 规划中,部分已就位)

```
admin-ui/                # 存在但尚未启动
├── coverage/            (测试覆盖报告目录)
├── index.html
├── node_modules/
├── package-lock.json
├── package.json
├── postcss.config.js
├── src/                 (空 / 待初始化)
├── tailwind.config.ts
├── tsconfig.json
├── vite.config.ts
└── ... (CLAUDE.md §9 规划: React 18 + shadcn/ui + Vite)
```

> **注意**:`admin-ui/` 目录已存在,有 `package.json` + `tailwind.config.ts` + `vite.config.ts`
> 等基础脚手架,但 `src/` 是空的(待 Sprint 0 启动时初始化 feature 结构)。

### 1.3 现有页面 vs OD 屏覆盖关系

| OD 屏 | 现有 wxml / 路径 | 现有 feature 归属 |
|---|---|---|
| mp-01 首页 | `frontend/pages/index/index` | (无,直接用主包) |
| mp-02 分类 | `frontend/pages/category/category` | (无) |
| mp-03 商品详情 | `frontend/pages-sub/product/product-detail/product-detail` | product |
| mp-04 购物车 | `frontend/pages/cart/cart` | cart |
| mp-05 我的 | `frontend/pages/profile/profile` | user |
| mp-06 订单确认 | `frontend/pages-sub/order/order-confirm/order-confirm` | order |
| mp-07 地址管理 | `frontend/pages-sub/user/address/address-list` + `address-edit` | user |
| mp-08 订单列表 | `frontend/pages-sub/order/order-list/order-list` | order |
| ad-01 登录 | (admin-ui 暂无) | (admin-ui 待启动) |
| ad-02 仪表盘 | (admin-ui 暂无) | (admin-ui 待启动) |
| ad-03 商品列表 | (admin-ui 暂无,可复用 mp 端 `ProductList` 组件) | (admin-ui 待启动) |
| ad-04 商品表单 | (admin-ui 暂无) | (admin-ui 待启动) |
| ad-05 订单列表 | (admin-ui 暂无) | (admin-ui 待启动) |
| ad-06 订单详情 | (admin-ui 暂无) | (admin-ui 待启动) |

**结论**:mp 8 屏**全部**有对应 wxml,无新屏;ad 6 屏**全部**为新增(admin-ui 启动)。

### 1.4 现有共享组件

| 组件 | 路径 | 在 OD 中对应 |
|---|---|---|
| `Button` | `frontend/src/shared/components/Button` | accent 实色按钮 / chips / 提交 |
| `Empty` | `frontend/src/shared/components/Empty` | 空购物车 / 空订单 / 空地址 |
| `Loading` | `frontend/src/shared/components/Loading` | OD 未明确(可参考 `.btn.is-loading` 的 spinner) |

---

## 2. mp 8 屏一对一差距表

> 列含义:
> - **设计差距** — OD 视觉 vs 现有视觉
> - **功能差距** — OD 演示能力 vs 现有实现能力

| # | 屏 | OD 关键元素 | 现有 wxml | 设计差距 | 功能差距 |
|---|---|---|---|---|---|
| 1 | mp-01 | Hero + 5 分类 + 6 卡瀑布 + 4 chips + 4 tab | `pages/index/index` | 完全(颜色/字体/阴影 全部替换) | 5 分类(OD 4 分类对齐后端 5 分类) / chips 客户端筛选(OD 演示态) / 购物车 tab badge(需 backend 端点) |
| 2 | mp-02 | 左侧分类 + 右侧 2 列瀑布 + chips | `pages/category/category` | 完全 | 客户端切换分类(后端已支持 query) |
| 3 | mp-03 | 大图 + 价格 + stepper + 3 按钮 | `pages-sub/product/product-detail` | 完全 | stepper / 立即购买 / 跳 mp-06 直购(现有?待验) |
| 4 | mp-04 | 列表 + 全选 + 改 qty + 删 + 结算 | `pages/cart/cart` | 完全 | 全选跨行(现有 store 是否支持) / 删后回退(empty 态) |
| 5 | mp-05 | 4 状态卡(待付款/待发货/待收货/已完成) + 工具列表 | `pages/profile/profile` | 完全 | 4 卡跳 mp-08 带 status query / 工具列表(地址/收藏/客服/设置) |
| 6 | mp-06 | 地址 + 商品清单 + 配送方式 + 备注 + 金额明细 + 提交 | `pages-sub/order/order-confirm` | 完全 | 配送方式切换改运费(后端) / 备注 max 50 字 / 库存不足阻断 |
| 7 | mp-07 | 列表 + 单选选择模式 + 编辑/删除/默认 | `pages-sub/user/address/address-list` + `address-edit` | 完全 | 选择模式(从 mp-06 跳来) / 设为默认单选(后端 endpoint?) / 默认地址不可删 |
| 8 | mp-08 | 6 状态 tab + 订单卡 + 状态 → 操作按钮映射 | `pages-sub/order/order-list` | 完全 | 6 tab(OD:全部/待付款/待发货/待收货/待评价/售后) / 5 状态 → 5 组操作按钮 / 翻页 / 再次购买 / 物流 / 售后 |

---

## 3. ad 6 屏一对一差距表

| # | 屏 | OD 关键元素 | 现有(后端 / admin-ui) | 设计差距 | 功能差距 |
|---|---|---|---|---|---|
| 1 | ad-01 | 账号 + 密码 + 记住我 + 登录 | 后端 `AdminAuthController` 已就位;admin-ui 空 | 完全(新建) | 失败 3 次锁 15 分钟 + `users.login.attempts{result=locked}` 联调 |
| 2 | ad-02 | 4 KPI + 7d 趋势 + 近期订单 + 库存预警 | 后端 `GET /api/admin/dashboard` 已就位;**待补** `trend7d` + `lowStock`;admin-ui 空 | 完全(新建) | Recharts 折线 / 库存预警阈值 10 / 跳 ad-06 |
| 3 | ad-03 | 筛选 + DataTable + 批量 + 单行 + 导出 | 后端 `GET /api/products` 复用;**待扩** `duplicate` + `export`;admin-ui 空 | 完全(新建) | shadcn DataTable / 多选 / 批量上架下架 / CSV 导出 |
| 4 | ad-04 | 基础信息 + 图片 + SKU 规格 + 物流 | 后端 `POST/PUT /api/products` 已有;**待扩** `/api/admin/uploads` + `Product.skus` 字段;admin-ui 空 | 完全(新建) | 富文本 / 多图上传 / SKU 行内编辑 / 拖拽排序 |
| 5 | ad-05 | 状态 tabs + DataTable + 批量发货 + 拣货单 + 导出 | 后端 `GET /api/orders` 已有;**待扩** `batch-ship` + `print-picklist` + `export`;admin-ui 空 | 完全(新建) | shadcn DataTable / 状态色标 / 拣货单 PDF 流 |
| 6 | ad-06 | 3 列布局 + 订单商品 + 用户信息 + 物流 + 退款 | 后端 `GET /api/admin/orders/{id}/detail` 已就位;**待扩** `Order.tracking` + 退款模型;admin-ui 空 | 完全(新建) | 时间线 / 退款审核 / 状态色标 |

---

## 4. 设计系统替换(影响 mp + ad 共享)

### 4.1 现状

- mp 端 `frontend/src/shared/tokens/` 目录存在但**无 OKLch token 源**
- mp 端颜色 hex 直接散落在 `app.json` 和各 `index.wxss`
- mp 端无统一字体声明
- admin-ui 端 `tailwind.config.ts` 已存在但**未配 OKLch theme**

### 4.2 替换后结构(建议)

```
# 单一 source of truth(建议)
docs/redesign/tokens.json           ← 19 token + 3 字体 + 6 圆角 + 3 阴影(JSON 形式)

# 派生(由 build step 生成)
frontend/src/shared/tokens/tokens.wxss       ← mp 端 WXSS 变量
frontend/src/shared/tokens/tokens.d.ts       ← mp 端 TS 类型(可选)

admin-ui/src/shared/tokens/tokens.tailwind.ts ← admin-ui Tailwind theme 配置
admin-ui/tailwind.config.ts                   ← import 上述 + 注入
```

### 4.3 build step(假设)

```bash
node scripts/build-tokens.js \
  --input docs/redesign/tokens.json \
  --output-mp frontend/src/shared/tokens/tokens.wxss \
  --output-admin admin-ui/src/shared/tokens/tokens.tailwind.ts
```

- mp 输出:`--bg: oklch(99% 0.006 60);` 等
- admin 输出:`export const tokens = { bg: 'oklch(99% 0.006 60)', ... };` + Tailwind theme 映射
  `colors.bg: 'var(--bg)'`

### 4.4 与 `app.json` / admin-ui 入口的关系

- `app.json` 里的 `navigationBarBackgroundColor: "#1e3a5f"` 需要改为"硬编 accent hex 副本"
  (微信原生 nav bar 不支持 CSS 变量)
- **决策点**:是否重写 nav bar 为自定义组件(用 `<view>` 模拟)?若用 OD 设计则必须重写
- admin-ui 入口 `admin-ui/index.html` / `App.tsx` 需 import `tokens.tailwind.ts` 并在 `<html>` 标签
  加 `class="bg-bg text-fg"` 等基础 token 应用

---

## 5. 影响面清单

### 5.1 mp 端 — `frontend/src/features/*`(4 个 features)

| feature | 涉及屏 | 改动范围 |
|---|---|---|
| product | mp-02 / mp-03 | ProductCard / ProductList 视觉重写;ProductDetail 视觉 + stepper + 立即购买 |
| cart | mp-04 | CartItemRow 视觉重写;store.ts 全选/多选/删除逻辑对齐 |
| order | mp-06 / mp-08 | OrderItemRow 视觉重写;order-confirm / order-list 屏视觉 + 状态机 |
| user | mp-05 / mp-07 | profile 屏视觉;address-list + address-edit 屏视觉 + 选择模式 |

### 5.2 mp 端 — `frontend/src/shared/*`(共享)

| 路径 | 改动 |
|---|---|
| `shared/components/Button` | 视觉重写(accent 实色 / 蓝调阴影 / pill 圆角) |
| `shared/components/Empty` | 视觉重写(衬线 display "暂无数据") |
| `shared/components/Loading` | 新 spinner 风格(参考 OD `.btn.is-loading`) |
| `shared/tokens/` | **新增** OKLch token 源 + build step |
| `shared/api/` | 新增 mp-08 操作端点封装(cancel / pay / remind / confirm / rebuy) |
| `shared/hooks/` | 新增 `useToast`(跨页面统一交互系统的小程序实现) |

### 5.3 mp 端 — `frontend/pages/(主包 4 屏)`

- `pages/index/index` — mp-01 视觉重写
- `pages/category/category` — mp-02 视觉重写
- `pages/cart/cart` — mp-04 视觉重写
- `pages/profile/profile` — mp-05 视觉重写
- `pages-sub/product/product-detail/` — mp-03 视觉重写
- `pages-sub/order/order-confirm/` — mp-06 视觉重写
- `pages-sub/order/order-list/` — mp-08 视觉重写 + 状态机
- `pages-sub/user/address/address-list/` + `address-edit/` — mp-07 视觉重写 + 选择模式

### 5.4 mp 端 — `app.json` + `app.wxss`

- `navigationBarBackgroundColor`: `#1e3a8a` → 硬编 accent hex(待 design owner 给具体值)
- `tabBar.color` / `selectedColor` / `backgroundColor`:同上,需同步
- `app.wxss`(若存在):全局 token 注入 `@import '/shared/tokens/tokens.wxss';`

### 5.5 admin-ui 端 — `admin-ui/src/features/*`(4 个,新)

| feature | 涉及屏 | 改动范围 |
|---|---|---|
| auth | ad-01 | 登录表单 + token 管理(localStorage → 后续 httpOnly cookie)+ 失败 3 次锁联调 |
| dashboard | ad-02 | 4 KPI Card + Recharts 7d 趋势 + 近期订单流 + 库存预警 |
| product | ad-03, ad-04 | DataTable(列表)+ 筛选 + 批量;Form(新建/编辑)+ 富文本 + 多图 + SKU |
| order | ad-05, ad-06 | DataTable(列表)+ 状态 tabs + 批量发货;3 列布局(详情)+ 时间线 + 退款审核 |

### 5.6 admin-ui 端 — `admin-ui/src/shared/*`(新)

| 路径 | 改动 |
|---|---|
| `shared/api/` | axios + 拦截器(401 → 跳登录);admin JWT token 存 localStorage(Sprint 1)/ httpOnly cookie(Sprint 1 末迁移) |
| `shared/components/` | shadcn/ui 组件封装(DataTable / Card / Form / Button / Input / Select / Dialog / Toast) |
| `shared/tokens/` | **新增** Tailwind theme 配置(由 build step 从 `docs/redesign/tokens.json` 派生) |
| `shared/hooks/` | useAuth / useApi / useToast |

### 5.7 admin-ui 端 — `admin-ui/tailwind.config.ts`

- 配 OKLch 颜色 theme:`colors: { bg: 'oklch(...)', fg: 'oklch(...)', accent: 'oklch(...)', ... }`
- 配字体:`fontFamily: { display: ['Fraunces', ...], body: ['Inter Tight', ...], mono: ['Geist Mono', ...] }`
- 配圆角 / 阴影 theme

### 5.8 后端扩展(影响 `backend/src/main/java/com/seafood/`)

| 后端路径 | 改动 | 出处 |
|---|---|---|
| `bff/admin/AdminBffController.dashboard()` | 补 `trend7d` 7 天聚合 + `lowStock` 库存预警 list | A-2 |
| `bff/admin/`(新) | `POST /api/admin/products/{id}/duplicate` | A-3 |
| `bff/admin/`(新) | `POST /api/admin/products/export` | A-3 |
| `bff/admin/`(新) | `POST /api/admin/uploads` (图片上传) | A-4 |
| `product/domain/Product.java` | 加 `skus: List<SKU>` 字段(SKU 领域扩展) | A-4 |
| `product/infra/`(新) | SKU 仓储 / 列表查询 | A-4 |
| `bff/admin/`(新) | `POST /api/admin/orders/batch-ship` | A-5 |
| `bff/admin/`(新) | `POST /api/admin/orders/{id}/print-picklist` | A-5 |
| `bff/admin/`(新) | `GET /api/admin/orders/export` | A-5 |
| `order/domain/Order.java` | 加 `tracking: Tracking` 字段(物流时间线) | C-1 |
| `order/domain/Order.java` | 加 `REFUNDING` status + 退款子表 | C-2 |

### 5.9 docker-compose / 部署(影响 `docker-compose.yml` + `Dockerfile`)

- ad-01/02/03 等前端 SPA → 是否打 docker image 部署?或与 backend 同进程静态服务?
- **决策点**(Sprint 0 末):admin-ui 部署方式

---

## 6. 与现有 spec 的衔接

### 6.1 `openspec/specs/mini-program/spec.md`

已有需求:

- ✅ Feature-based directory layout
- ✅ Product browsing flow(`GET /api/products`)
- ✅ Authentication and session(wechat login)
- ✅ Cart and checkout
- ✅ Order history
- ⚠️ **Design-token parity with admin-ui** — 首次落地(本路线图 Sprint 0,mp + ad 共享 token)

新需求(本路线图新增,建议作为 Mini-program spec 的 "ADDED Requirements"):

- 📌 **Order state machine — customer actions**:mp-08 5 状态 → 5 组操作按钮(详见 `01-functional-mp.md` § mp-08)
- 📌 **Address management**:mp-07 列表/编辑/默认/选择模式(详见 `01-functional-mp.md` § mp-07)
- 📌 **Direct buy**:从 mp-03 立即购买 → mp-06 直购(跳过 cart)

### 6.2 `openspec/specs/admin-ui/spec.md`

- 当前骨架
- 本路线图 `02-functional-ad.md` 是首份实质 design input
- 建议同步开 1 个 `openspec/changes/v2-visual-redesign/` change 把 14 屏 + token 同步 + 后端扩展归档

### 6.3 `docs/DESIGN.md`

- 当前是空骨架 / 待按本路线图重写
- 建议重写后纳入 Sprint 0 验收标准

---

## 7. 一句话总结

> **mp 8 屏全部有现有 wxml**(无新屏),ad 6 屏**全部为新增**;**mp + ad 共享同一份 design token**;
> 后端扩展 9 项(`/admin/dashboard` 补字段 + 5 新端点 + Product.skus + Order.tracking + 退款模型)
> 散布在 4 个 Sprint。**最大影响面**是:
> ① `frontend/src/shared/tokens/` + `admin-ui/tailwind.config.ts`(共享 token);
> ② 4 mp features + 4 admin features 的 Row / Card 组件视觉重写;
> ③ 后端 9 项扩展(Sprint 1/2/3 分摊,后端 1 owner 重负载风险高)。
