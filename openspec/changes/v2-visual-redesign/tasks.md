# v2-visual-redesign 实施任务清单

> **总工作量:72.5 eng-day**,2-4 人协作,~7 周(1 + 2 + 2 + 2)收尾。
> 4 个 Sprint 切分;每任务标 owner(frontend / admin-ui / backend / design)、依赖、验收。
> 与 `docs/redesign/05-moscow-roadmap.md` § 3 一一对应。

---

## 1. Sprint 0 — 设计系统 + 脚手架(1 周,~5 eng-day)

> 入口:必须 design owner 在场拍板「烤虾橙」主色;若不在,本 Sprint 暂停启动。
> 出处:M-1

### 1.1 token 源 + build step

- [x] 1.1 写 `docs/redesign/tokens.json`(19 token + 3 字体 + 6 圆角 + 3 阴影),design owner 拍板 OKLch 值 — **2026-06-13 完成 + design owner 拍板 accept**:`docs/redesign/tokens.json` 写好,值取自 OD `03-design-system.md` § 1-4。**design owner 确认 OD 值就是 brand final,不再微调**。`_meta.re_review` 字段保留(指向 OD 来源 + 拍板日期)作为审计轨迹。
- [x] 1.2 写 `scripts/build-tokens.js`(无依赖,Node.js)— 读 JSON → 输出 mp WXSS + admin Tailwind TS — 2026-06-13 写好,247 行,带 hex-rejection 校验。
- [x] 1.3 加 `npm run build:tokens` 脚本到根 `package.json` — 2026-06-13 根 `package.json` 新建,含 `build:tokens` + `test:tokens` 两个 script。
- [x] 1.4 写 token parity 单元测试:mp `tokens.wxss` vs admin `tokens.tailwind.ts` 一一对应 — 2026-06-13 写好,`scripts/__tests__/build-tokens.test.js`,**9/9 测试通过**(含 hex-reject + missing-key-reject 负向)。
- [x] 1.5 CI 加 `npm run build:tokens` + parity 测试步骤(`.github/workflows/ci.yml`) — **2026-06-13 完成**:ci.yml 加独立 `tokens` job(setup-node@v6 Node 20 + `npm run build:tokens` + `npm run test:tokens`),3 分钟超时,跑在所有下游 job 之前。任何改 `docs/redesign/tokens.json` 的 PR 都会触发 parity 护栏。

### 1.2 mp 端视觉替换

- [ ] 1.6 替换 `frontend/src/shared/components/Button` 为 OD 风格(accent 实色 / pill / 蓝调阴影)
- [ ] 1.7 替换 `frontend/src/shared/components/Empty` 为衬线 display
- [ ] 1.8 替换 `frontend/src/shared/components/Loading` 为新 spinner
- [ ] 1.9 替换 `frontend/src/features/product/components/ProductCard` 视觉
- [ ] 1.10 替换 `frontend/src/features/product/components/ProductList` 视觉
- [ ] 1.11 替换 `frontend/src/features/cart/components/CartItemRow` 视觉
- [ ] 1.12 替换 `frontend/src/features/order/components/OrderItemRow` 视觉
- [x] 1.13 替换 `frontend/app.wxss` 顶部加 `@import '/shared/tokens/tokens.wxss';` — **2026-06-13 软切完成**:顶部加 `@import` + 14 行注释, v1 `=== TOKENS:BEGIN ===` 块保留,`var(--color-*)` 走 v1, `var(--*)` 走 v2,共处不冲突。无视觉变化。
- [x] 1.14 替换 `frontend/app.json` 颜色(`navigationBarBackgroundColor` + `tabBar.*`)为 accent hex 副本 — **2026-06-13 硬切完成 + design owner 拍板 accept perceptual approx**:nav bar `#1e3a5f` → `#C9744A` (perceptual approx of `oklch(64% 0.16 38)`,design owner 接受);tabBar.selectedColor 同色,tabBar.color `#718096` → `#7E7670` 雾,tabBar.backgroundColor `#faf8f5` → `#FAF8F4` 蛋壳;`_hex_fallback_note` 注释说明 hex 来源 + Sprint 0 末 design owner 可给精确 sRGB 替换。
- [ ] 1.15 跑 `npm test -- --coverage`,Row 快照测试更新,覆盖率 ≥ 88%

### 1.3 字体引入

- [ ] 1.16 字体子集化 spike:取数字 + 英文 + 常用汉字 ~500 字(1d 字频统计)
- [ ] 1.17 把子集字体文件放 `frontend/assets/fonts/`(`.woff2` 格式)
- [ ] 1.18 写 `frontend/src/shared/tokens/fonts.wxss`,`@font-face` 引用 woff2
- [ ] 1.19 在 admin-ui `package.json` 加 `@fontsource/fraunces` / `inter-tight` / `geist-mono` 依赖
- [ ] 1.20 admin-ui 入口 import 这 3 个 fontsource 包的 css

### 1.4 admin-ui 脚手架

- [ ] 1.21 admin-ui 初始化:Vite + React 18 + TS + Tailwind 3 + shadcn/ui 基础
- [ ] 1.22 配 `admin-ui/tailwind.config.ts` 消费 `tokens.tailwind.ts`(colors + fontFamily + radius + shadow) — **BLOCKED**(2026-06-13 探查后):admin-ui **已有 6 个 page 实质内容**(`LoginPage` / `DashboardPage` / `ProductListPage` / `ProductForm` / `OrderListPage` / `OrderDetailPage`),全用 v1 嵌套 token(`text-primary-500` / `text-app-muted` / `text-feedback-error` / `text-h1` / `text-small`)。`admin-ui/src/index.css` 还用 `theme('colors.primary.500')` 引用 v1。**硬切 = 6 个 page 编译报错 + 焦点环丢失**。**真实 blocker** = 需要独立「admin-ui v1→v2 迁移 PR」,逐 page 把 v1 嵌套 class 替换成 v2 flat 命名(`text-app-muted` → `text-muted`,`text-feedback-error` → `text-error`,`text-primary-500` → `text-accent`,`text-h1` → 用 Tailwind 默认 text-3xl 等);同时移除 `index.css` 的 `theme('colors.primary.500')` 引用。**与 design owner 拍板无关**;纯机械迁移。Sprint 1 起开独立 task。
- [ ] 1.23 写 `admin-ui/src/main.tsx` 在根 element 加 `bg-bg text-fg` 等基础 token 应用 — **BLOCKED by 1.22**:在 `main.tsx` 根 div 加 `className="bg-bg text-fg"` 或在 `index.css` 加 `body { @apply bg-bg text-fg; }`,前提是 v2 flat token 已注入 tailwind theme(1.22 切完后才生效)。1.22 未切,1.23 加 class 编译会报 "Cannot apply unknown utility class bg-bg"。等 1.22 迁移 PR 完成后一并做。
- [ ] 1.24 写 1 个空 page(Sprint 0 末 spike),确认 dev server 启动 + Tailwind 主题渲染 OK
- [ ] 1.25 `admin-ui/src/features/` 初始化 4 个 feature 目录(auth / dashboard / product / order)骨架

### 1.5 文档 + 验收

- [ ] 1.26 重写 `docs/DESIGN.md`(6 posture + token 索引 + 字体加载策略)
- [ ] 1.27 Sprint 0 验收:mp 端 WeChat DevTools 渲染新 token;admin-ui `npm run dev` 启动空页;parity 测试通过

---

## 2. Sprint 1 — Must mp-01~04 + mp-08 状态机 + ad-01/02(2 周,~10 工作日)

> 入口:Sprint 0 已完成,tokens + 字体 + 脚手架就位。
> 出处:M-2 / M-3 / A-1 / A-2

### 2.1 mp-01~04 视觉对齐(8 eng-day,1-2 frontend)

- [ ] 2.1 mp-01 首页视觉:5 分类入口 / Hero / 6 卡瀑布 / 4 chips / 4 tab(3d)
- [ ] 2.2 mp-02 分类视觉:左侧分类 / 右侧 2 列 / chips(1.5d)
- [ ] 2.3 mp-03 商品详情视觉:大图 / 价格 / stepper / 3 按钮(1.5d)
- [ ] 2.4 mp-04 购物车视觉:列表 / 全选 / stepper / 删 / 结算(2d)
- [ ] 2.5 E2E 跑通「首页 → 加购 → 购物车」+ 4 屏视觉与 OD HTML 差异 < 5%(2d QA)

### 2.2 mp-08 订单状态机(5 eng-day,1 frontend + 0.5 backend)

- [ ] 2.6 后端:扩 `OrderStatus` 加 `REFUNDING` 状态(M-3 准备;C-2 实际 Sprint 3)
- [ ] 2.7 mp-08 视觉对齐:6 tab / 订单卡 / 状态色标(0.5d)
- [ ] 2.8 mp-08 状态 → 操作按钮映射(5 状态 × 5 按钮)落 `frontend/src/features/order/components/OrderActionRow`(2d)
- [ ] 2.9 mp-08 操作端点封装:cancel / pay / remind-ship / confirm-receive / rebuy(1d)
- [ ] 2.10 5 状态各 1 个 E2E 路径(1d QA)
- [ ] 2.11 `orders.*` 3 counter 埋点联调(created / cancelled / paid)(0.5d)

### 2.3 ad-01 登录(2.5 eng-day,1 admin-ui + 1 backend)

- [ ] 2.12 后端:写 `AdminCookieAuthController`(`POST /api/admin/auth/cookie-login` + logout + csrf 端点)
- [ ] 2.13 后端:扩 `auth` spec — admin cookie 鉴权 + 3 次失败锁 + IP 限流
- [ ] 2.14 admin-ui:ad-01 表单(phone + password + 记住我 + 登录按钮)(1.5d)
- [ ] 2.15 admin-ui:axios 拦截器(401 → 跳登录;CSRF token 注入)(0.5d)
- [ ] 2.16 E2E 跑通「登录 → 进 ad-02」+ 失败 3 次锁 15 分钟(0.5d QA)

### 2.4 ad-02 仪表盘(5 eng-day,1 admin-ui + 1 backend)

- [ ] 2.17 后端:`GET /api/admin/dashboard` 补 `trend7d` 7 天聚合(2d)
- [ ] 2.18 后端:`GET /api/admin/dashboard` 补 `lowStock` 库存 < 10 Top 10 聚合(0.5d)
- [ ] 2.19 admin-ui:4 KPI Card(0.5d)
- [ ] 2.20 admin-ui:Recharts 7 天趋势折线(0.5d)
- [ ] 2.21 admin-ui:近期订单流 + 库存预警(0.5d)
- [ ] 2.22 E2E 跑通「登录 → 看 4 KPI → 跳近期订单」(1d QA)

### 2.5 Sprint 1 验收

- [ ] 2.23 4 屏视觉与 OD HTML 差异 < 5%(QA 拍图对比)
- [ ] 2.24 mp-08 5 状态各 1 E2E 路径
- [ ] 2.25 ad-01/02 可登录 + 仪表盘数据正确(seed 数据)
- [ ] 2.26 `orders.*` 3 counter 在 mp-08 状态切换时正确埋点

---

## 3. Sprint 2 — Should ad-03/04 + mp-05~07(2 周,~10 工作日)

> 入口:Sprint 1 已完成,mp 漏斗顶端 + ad 鉴权就位。
> 出处:S-1 / S-2 / S-3 / A-3 / A-4

### 3.1 ad-03 商品列表(5 eng-day,1 admin-ui + 1 backend)

- [ ] 3.1 后端:`POST /api/admin/products/{id}/duplicate`(2d)
- [ ] 3.2 后端:`POST /api/admin/products/export` CSV 流式响应(1d)
- [ ] 3.3 admin-ui:ad-03 shadcn DataTable + 筛选 + 批量 + 单行(2d)
- [ ] 3.4 E2E 跑通「筛选 → 批量上架 → 导出 → duplicate」(0.5d QA)
- [ ] 3.5 Sprint 1 末 spike:1000 单批量发货 + 1 万行 CSV 导出 p99(0.5d,防 Sprint 3 性能风险)

### 3.2 ad-04 商品表单(8 eng-day,1 admin-ui + 1 backend)

- [ ] 3.6 后端:`POST /api/admin/uploads` 图片上传(本地磁盘默认,OSS/S3 决策)(3d)
- [ ] 3.7 后端:`Product` 聚合根加 `skus: List<SKU>` 字段(1d)
- [ ] 3.8 后端:SKU 仓储 / 列表查询 / 验证规则(2d)
- [ ] 3.9 admin-ui:ad-04 shadcn Form + 富文本(1d)
- [ ] 3.10 admin-ui:ad-04 多图上传 UI + 主图标记 + 拖拽排序(1d)
- [ ] 3.11 admin-ui:ad-04 SKU 行内编辑(1d)
- [ ] 3.12 E2E 跑通「新建商品 + 上传 3 图 + 加 2 个 SKU + 发布」(0.5d QA)

### 3.3 mp-05~07 视觉对齐(7 eng-day,1-2 frontend)

- [ ] 3.13 mp-05 我的视觉:用户卡 / 4 状态卡 / 工具列表(1.5d)
- [ ] 3.14 mp-06 订单确认视觉:地址 / 商品清单 / 配送方式 / 备注 / 金额明细(2d)
- [ ] 3.15 mp-07 地址管理视觉:列表 / 编辑 / 默认 / 选择模式(1.5d)
- [ ] 3.16 死交互修复(S-2):`is-clicked` 0.18s 闪 + 0.6s fade-out 落 WXSS,覆盖所有 `[data-action]` / `[data-toggle]` / chips / tab / 全选 / stepper(2d)

### 3.4 mp-06 金额明细(S-3)

- [ ] 3.17 mp-06 金额明细实时算(4 金额项联动)(1d)
- [ ] 3.18 mp-06 备注 max 50 字 + 配送方式切运费(1d)

### 3.5 Sprint 2 验收

- [ ] 3.19 mp 3 屏(05/06/07)视觉对齐
- [ ] 3.20 ad-03 商品 CRUD 走通(含 duplicate)
- [ ] 3.21 ad-04 基础商品 CRUD + 多图上传(若 SKU 滑到 Sprint 3,SKU 行内编辑可 disabled)
- [ ] 3.22 E2E 跑通「登录 → 我的 → 改地址 → 下单」

---

## 4. Sprint 3 — Should ad-05 + Could 收尾(2 周,~10 工作日)

> 入口:Sprint 2 已完成,mp 全 8 屏视觉对齐 + ad 03/04 就位。
> 出处:C-1 / C-2 / A-5 / A-6
> **风险**:后端 1 owner 同时支持 5 条线(C-1 / C-2 / A-5 / A-6 / A-4 收尾),Sprint 3 前置 review 哪些必交付 / 哪些滑到 Sprint 4

### 4.1 C-1 物流轨迹(5 eng-day,1 backend + 1 frontend)

- [ ] 4.1 后端:`Order.tracking` 字段值对象 + `TrackingEvent`(`carrier` / `trackingNumber` / `events`)(2d)
- [ ] 4.2 后端:`GET /api/orders/{id}/tracking` 端点(0.5d)
- [ ] 4.3 mp-08 订单详情时间线(SHIPPED 后 3 节点)(1d frontend)
- [ ] 4.4 ad-06 时间线组件(1d frontend)
- [ ] 4.5 E2E 跑通「发货 → 查物流 → 时间线可见」(0.5d QA)

### 4.2 C-2 退款模型(8 eng-day,1 backend + 1 frontend)

- [ ] 4.6 后端:`Refund` 聚合根 + MongoDB collection + 仓储(1d)
- [ ] 4.7 后端:`POST /api/orders/{id}/refund` 端点(创建 Refund + Order 转 REFUNDING)(1d)
- [ ] 4.8 后端:`POST /api/admin/orders/{id}/refund/approve` + `/reject` 端点(1d)
- [ ] 4.9 后端:`orders.refunded` counter 埋点(0.5d)
- [ ] 4.10 mp-08 申请退款 UI(底部 sheet + 原因 + 金额 + 提交)(1d frontend)
- [ ] 4.11 ad-06 退款审核 UI(展示原因 / 金额 / 同意 / 拒绝 + 拒绝原因)(1d frontend)
- [ ] 4.12 E2E 跑通「mp 申请 → ad 审核 → mp 状态变 REFUNDING → 同意 → REFUNDED」(1d QA)

### 4.3 A-5 订单列表(5 eng-day,1 admin-ui + 1 backend)

- [ ] 4.13 后端:`POST /api/admin/orders/batch-ship`(2d)
- [ ] 4.14 后端:`POST /api/admin/orders/{id}/print-picklist` PDF 流(2d)
- [ ] 4.15 后端:`GET /api/admin/orders/export` CSV(0.5d)
- [ ] 4.16 admin-ui:ad-05 shadcn DataTable + 状态 tabs + 批量操作(0.5d)
- [ ] 4.17 E2E 跑通「筛选已付款 → 批量发货 → 拣货单打印 → 导出」(0.5d QA)

### 4.4 A-6 订单详情(3.5 eng-day,1 admin-ui + 0.5 backend)

- [ ] 4.18 admin-ui:ad-06 3 列布局(订单商品 / 用户信息 / 物流 / 金额明细)(2d)
- [ ] 4.19 admin-ui:ad-06 底部状态驱动操作栏(0.5d)
- [ ] 4.20 后端:确认 `orderDetail()` payload 包含 OD 期望的所有字段(含 `tracking` / `refundId`)(0.5d)
- [ ] 4.21 E2E 跑通「跳 ad-06 看订单核心信息」(0.5d QA)

### 4.5 Sprint 3 收尾决策

- [ ] 4.22 Sprint 3 末做最后决策:C-1 + A-5 必须落地;C-2 + A-6 若未完,标"待 C-1/C-2 落地"或滑到 Sprint 4
- [ ] 4.23 跑完整 E2E 套件,跨 14 屏 + 9 后端新端点
- [ ] 4.24 更新 `docs/redesign/05-moscow-roadmap.md` § 7 验收清单,勾完所有项
- [ ] 4.25 runbook 补 1 段「v2 视觉 + admin-ui」发布前 design owner 验 14 屏(交给 `setup-runbook-and-oncall` owner)

### 4.6 Sprint 3 验收

- [ ] 4.26 mp-08 物流 / 售后 可见(若后端 C-1/C-2 完成)
- [ ] 4.27 ad-05 单订单 + 批量发货走通
- [ ] 4.28 ad-06 订单核心信息可看(物流/退款模块标"待 C-1/C-2 落地"也可接受)

---

## 5. 跨 Sprint 共享(不在 Sprint 0~3 之内,但需持续推进)

### 5.1 部署 + CI

- [ ] 5.1 `admin-ui/Dockerfile`(多阶段:Node 20 build → nginx:1.27-alpine serve)
- [ ] 5.2 `admin-ui/nginx.conf`(反代 `/api/` 到 `http://backend:8080`)
- [ ] 5.3 `docker-compose.yml` 加 `admin-ui` 服务(depends_on: backend healthy)
- [ ] 5.4 `.github/workflows/ci.yml` 加 parity 测试步骤(对应 1.5)
- [ ] 5.5 `.github/workflows/native.yml` 加 admin-ui image build 步骤(若适用)

### 5.2 可观测性

- [ ] 5.6 `orders.created` / `orders.cancelled` / `orders.paid` / `orders.refunded` counter 联调(配合 `setup-observability-stack` PR #15)
- [ ] 5.7 `users.login.attempts{result=success/failed/locked/invalid_role}` counter 联调
- [ ] 5.8 mp-08 状态切换埋点对齐(运行时核对 ArchUnit `MetricsCardinalityTest`)

### 5.3 与 in-flight 工作的衔接

- [ ] 5.9 `setup-runbook-and-oncall` 补 1 段「v2 视觉 + admin-ui」发布前 design owner 验 14 屏
- [ ] 5.10 `add-miniapp-e2e-tests` 覆盖本路线图 E2E 路径(若 in-flight 已写部分 mp-* E2E)
- [ ] 5.11 `introduce-feature-flag-platform` 若启用,Sprint 0 末决策「全员发布」还是「灰度」

### 5.4 数据迁移

- [ ] 5.12 无:`Order.REFUNDING` 是新增状态,无已存在「COMPLETED + REFUNDING」混状态订单
- [ ] 5.13 无:`Product.skus` 是新增字段,`Product.price` / `stock` 保留作「默认 SKU」(向后兼容)

---

## 6. 全 Sprint 收尾总验收(5 eng-day,跨 owner)

- [ ] 6.1 mp 端:`npm test -- --coverage` ≥ 88%,4 类 Row 快照测试通过,8 屏 E2E 全绿
- [ ] 6.2 mp 端:8 屏视觉与 OD HTML 差异 < 5%
- [ ] 6.3 admin-ui 端:`npm test -- --coverage` ≥ 80%,6 屏 E2E 全绿
- [ ] 6.4 后端:`./gradlew check` 通过(ArchUnit + checkNoRefreshScope + 100% 现有测试)
- [ ] 6.5 后端:`./gradlew nativeCompile` 通过 + docker smoke RSS < 200MB
- [ ] 6.6 CI:3 个 workflow 全绿(jvm-check / native / security)
- [ ] 6.7 跨屏:parity 测试通过 + `tokens.json` 单源改动两端同步
- [ ] 6.8 文档:`docs/DESIGN.md` 已重写(6 posture);`docs/redesign/05-moscow-roadmap.md` § 7 验收清单全勾
- [ ] 6.9 OpenSpec:`/opsx:apply` 跑通所有 task;`/opsx:archive` 落 `openspec/changes/archive/2026-06-XX-v2-visual-redesign/`
- [ ] 6.10 main specs 同步:本 change 的 6 个新 spec 落到 `openspec/specs/`,4 个 modified spec 已 append
