## Context

海鲜商城是 1 个微信小程序 + 后端单 Spring Boot 4 仓(Java 25 + GraalVM Native + MongoDB 7.x),`main` 已在生产跑 7 模块归档版本;`feature/refactor` 是单仓改造。**当前视觉系统**有 3 个核心痛点:

1. **mp 端**:`app.json` 写死 `navigationBarBackgroundColor: #1e3a5f` + 微信原生 nav bar;颜色 / 字体 / 阴影 / 圆角 散落在各 `index.wxss`;**无 token 源**
2. **admin-ui 端**:目录存在但 `src/` 空;没有 OKLch theme、没有字体声明、没有 DataTable / Form 封装
3. **设计资产**已就位:Open Design 项目 `686e3434-0233-451e-9c99-debee025a336` 已交付 14 屏高保真 HTML(8 mp + 6 ad)+ 完整 design system(11 OKLch 状态色 + 3 字体 + 6 圆角 + 3 阴影 + 6 posture),详 `docs/redesign/{01..05}`

**Stakeholders**:

- **设计 owner** — 拍板 OKLch 19 token / Fraunces 衬线 display / 烤虾橙(主色);6 posture 写入 `docs/DESIGN.md`
- **mp frontend eng** — 4 features 视觉重写 + 8 屏 wxml 视觉对齐 + 死交互修复
- **admin-ui eng** — React 18 + shadcn/ui + Vite 脚手架 + 6 屏功能实现(单卖家内部运营,**不**做外部商家接入)
- **后端 eng**(1 owner) — 9 项扩展(duplicate / export / uploads / batch-ship / print-picklist / rebuy / refund / tracking / 退款模型)+ admin cookie 鉴权
- **PM** — Sprint 切分 + MoSCoW 验收

**In-flight 依赖**:

- `setup-observability-stack` PR #15 已合并(`orders.*` / `users.login.attempts` counter 模板已落地)
- `sprint2-native-security` PR #8 已合并(backend 鉴权 + rate limit 已在)
- `add-miniapp-e2e-tests` in-flight(E2E 路径会大量用上,本路线图覆盖)
- `introduce-feature-flag-platform` in-flight(若用 flag 控制 v2 视觉灰度,Sprint 0 末决策)
- `setup-runbook-and-oncall` in-flight(本路线图上线后,runbook 补 1 段 design owner 验 14 屏)

**性能预算**:`first paint < 2s` / `page switch < 300ms` / `API < 500ms` / `RSS < 200MB`(native binary ~84MB,远低于 budget)

---

## Goals / Non-Goals

**Goals:**

1. **单一 design system 源**:`docs/redesign/tokens.json` 唯一 source of truth,build step 派生 mp `tokens.wxss` + admin `tokens.tailwind.ts`,**两端 parity 单元测试 CI 守**
2. **mp 8 屏视觉对齐 OD**:mp-01~08 在 WeChat DevTools 渲染差异 < 5%
3. **mp-08 订单状态机 + 物流 + 退款**:`5 状态 × 5 操作按钮` + `Order.tracking` + `REFUNDING` + `Refund` 子表
4. **admin-ui 6 屏上线**:登录 / 仪表盘 / 商品列表 / 商品表单 / 订单列表 / 订单详情,单卖家内部运营模型
5. **mp + ad 共享 OKLch token + 3 字体**:用户视角上,两边都「鲜 / 真 / 暖」
6. **后端 9 项扩展**:5 个新 admin 端点 + 2 个 mp 端点 + 1 个 admin cookie 端点 + 1 个 dashboard 字段补全
7. **4 Sprint 收尾**(7 周,~72.5 eng-day)

**Non-Goals:**

- ❌ **外部商家接入** / **多 seller** / **多租户** / **商家自助门户** / **结算分账** / **提现**(单卖家模型 = 1 个商家,内部运营)
- ❌ **评价系统**(后端无评价模型,mp-08「评价」按钮先占位 toast「开发中」)
- ❌ **真实微信支付对接**(Sprint 3 起,`OrderService.markPaid()` 当前 mock)
- ❌ **OKLch 灰度**(Sprint 0 末决策;默认「全员发布」)
- ❌ **设计 owner 不在时的色板变更**(Sprint 0 启动前 design owner 必须拍板)
- ❌ **mp 端 native binary 重做**(`sprint2-native-security` 已覆盖,无冲突)
- ❌ **现有可观测性 / runbook 重做**(复用其 counter / runbook 模板)

---

## Decisions

### 决策 1:`docs/redesign/tokens.json` 单一源 + build step 派生两端

**Why**:mp + ad 共享同一份 token 是「设计 parity」的本质要求;build step 派生避免两端 drift;**parity 单元测试 CI 守**(若某 token 改了一端忘改另一端,CI 立即 fail)。

**Alternatives**:

- ❌ **两端各写一份 `tokens.json`**:易 drift,违反 mini-program spec § "Design-token parity with admin-ui"
- ❌ **mp 端读 admin `tokens.tailwind.ts`**(运行时):admin-ui 不嵌入 mp bundle,不可行
- ✅ **单一 JSON + build step 派生两端**:本次采纳

**实现**:`scripts/build-tokens.js`(Node.js,无依赖)— 读 `docs/redesign/tokens.json` → 输出 mp `frontend/src/shared/tokens/tokens.wxss` + admin `admin-ui/src/shared/tokens/tokens.tailwind.ts`。CI 加 1 步 `npm run build:tokens` + 1 个 parity 测试。

---

### 决策 2:admin-ui 鉴权 Sprint 1 启动就走 httpOnly Cookie

**Why**:localStorage 存 JWT 易被 XSS 偷;httpOnly cookie 配 CSRF token 是后端 Spring Security 标准实践;**不接受「先 localStorage 后迁移」中间态**(避免二次返工)。

**Alternatives**:

- ❌ **localStorage → 后续迁 cookie**(决策 Q8 排除):两次返工
- ❌ **OAuth 2.0 Authorization Code + PKCE**:对单卖家内部运营过度设计
- ✅ **httpOnly Cookie + CSRF token + 短 TTL(15 min)+ 滑动续期**:本次采纳

**后端扩**:`AdminCookieAuthController` — `POST /api/admin/auth/cookie-login` 写 cookie + 返回 CSRF token;`POST /api/admin/auth/logout` 清 cookie + 撤销 session。`JWT_ADMIN_SECRET` 仍用于 cookie 签名(独立于 mp 端 `JWT_SECRET`)。

---

### 决策 3:mp 端字体 `@font-face` 打包 + admin 端 `fontsource` npm

**Why**:mp 端走 CDN 动态加载对首屏 < 2s 不可控(3G 环境尤其);admin-ui 是 Vite SPA,fontsource 走 npm 是 React 生态惯用法。**子集化**降低包大小(Fraunces 全字符 ~500KB → 数字 + 英文 + 常用汉字 ~500 字 ~200KB)。

**Alternatives**:

- ❌ **CDN 动态加载**(mp):首屏 < 2s 风险高
- ❌ **完整字体打包**(mp + admin):包大小超预算 ~800KB
- ❌ **不引入字体,纯 system 字体**:失去「鲜 / 真」衬线 display 差异化(posture 4)
- ✅ **子集化 + mp `@font-face` 打包 + admin `fontsource` npm**:本次采纳(决策 Q3)

**MVP 降级路径**:若包大小 > 预算 20%,Sprint 0 末降级为 1 套 Inter Tight(衬线 display 用 system serif 替代);Sprint 1 末评估。

---

### 决策 4:后端 1 owner 同时支持 Sprint 3 五条线,**接受延期风险**

**Why**:C-1(物流)+ C-2(退款)+ A-5(批量)+ A-6(payload)+ A-4(SKU 收尾)5 条线需后端深度领域扩张;1 owner 全做,减少跨 owner 协调成本(避免分 2 owner 引入事务边界 / 一致性 / 集成测试复杂度)。

**Alternatives**:

- ❌ **拆分给 2 owner**:增加协调成本,Sprint 1+2 已习惯 1 backend owner
- ❌ **Sprint 3 拆 2 周 + Sprint 4 收尾**:延长 1 周,违反「4 Sprint 7 周收尾」目标
- ✅ **1 owner + 接受延期风险**:本次采纳(决策 Q5);Sprint 3 前置 review 哪些必交付,哪些可滑到 Sprint 4

**MVP 降级路径**:Sprint 3 末做最后决策,优先 C-1(物流)+ A-5(批量发货),C-2(退款)+ A-6(详情)可滑到 Sprint 4。

---

### 决策 5:`Order` 聚合根加 `tracking: Tracking` 字段 + `REFUNDING` 状态 + `Refund` 子表

**Why**:OD 演示态有「申请售后」/「退款审核」/「物流时间线」3 个能力,后端必须支持;**新 status + 新表比复用老 `OrderStatus` 字符串扩展更显式**(避免「COMPLETED + canceled」之类组合状态)。

**Alternatives**:

- ❌ **复用老 status,加 `cancelReason`**:语义混杂,UI 难映射
- ❌ **`Order` 内嵌 `List<Refund>`**:DDD 聚合过大,违反「小而清晰」原则
- ✅ **`REFUNDING` 状态 + `Refund` 子表 + 领域事件 `RefundRequested`**:本次采纳(决策 Q2)

**状态流**:`PENDING → PAID → SHIPPED → COMPLETED → REFUNDING → REFUNDED`(终态)/ `CANCELLED`(任一阶段可)

---

### 决策 6:`Product.skus: List<SKU>` 领域扩张,mp 端用底部 sheet 选规格

**Why**:mp-03 商品详情原本只展示 1 个 `price` + `stock`;OD 演示态有「规格选择」UI(衬线 display 价格随 SKU 变化);后端必须加 `skus` 字段支持多规格(规格名 / 单价 / 库存)。

**Alternatives**:

- ❌ **在主详情页 tab 切规格**:页结构被打散,UX 沉重
- ❌ **顶部 stepper 旁边加 +/– 切规格**:与数量 stepper 概念混淆
- ✅ **底部 sheet 弹出**(轻量,1 次额外点击):本次采纳(决策 Q4)

---

### 决策 7:admin-ui 第 3 独立 image(Vite build + nginx),`docker-compose.yml` 加 `admin-ui` 服务

**Why**:Vite build 产物是静态 SPA,跑在 nginx 是最轻部署;`depends_on: backend` + env 配 `VITE_API_BASE_URL=/api`(nginx 反代到 backend);职责清晰,无状态。

**Alternatives**:

- ❌ **与 backend 同进程静态服务**:Spring Boot WebFlux / 静态资源 handler 可加,但与 GraalVM Native 兼容性未测
- ❌ **走 k8s + 独立 ingress**:对单仓 docker-compose 过度
- ✅ **第 3 独立 image + nginx**:本次采纳(决策 Q7)

---

### 决策 8:`Product` 聚合根现状与 SKU 兼容

**Why**:现有 `Product` 是 1 个 productId + 1 个 price + 1 个 stock;**加 `skus` 后,需要决定「`price` / `stock` 是否冗余」**:

- 选 A:`Product.price` / `stock` 保留作「默认 SKU」,`skus` 是可选扩展(空 list = 用 `Product.price` / `stock`)
- 选 B:`Product.price` / `stock` 删除,强制每商品至少 1 个 SKU
- ✅ **选 A**:本次采纳。**向后兼容** — 已有商品 50 条 fixtures 无 SKU 仍能跑;新商品可选择性加 SKU。

---

## Risks / Trade-offs

| 风险 | 等级 | 缓解 |
|---|---|---|
| 后端 1 owner 同时支持 Sprint 3 五条线(C-1 / C-2 / A-5 / A-6 / A-4 收尾) | 🔴 **高** | Sprint 3 前置 review 哪些必交付 / 哪些滑到 Sprint 4;MVP 降级 = 物流/退款延后;遇阻回退 Q5 |
| Fraunces / Inter Tight / Geist Mono 包大小影响 mp 端首屏 | 中 | Sprint 0 评估;若 > 20% 超预算,MVP 降级 = 1 套 Inter Tight(衬线 display 用 system serif 替代) |
| admin-ui React 脚手架 + shadcn/ui 学习曲线 | 中 | Sprint 0 末 1 天 spike 跑通 1 个空 page;admin frontend eng 2 人至少 1 人有 React 经验 |
| SKU 领域扩张对 mp 端 `ProductCard` / 详情屏影响 | 中 | Sprint 2 末做 mp 端 SKU 选规格 UI;若 ad-04 SKU 滑到 Sprint 3,mp 端相应延后 |
| 设计 owner 不在 → 主色不被认 | 🔴 **高** | 必须 design owner 拍板「烤虾橙」是否品牌层决策;Sprint 0 启动前确认;**不可推迟** |
| OKLch 在老 iOS(微信内置 WebView)兼容 | 低 | 微信小程序 8.0+ WebView 支持 OKLch(2022 起的 base line) |
| admin-ui 范围蔓延(把外部商家接入混进来) | 中 | `02-functional-ad.md` § 1.2 明确排除;Sprint review 时坚持;PR template 加「范围」checkbox |
| 后端批量操作 + 导出对 MongoDB 性能影响 | 中 | Sprint 1 末 spike:1000 单批量发货 + 1 万行 CSV 导出,看 p99;若 > 500ms,加索引 + 流式响应 |
| mp 端 `tokens.wxss` 与 admin 端 `tokens.tailwind.ts` drift | 中 | CI parity 单元测试守;任一端改 token 必须同步 JSON 源 |
| 字体子集化字符不足(常用汉字 > 500) | 低 | Sprint 0 spike 1d 字频统计;若不足,扩到 1000 字 ~400KB(仍可控) |
| 死交互修复 `is-clicked` 与微信原生 tap 冲突 | 低 | 0.18s 闪 + 0.6s fade 是 OD 已验证节奏;若冲突,缩短到 0.12s + 0.4s |
| `REFUNDING` 状态接入后,已上线 50 笔历史订单状态机兼容 | 中 | 数据迁移:历史订单全标 `COMPLETED`;`OrderService.refund()` 入参必须校验当前 status ∈ {`COMPLETED`} |

---

## Migration Plan

### 阶段 0:数据迁移(无,纯视觉 + 前端)

无数据迁移 — 视觉系统不影响数据。`Order.status` 加 `REFUNDING` 是新增值,无已存在的「`COMPLETED + REFUNDING`」混状态订单。`Product.skus` 是新增字段,`Product.price` / `stock` 保留作「默认 SKU」(决策 8)。

### 阶段 1:Sprint 0 → Sprint 1 灰度

1. **Sprint 0 末**:落 `tokens.json` + build step + mp 4 features 视觉替换 + admin-ui 脚手架 + Tailwind theme
   - 可灰度方式:`introduce-feature-flag-platform` 加 `v2_visual_enabled` flag(若 in-flight 已就位)
   - 默认**全员发布**(本次决策,简化回滚路径)
2. **Sprint 1 末**:mp-01~04 + mp-08 状态机 + ad-01/02 上线
   - mp 端:`tokens.wxss` 切换,旧 hex 移除
   - admin 端:`admin-ui` 服务加入 `docker-compose.yml`
3. **Sprint 2 末**:mp-05~07 + 死交互 + ad-03/04
4. **Sprint 3 末**:C-1 / C-2 / ad-05/06 收尾

### Rollback 策略

- **mp 端视觉回滚**:`tokens.wxss` 是单一 import,回滚到 Sprint 0 前的 4 Row + 3 shared 视觉;`tokens.wxss` 本身回滚到 `app.wxss` 不 import
- **admin-ui 回滚**:`docker-compose.yml` 注释 `admin-ui` 服务,仅 backend + mongodb 跑(后端 admin 端点保留)
- **后端扩展回滚**:9 个新端点都是 `POST /api/admin/...`,不影响 mp 端;`Product.skus` / `Order.tracking` / `REFUNDING` 是新字段,无已写入数据可不回退
- **`Order.REFUNDING` 状态回滚**:若发现重大 bug,新增迁移脚本:`db.orders.updateMany({status: "REFUNDING"}, {$set: {status: "COMPLETED", _refundRolledBack: true}})`

---

## Open Questions

1. ~~**设计 owner 在不在场**:Sprint 0 启动前**必须**确认「烤虾橙」品牌方向(`oklch(64% 0.16 38)`);若不在,本次 change 暂停启动~~ — **2026-06-13 RESOLVED**:design owner 拍板 accept OD OKLch 19 token 值 + perceptual approx nav bar hex `#C9744A` + OD 6 posture 原文 + 子集化字体策略。Sprint 0 启动**已解锁**。
2. **admin-ui 字体加载**:`fontsource-{fraunces,inter-tight,geist-mono}` 还是自托管 WOFF2?Sprint 0 末 spike 决定 — **2026-06-13 拍板**:走 `fontsource` npm(决策 Q4 接受推荐)。
3. **OKLch 灰度策略**:Sprint 0 末决策「全员发布」还是「灰度」(若用 `feature-flag-platform`,默认 50% 用户)— 默认「全员发布」(Sprint 0 已切流 nav bar,无灰度)。
4. **mp 端 nav bar 改造**:微信原生 nav bar 不支持 CSS 变量,本次 change 改 accent hex 副本;**是否同时重写 nav bar 为自定义组件**(`<view>` 模拟)?需 design owner 决定 — 暂不动,保持微信原生 nav bar(Sprint 3 后若 design owner 要重写再开 change)。
5. **后端 owner Sprint 3 必交付清单**:Sprint 2 review 时锁定(C-1 + A-5 优先 / C-2 + A-6 滑到 Sprint 4?)
6. **`/api/admin/uploads` 接入 OSS/S3 还是本地磁盘**:Sprint 1 spike 决定(本地磁盘简单,OSS 可扩)
7. **mp 端 SKUs 选规格 UI 与 `ProductCard` 集成方式**:Sprint 2 末做(若 ad-04 SKU 滑到 Sprint 3,mp 端相应延后)
8. **admin-ui v1→v2 迁移 PR**:1.22 标 BLOCKED 后发现的真实工作量 — 6 个 page 替换 v1 嵌套 class(`text-primary-500` / `text-app-muted` / `text-feedback-error` / `text-h1` / `text-small`)为 v2 flat class(`text-accent` / `text-muted` / `text-error` + Tailwind 默认 text-3xl/2xl/base 等),`index.css` 移除 `theme('colors.primary.500')`。Sprint 1 起开独立 task(纯机械迁移,与 design owner 拍板无关)。
