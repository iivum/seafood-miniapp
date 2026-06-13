## Why

海鲜商城小程序当前视觉系统是「海军蓝 + 散落 hex」方案(mp 端 `app.json` 写死 `#1e3a5f` + 微信原生 nav bar;admin-ui 暂无);**没有 token 化的设计系统**,导致品牌一致性弱、跨表面样式难维护、字体 / 阴影 / 圆角不统一。同时**单卖家模型下仍需内部管理后台**——目前 admin-ui 仅有空脚手架,运营工作(登录 / 仪表盘 / 商品 / 订单)全靠后端 admin 端点手工调用。

Open Design 项目 `686e3434-0233-451e-9c99-debee025a336` 已交付 14 屏(8 mp + 6 ad)高保真 HTML 原型 + 完整设计系统(11 OKLch 状态色 + 3 字体 + 6 圆角 + 3 阴影 + 6 posture);Sprint 2 后端 admin 端点 / auth / metrics 等基础设施已就位。**这次 change 把 OD 资产从「演示态 HTML」落地到「生产 token + 真实组件 + 后端扩展」**,4 个 Sprint(7 周)收尾,总工作量 ~72.5 eng-day。

---

## What Changes

### 设计系统统一(跨 mp + ad)

- **新增** `docs/redesign/tokens.json` 单一 source of truth(19 token + 3 字体 + 6 圆角 + 3 阴影)
- **新增** `scripts/build-tokens.js` build step:派生 `frontend/src/shared/tokens/tokens.wxss`(mp 端 WXSS 变量) + `admin-ui/src/shared/tokens/tokens.tailwind.ts`(admin 端 Tailwind theme 注入)
- **新增** token parity 单元测试:mp `tokens.wxss` 与 admin `tokens.tailwind.ts` 一一对应(CI 跑)
- **替换** mp 端 `app.json` 硬编 hex 为 accent 副本;`app.wxss` 顶部 `@import '/shared/tokens/tokens.wxss'`
- **替换** mp 端 4 个 features 的 `ProductCard` / `ProductList` / `CartItemRow` / `OrderItemRow` 视觉
- **替换** mp 端 3 个 shared 组件 `Button` / `Empty` / `Loading` 视觉
- **替换** mp 端 8 屏 wxml 视觉(mp-01~08 全部对齐 OD)
- **配置** admin-ui `tailwind.config.ts`:OKLch 颜色 theme + 3 字体 theme + 6 圆角 theme + 3 阴影 theme
- **重写** `docs/DESIGN.md`:6 条 posture 写入团队共识

### 字体引入(跨 mp + ad)

- mp 端:`@font-face` + 子集化(数字 + 英文 + 常用汉字 ~500 字,1d spike),打包到 `frontend/assets/fonts/`
- admin 端:`fontsource` npm 包(Fraunces / Inter Tight / Geist Mono)
- **包大小预算**:mp ~200KB / admin ~150KB;超出则 MVP 降级 = 1 套 Inter Tight(衬线 display 用 system serif 替代)

### admin-ui 6 屏全做(单卖家内部运营)

- **ad-01** 登录:React 18 表单 + 失败 3 次锁 + httpOnly Cookie 鉴权(`JWT_ADMIN_SECRET` 签发)
- **ad-02** 仪表盘:4 KPI + 7 天趋势(Recharts)+ 近期订单 + 库存预警
- **ad-03** 商品列表:shadcn DataTable + 筛选 + 批量上架/下架 + 复制 + 导出 CSV
- **ad-04** 商品表单:shadcn Form + 富文本 + 多图上传 + SKU 行内编辑
- **ad-05** 订单列表:状态 tabs + DataTable + 批量发货 + 拣货单打印 + 导出
- **ad-06** 订单详情:3 列布局 + 订单商品 + 用户信息 + 物流轨迹 + 退款审核
- **部署**:`docker-compose.yml` 加第 3 个 `admin-ui` 服务(独立 Vite build image + nginx)

### mp 端功能扩展

- **mp-07** 地址管理:列表 / 编辑 / 默认 / 选择模式
- **mp-08** 订单状态机:5 状态 → 5 操作按钮(取消/付款/提醒发货/确认收货/再次购买)
- **mp-03** 直购:跳过购物车 → 直接 mp-06
- **mp-08** 物流轨迹(SHIPPED 后可见):`Order.tracking` 字段 + 3 节点时间线
- **mp-08** 申请售后(COMPLETED 后):`Order.status = REFUNDING` + `Refund` 子表

### 后端扩展(9 项,散布 Sprint 1/2/3)

- `bff/admin/AdminBffController.dashboard()` 补 `trend7d` 7 天聚合 + `lowStock` 库存预警 list
- `POST /api/admin/products/{id}/duplicate` 商品复制
- `POST /api/admin/products/export` 商品列表导出 CSV
- `POST /api/admin/uploads` 图片上传
- `product/domain/Product.java` 加 `skus: List<SKU>` 字段(SKU 规格)
- `POST /api/admin/orders/batch-ship` 批量发货
- `POST /api/admin/orders/{id}/print-picklist` 拣货单 PDF 流
- `GET /api/admin/orders/export` 订单导出 CSV
- `order/domain/Order.java` 加 `tracking: Tracking` 字段 + `Order.status = REFUNDING` + `Refund` 子表
- `POST /api/orders/{id}/rebuy` 再次购买(返回 cart items)
- `POST /api/admin/auth/cookie-login` admin httpOnly Cookie 端点(替代 localStorage)

### 跨屏统一交互系统(OD 自带,落 WXSS)

- **死交互修复** `is-clicked` 0.18s 闪一下 → 0.6s fade-out,所有 `[data-action]` / `[data-toggle]` / chips / tab / 全选 / stepper
- **`useToast` hook** 替换各页散落 toast 实现:顶部 62px + 8px 间距 + cubic-bezier 弹性入场
- **`form` 校验** + loading 800ms + 跳 `data-redirect`

### 显式排除(本路线图不做)

- ❌ **外部商家入驻 / 多 seller / 多租户**(单卖家 = 1 个商家)
- ❌ **商家自助门户 / 商家结算 / 分账 / 提现**(无商家角色,只有内部运营)
- ❌ **评价系统**(后端无评价模型)
- ❌ **真实微信支付对接**(Sprint 3 起,`OrderService.markPaid()` 当前 mock)

---

## Capabilities

### New Capabilities

- `visual-design-system`:单一 `tokens.json` source of truth + build step(派生 mp WXSS + admin Tailwind)+ 3 字体加载策略 + 6 posture 写入 `docs/DESIGN.md`;跨 mp + ad 共享
- `admin-ui-modules`:6 屏(ad-01~06)功能规范;含 admin 范围边界(单卖家内部运营,不做外部商家接入);admin httpOnly Cookie 鉴权
- `order-customer-state-machine`:mp-08 5 状态 → 5 操作按钮 + `Order.tracking` 物流字段 + 退款模型(`REFUNDING` 状态 + `Refund` 子表)+ `orders.*` counter 埋点
- `address-management`:mp-07 地址管理(列表 / 编辑 / 默认 / 选择模式)+ `address-edit` 子屏
- `product-sku`:`Product.skus: List<SKU>` 字段 + SKU 仓储 / 列表查询 / 验证规则;mp-03 选规格底部 sheet UI;ad-04 SKU 行内编辑
- `admin-batch-operations`:ad-03 duplicate / export、ad-05 batch-ship / print-picklist / export、ad-02 trend7d + lowStock 补字段

### Modified Capabilities

- `mini-program`:加 4 条 ADDED Requirements — Design-token parity 真正落地(共享 tokens.json)+ Order state machine customer actions + Address management + Direct buy
- `admin-ui`:从骨架变实质 — 加 6 屏功能规范 + 单卖家范围边界 + httpOnly Cookie 鉴权 + 部署方式
- `backend-api`:加 ADDED Requirements — 9 项新端点(duplicate / export / uploads / batch-ship / print-picklist / rebuy / refund / cookie-login)+ `Product.skus` + `Order.tracking` + 退款模型
- `auth`:加 ADDED Requirements — admin httpOnly Cookie 端点(`/api/admin/auth/cookie-login` + logout + CSRF 防护)

---

## Impact

### Affected code

| 路径 | 改动 |
|---|---|
| `frontend/src/shared/tokens/` | **新增** `tokens.json` 源 + `tokens.wxss` 派生 + build step |
| `frontend/src/shared/components/{Button,Empty,Loading}` | 视觉重写(accent 实色 / 蓝调阴影 / pill 圆角 / 衬线 display) |
| `frontend/src/features/{product,cart,order,user}/` | 4 个 features 的 Row / Card 组件视觉重写 |
| `frontend/pages/(主包 4 屏)` + `pages-sub/(分包 4 屏)` | 8 屏 wxml 视觉重写 |
| `frontend/src/shared/hooks/useToast` | **新增** 统一 toast(替代散落实现) |
| `frontend/src/shared/api/` | 加 mp-08 5 状态操作端点封装 + mp-07 地址端点 + mp-03 rebuy |
| `app.json` + `app.wxss` | 颜色硬编 + `@import tokens.wxss` |
| `admin-ui/src/features/{auth,dashboard,product,order}/` | **新增** 4 个 features(React 18 + shadcn/ui) |
| `admin-ui/src/shared/{api,components,hooks,tokens}/` | **新增** 拦截器 + shadcn 封装 + Tailwind theme |
| `admin-ui/tailwind.config.ts` | OKLch theme + 3 字体 + 6 圆角 + 3 阴影 |
| `admin-ui/package.json` | 加 axios / TanStack Table / Recharts / shadcn 依赖 |
| `backend/src/main/java/com/seafood/bff/admin/` | 5 个新端点(dashboard 补字段 / duplicate / export / uploads / batch-ship / print-picklist) |
| `backend/src/main/java/com/seafood/product/domain/Product.java` | `skus: List<SKU>` 字段 |
| `backend/src/main/java/com/seafood/product/infra/` | SKU 仓储 / 列表查询 |
| `backend/src/main/java/com/seafood/order/domain/Order.java` | `tracking: Tracking` + `REFUNDING` 状态 |
| `backend/src/main/java/com/seafood/order/domain/Refund.java` | **新增** 退款子表 |
| `backend/src/main/java/com/seafood/auth/` | `AdminCookieAuthController` 端点 + CSRF token |
| `docker-compose.yml` | 加 `admin-ui` 服务(独立 image + nginx) |
| `docs/DESIGN.md` | 重写(6 posture + token 索引) |
| `docs/redesign/` | 6 份 .md 收编进 OpenSpec 后保留作为 design input 引用 |

### Affected APIs(新端点)

| 端点 | 角色 |
|---|---|
| `POST /api/admin/auth/cookie-login` | admin httpOnly Cookie 登录 |
| `POST /api/admin/auth/logout` | admin 登出(清 cookie) |
| `POST /api/admin/products/{id}/duplicate` | 商品复制 |
| `POST /api/admin/products/export` | 商品列表导出 CSV |
| `POST /api/admin/uploads` | 图片上传(对接 OSS/S3) |
| `POST /api/admin/orders/batch-ship` | 批量发货 |
| `POST /api/admin/orders/{id}/print-picklist` | 拣货单 PDF 流 |
| `GET /api/admin/orders/export` | 订单导出 CSV |
| `POST /api/orders/{id}/rebuy` | 再次购买 |
| `POST /api/orders/{id}/refund` | 申请退款(mp-08) |
| `GET /api/orders/{id}/tracking` | 物流轨迹(mp-08) |
| `GET /api/admin/dashboard` | **扩展** 加 `trend7d` + `lowStock` 字段 |

### Affected dependencies

| 依赖 | 用途 |
|---|---|
| `fontsource-{fraunces,inter-tight,geist-mono}` | admin-ui 字体 npm |
| `@tanstack/react-table` | admin-ui DataTable |
| `recharts` | admin-ui 仪表盘趋势图 |
| `shadcn/ui` 组件套 | admin-ui 基础组件 |
| `react-router-dom@6` | admin-ui 路由 |
| `zustand@4` | admin-ui 状态管理 |
| mp 端无新依赖(继续用微信原生 API) | — |

### Affected specs

- 已有 spec `mini-program` / `admin-ui` / `backend-api` / `auth` 需加 ADDED Requirements(delta spec 写入 `specs/<capability>/spec.md`)
- 新增 6 个 capability spec:`visual-design-system` / `admin-ui-modules` / `order-customer-state-machine` / `address-management` / `product-sku` / `admin-batch-operations`

### Breaking changes

- **BREAKING** `app.json` 的 `navigationBarBackgroundColor` / `tabBar.*` hex 改为 accent hex 副本(微信原生 nav bar 不支持 CSS 变量,只能硬编副本)
- **BREAKING** admin-ui 部署从「无」变为「docker-compose 第 3 服务」,需新增 `admin-ui/Dockerfile` + `admin-ui/nginx.conf`
- **BREAKING** admin 鉴权从「无」变为 httpOnly Cookie,`AdminAuthController` 现有 login 端点需扩 cookie 路径或新增 `AdminCookieAuthController`
- **BREAKING** `Order` 聚合根加 `tracking` 字段 + `REFUNDING` 状态 — 已有的 `OrderStatus` 枚举扩 1 档,后端 9 个 service 路径上需 review(影响 `OrderService.cancel()` / `markPaid()` 等)

### In-flight 关系

| 在飞 PR / change | 关系 |
|---|---|
| `setup-observability-stack` PR #15 | 无冲突;`orders.*` 3 counter 与本路线图 mp-08 状态切换埋点对齐;ad-01 复用 `users.login.attempts` |
| `sprint2-native-security` 已合并 | 无冲突;视觉替换 + admin-ui 都不影响 GraalVM Native |
| `introduce-feature-flag-platform` in-flight | 低耦合;若用 flag 控制 v2 视觉灰度,Sprint 0 末决策「全员发布」还是「灰度」 |
| `add-miniapp-e2e-tests` in-flight | 本路线图 E2E 路径会大量用上;若 in-flight 已写部分 mp-* E2E,本路线图覆盖 |
| `setup-runbook-and-oncall` in-flight | 本路线图上线后,runbook 需补 1 段「v2 视觉 + admin-ui」发布前 design owner 验 14 屏 |

### 待澄清事项(见 `docs/redesign-requirements.md` § 5,8 项决策 2026-06-13 已落定)

- ✅ 字体加载策略(决策 Q3)= 子集化 + mp `@font-face` 打包 + admin `fontsource` npm
- ✅ admin-ui 鉴权(决策 Q8)= Sprint 1 启动就走 httpOnly Cookie
- ✅ admin-ui 部署(决策 Q7)= 第 3 独立 image + nginx
- ✅ 后端 owner 负载(决策 Q5)= Sprint 3 全做 5 条线,接受延期风险
- ✅ 账号管理优先级(决策 Q6)= Sprint 1 就做账号 CRUD(扩 3-4d,总 ~25d)
- ⚠️ 设计 owner 必须 Sprint 0 启动前确认「烤虾橙」是品牌层决策
