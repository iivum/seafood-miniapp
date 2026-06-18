## Context

v2-visual-redesign Sprint 1 在 PR #24 后名义上"完结",但验收标准未真正达成:

- **mp 端**(Sprint 1 验收第 1 条:"mp 4 屏视觉与 OD HTML 差异 < 5%"):实际差距 66-98%。根因是只做了 token 替换 + 局部 swiper indicator 颜色,**页面布局沿用 v1**。
- **mp-08 状态机**:`OrderActionRow` 组件骨架已建(PR #24 commit d2e668b),但 5 状态 × 5 操作按钮未全联通;后端 4 端点中只有 `cancel` 落地,`pay/confirm/reorder` 仍是 stub。
- **ad-01/ad-02**:后端 `AdminBffService.dashboard()` 已有 payload(PR #24 commit 3ca8a22),前端是占位骨架。登录失败 3 次锁 15 分钟、`users.login.attempts{result=locked}` 联调未跑通。
- **E2E 路径未跑通**:"首页 → 加购 → 购物车 → 订单确认 → 提交 → 订单列表" 完整路径缺。

本 change 闭合上述差距,把 Sprint 1 真正变成"达成验收"状态。

## Goals / Non-Goals

**Goals:**
- mp-01/02/03/04 页面级 WXML+WXSS 按 OD 原型重写,与 `docs/redesign/mp-screenshots/` 视觉差 ≤5%(用 mini-program-automator 截图 + haiku 视觉对比验证)
- mp-08 5 状态(7 含 REFUNDING + REFUNDED)5 操作按钮全联通,后端 4 端点 + counter 埋点
- ad-01 登录页 react-hook-form + zod + 失败 3 次锁 15 分钟 + `users.login.attempts{result=locked}` 联调
- ad-02 4 KPI + Recharts 7 天趋势 + 近期订单 + 低库存 4 模块可见
- mp 端 e2e + admin-ui Playwright 跑通 Sprint 1 验收路径

**Non-Goals:**
- 不改 v2 token 体系本身(已在 PR #24 落地且 parity 9/9)
- 不动后端业务模型(已落地的 Order/Refund/Sku/Product 不重构)
- 不开新 npm/Gradle 依赖(Recharts 已在 admin-ui 用过)
- 不动 admin-ui 其它 4 屏(ad-03/04/05/06 是 Sprint 2/3 范围)
- 不动 OD 原型(用户约束"不准改 OD 原型")
- 不重做 spec(只走"modified capabilities"加 ADDED Requirements 模式)

## Decisions

### D1: mp 页面重写策略 — **逐页 WXML+WXSS 重写,保留 JS 逻辑**

**Why**: 视觉差异的根因是 WXML 节点结构与 WXSS 类名都还是 v1 风格。token 替换只动 CSS variable,不动 layout / 节点 / class。必须改 WXML 节点树 + 配套的 WXSS 类。

**Why not "在 v1 WXML 上套 v2 类名"**: 那样只会得到一个混乱的中间状态,v1 的 padding/margin/grid 不动,视觉仍偏 v1。

**保留**: 每个页面的 `index.js` 业务逻辑(CartService/OrderService 调用、`data` 字段、`onLoad`/`onShow` 生命周期)不动 — 重构是后续事。

### D2: mp-08 状态机 — **后端状态转移表驱动,不写 switch**

**Why**: PR #24 在 `Order.java` 用 enum 表达 5 状态,但 5×5 状态转移矩阵应是显式表(`Map<OrderStatus, Set<OrderStatus>>`),便于查"哪些转移合法"和"哪些 counter 触发"。

**Why not "用 if-else 在 controller":** 5×5 = 25 个 case 写出来是反模式,新增状态要改 N 个地方。

**实现**: `OrderStatus.java` 加 `canTransitionTo(OrderStatus target)` 静态方法 + `OrderService.java` 抽 `transition(orderId, action, principal)` 一个统一入口,内部查表 + 埋点 + 返回 DTO。

### D3: ad-01 登录锁 — **IP+account 双维度,3 次/15 分钟**

**Why**: 单 IP 锁会被 NAT 误伤(办公室共用出口 IP 锁整公司),单 account 锁会被攻击者换 IP 暴力破解。两个都计,任一维度超阈值都锁。

**数据**:
- `login_attempts` collection: `{ ip, account, success, ts }`,TTL index 15 分钟自动清
- 锁状态查询: `GET /api/auth/login-lock?account=&ip=` 返 `{ locked: bool, until: ISO8601|null }`

**Why not "用 Redis"**: 单仓架构不上 Redis,留 TODO。Sprint 2 后端 owner 重负载,避免引入新依赖。

### D4: ad-02 dashboard 4 KPI — **后端聚合 + 前端 memoized 计算**

**Why**: 4 KPI(今日订单数/今日营收/待发货/库存预警)需要查多张 collection,在 `AdminBffService` 内做 1 次 MongoDB 聚合(避免 N+1)。

**前端**:
- KPI 数字直接用 `dashboard.today` payload,不重算
- 7 天趋势用 Recharts `<LineChart>`,`xAxis=date`,`yAxis=count`
- 近期订单 `<DataTable>` 5 行,跳 `/admin/orders?from={id}`
- 低库存 `<DataTable>` sku 列表,跳 `/admin/products/{id}`

### D5: E2E 工具链 — **mp 端 miniprogram-automator + admin-ui Playwright**

**Why**:
- mp 端现有 `cartFlow.e2e.test.ts` + `mp08.e2e.test.ts` 用 miniprogram-automator,沿用扩 5 屏截图
- admin-ui 现有 `LoginPage.test.tsx` 是单元,加 Playwright 跑端到端 + 截图(`admin-ui/e2e/`)

**Why not "两套都用 Cypress"**: 引入新工具链扩学习成本,本 change 不开新依赖。

### D6: 视觉对比验证 — **haiku 子代理 + Read 读 PNG,Agent 对比 OD 与实拍**

**Why**: 上轮 (PR #24 终态6) 用 haiku+Read 对比 5 屏 mp 截图,效果是能识别的(虽然有"OD 原型只是 6KB 占位,真实原型在 `design-ref/`"的坑)。本 change 改用 `design-ref/` 下 5 屏高清参考图,避免再被占位图误导。

## Risks / Trade-offs

- **[Risk] mp 页面重写影响 E2E 截图** → 已有 `mp08.e2e.test.ts` 等测试可能因 DOM 变化挂;**Mitigation**: 测试本身就是验证手段,挂了就改测试(测试应反映新结构),不算回归。
- **[Risk] 5×5 状态机表漏 case** → 单元测试覆盖 25 个转移 + 5 个非法转移;**Mitigation**: `OrderStatusTest` + `OrderStatusRefundingTest` 已存在,扩 5 个状态转移 happy path + 5 个非法转移。
- **[Risk] 后端 owner 重负载** → 与 PR #24 Sprint 2 期间后端 owner 同样面临;**Mitigation**: 状态机表 + counter 埋点都是窄改动,1 backend eng 1-2 周内可消化。
- **[Risk] ad-01 锁引入可用性风险** → 误锁影响真实用户;**Mitigation**: 测试用 mock 时间,生产留 unlock 端点(留 Sprint 4 实现)。
- **[Risk] E2E 在 CI 上 flaky** → miniprogram-automator + Playwright 都是真机/真浏览器,CI runner 不一致可能挂;**Mitigation**: 重试 2 次 + 截图 baseline 容许 1% 像素差(实际 OD vs 实拍差 5% 是因为布局,不是像素级)。
- **[Risk] Sprint 1 闭合再次发现"页面布局根本不对"** → 视觉对比可能仍 >5%;**Mitigation**: 接受现实,把 Sprint 2 启动期预算加 1 周做"mp-01/02 重构",不要无限期补刀。

## Migration Plan

无 — 本 change 是纯增量,不动 v2 已有落地。部署:
1. 合 PR → 走 CI(Jest/vitest/gradle check/native smoke)→ 合并到 main
2. admin-ui 不需要后端发版协调,后端 4 端点是已有代码的联通(早已合入)
3. 灰度:建议 100%(已 v2 视觉切流完毕)

回滚:revert PR。

## Open Questions

- **OD 原型 vs 真实设计稿**: `docs/redesign/mp-screenshots/` 下 mp-01~08 是 6KB 占位 PNG,真实设计稿在 `docs/redesign/mp-screenshots/design-ref/` 子目录。本 change 视觉对比用 design-ref/。是否要把 design-ref/ 升到主目录?留 follow-up。
- **ad-01 失败 3 次锁的 unlock 端点**:本 change 只做"锁住",不实现 unlock。是否在 Sprint 1 闭合内补?Sprint 1 不补,留 Sprint 4。
- **ad-02 4 KPI 数字精度**:今日营收是 sum of `unitPrice * quantity` for PAID orders today,但"今日"按哪个时区?(本 change 用 `LocalDate.now(ZoneId.of("Asia/Shanghai"))` 与 PR #24 sprint 2 的 fmtTime 一致)
