## Why

Sprint 1 of v2-visual-redesign(参见 `docs/redesign/05-moscow-roadmap.md` § Sprint 1)在 PR #24 落地后**未真正完结**:

- 验收标准"mp 4 屏视觉与 OD HTML 差异 < 5%"实际差距 66-98% — 根因是只做了 token 替换 + 局部 swiper indicator 颜色,mp-01/02/03/04 的**页面布局**(5 分类入口 / 6 卡瀑布 / 4 chips / 4 tab / stepper / 结算栏 等)未按 OD 原型重写,沿用 v1 结构。
- `mp-08` 状态机只新增了 `OrderActionRow` 组件骨架,5 状态 × 5 操作按钮(取消/付款/提醒发货/确认收货/再次购买)+ 后端 4 端点未全部联通。
- `ad-01` 登录页 + `ad-02` 仪表盘后端有,前端只是占位页,登录失败 3 次锁 15 分钟联调未跑通。
- Sprint 1 E2E 路径("首页 → 加购 → 购物车 → 订单确认 → 提交 → 订单列表")未落地。

为兑现 Sprint 1 验收,补这一刀把 Sprint 1 真正闭合。

## What Changes

- **mp-01 首页 WXML+WXSS 重写**:5 分类 chip(横向 scroll)+ 顶部 banner(swiper)+ 今日推荐 2 列 grid(6 卡瀑布)+ loading/empty/error 状态。OD 对齐到 5% 视觉差内。
- **mp-02 分类页重写**:左侧分类导航 + 右侧 2 列商品瀑布 + 顶部 chips。
- **mp-03 商品详情页重写**:大图 carousel + 价格区 + SKU 选择占位 + stepper + 加入购物车/立即购买/收藏 3 按钮。
- **mp-04 购物车页重写**:全选/单选 + 商品卡 + stepper + 删除 + 底部结算栏(合计/按钮)。
- **mp-08 订单列表 + 详情**:5 状态(PENDING/PAID/SHIPPED/COMPLETED/CANCELLED + REFUNDING)5 操作按钮(取消/付款/提醒发货/确认收货/再次购买/申请退款)。后端 4 端点 `POST /api/orders/{id}/cancel|pay|confirm|reorder` 全部联通。
- **ad-01 登录页**:React 18 + react-hook-form + zod 校验;失败 3 次锁 15 分钟(`IP+account` 维度);`users.login.attempts{result=locked}` counter 埋点;登录成功跳 `/admin/dashboard`。
- **ad-02 仪表盘**:4 KPI(今日订单/今日营收/待发货/库存预警) + Recharts 7 天趋势 + 近期订单表(5 行) + 低库存列表。接 `GET /api/admin/dashboard` payload(已扩 `trend7d` + `lowStock` 字段)。
- **E2E 测试**:
  - mp:"首页 → 加购 → 购物车 → 订单确认 → 提交 → 订单列表 → 取消" 完整路径(miniprogram-automator)
  - ad:"登录 → 仪表盘 → 看 KPI + 跳近期订单" 完整路径(Playwright)
- **维护本期已落地的 v2 token 一致性**:所有新页面继续用 `var(--accent) / var(--fg) / var(--bg)` 等,避免引入 v1 颜色。

## Capabilities

### New Capabilities

(无 — 本 change 只动现有能力,不开新能力。)

### Modified Capabilities

- `mini-program`: 在已有 v2 视觉/订单状态机/地址管理 requirements 之上,补 mp-01~04 OD 布局与 mp-08 5 状态 5 操作的端到端 requirement(本 change 是 v2-visual-redesign Sprint 1 部分的真正闭合)。
- `admin-ui`: 在已有 6 屏 scope 之上,补 ad-01 登录失败 3 次锁与 ad-02 4 KPI + 7d 趋势图表的 requirement。
- `auth`: 在已有 admin cookie auth 与 IP+account 锁 requirement 之上,补"锁定时长 15 分钟 / 锁定后引导走 unlock 流程"的更细 requirement。
- `backend-api`: 在已有 admin dashboard payload 与 Order 行为之上,补"mp-08 5 状态切换必须返回 4 个状态转移端点的 200 响应 + 关联 counter 埋点"的 contract。

## Impact

- **mp 前端**:
  - `frontend/pages/index/index.{wxml,wxss}`(重写)
  - `frontend/pages/category/category.{wxml,wxss}`(重写)
  - `frontend/pages-sub/product/product-detail/product-detail.{wxml,wxss}`(重写)
  - `frontend/pages/cart/cart.{wxml,wxss}`(重写)
  - `frontend/pages-sub/order/order-list/order-list.{wxml,wxss}`(重写)
  - `frontend/pages-sub/order/order-confirm/order-confirm.{wxml,wxss}`(补)
  - `frontend/src/features/order/components/OrderActionRow/`(补 5 操作按钮)
  - `frontend/src/features/product/components/ProductCard/`(对齐 OD)
  - `frontend/src/features/cart/components/CartItemRow/`(对齐 OD)
  - 新增 e2e:`frontend/e2e/mp-cart-flow.test.ts`(miniprogram-automator)
- **admin-ui 前端**:
  - `admin-ui/src/features/auth/LoginPage.tsx`(完成 react-hook-form + zod + lockout 提示)
  - `admin-ui/src/features/dashboard/DashboardPage.tsx`(完成 4 KPI + Recharts)
  - `admin-ui/src/test/setup.ts`(可能补 mock)
  - 新增 e2e:`admin-ui/e2e/admin-login-dashboard.spec.ts`(Playwright)
- **后端**(Sprint 2 期间在 PR #24 已有扩展,本 change 主要联通):
  - `backend/src/main/java/com/seafood/order/application/OrderService.java`(补 4 端点的入参校验 + counter 埋点)
  - `backend/src/main/java/com/seafood/user/api/AuthController.java`(补 lockout 状态查询端点)
  - 已有 `AdminBffService` + `DashboardResponse` 不动
- **测试**:
  - mp e2e suite 扩 9+ 截图(本 change 重点是 mp-01/02/03/04/08 共 5 屏)
  - admin-ui Playwright suite 扩 5+ 截图
- **OD 原型**:`docs/redesign/mp-screenshots/` 下的 5 屏 mp HTML + `design-ref/` 下的 5 屏高清参考图(本 change 不动,只对照)
- **本 change 不开新 npm/Gradle 依赖**,仅复用现有技术栈(React 18 / shadcn/ui / Recharts / miniprogram-automator)
