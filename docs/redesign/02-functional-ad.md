# 02 · ad-01~06 管理后台 6 屏功能拆解(纳入路线图)

> ✅ **已纳入 MoSCoW 排序(2026-06-13 决策)**:admin-ui 要做(单卖家模型下也需要内部管理后台),
> 只是**不实现外部商家接入**。本后台 = **单卖家内部运营管理**。
> 后端 `/api/admin/**` 3 端点(`dashboard` / `products/stats` / `orders/{id}/detail`)已就位。

---

## 1. 范围边界(明确)

### 1.1 在范围内(单卖家内部运营管理)

- 内部账号登录 + 登录限流(ad-01)
- 仪表盘(ad-02):运营看 4 KPI + 7 天趋势 + 近期订单 + 库存预警
- 商品 CRUD + 上下架 + 库存 + SKU 规格(ad-03, ad-04)
- 订单处理:发货 / 取消 / 退款审核 / 拣货单打印 / 导出 CSV(ad-05, ad-06)
- 内部账号管理(扩展 `/api/admin/users` — 留给 Sprint 3 之后)

### 1.2 不在范围内(明确排除,本路线图不做)

- ❌ 外部商家**入驻流程**(无注册页 / 无审核流)
- ❌ 多 seller / 多租户架构(后端 Product 仍属 1 个 store)
- ❌ 商家**自助门户**(商家看不到 admin-ui;运营人员是唯一用户角色)
- ❌ 商家**结算 / 分账 / 提现**(单卖家模型 = 全部收入归 1 个商家,无分配逻辑)
- ❌ 商家**数据分析看板**(只运营内部看;商家无自助 BI)

> **关键区分**:"admin-ui 实现" ≠ "多 seller 平台"。本后台**始终是 1 个内部管理工具**,
> 跟外部商家无关。

---

## 2. 屏映射表

| OD 屏 | 文件 | 角色 | 类别 |
|---|---|---|---|
| ad-01 | `ad-01-login.html` | 内部运营账号登录 | 鉴权 |
| ad-02 | `ad-02-dashboard.html` | 仪表盘 | 数据 |
| ad-03 | `ad-03-product-list.html` | 商品列表 | 库存 |
| ad-04 | `ad-04-product-form.html` | 商品表单 | 库存 |
| ad-05 | `ad-05-order-list.html` | 订单列表 | 订单 |
| ad-06 | `ad-06-order-detail.html` | 订单详情 | 订单 |

---

## 3. ad-01 管理员登录

### 3.1 功能点

- 账号(手机号) + 密码 + "记住我" + 登录按钮
- 失败 3 次 → 锁 15 分钟(用 `AdminRateLimiter` + `users.login.attempts{result=locked}` counter)
- 成功 → 写 `JWT_ADMIN_SECRET` 签发的 token → 进 ad-02

### 3.2 后端对接

- `POST /api/admin/auth/login` — ✅ **当前后端**:`AdminAuthController` 已就位
- `users.login.attempts{result=success/failed/locked}` 已在 `AuthService` 埋点

### 3.3 MoSCoW 倾向

- 🟥 **Must**(A-1)
- **理由**:无 ad-01 → 整个 admin-ui 不可达;后端已就位;纯前端 ~2d

### 3.4 工作量

- 2 eng-day(纯 frontend:React 18 表单 + shadcn Input + 调用 `POST /api/admin/auth/login` + token 存 localStorage)
- 0.5 eng-day 配合 QA(失败 3 次锁 + 限流联调)

---

## 4. ad-02 仪表盘

### 4.1 功能点

- 4 KPI 卡片:今日订单数 / 今日 GMV / 在售商品数 / 活跃用户(过去 24h)
- 趋势图(7 天订单 + GMV 折线)
- 近期订单流(最新 10 单,跳 ad-06)
- 库存预警(库存 < 10 的商品 Top 10)

### 4.2 后端对接

- `GET /api/admin/dashboard` — ✅ 已就位(`AdminBffController.dashboard()`)
- 期望 payload:`{ kpis: {...}, trend7d: [...], recentOrders: [...], lowStock: [...] }`
- **待补全**:`trend7d` 序列 + `lowStock` list(2d backend)

### 4.3 MoSCoW 倾向

- 🟥 **Must**(A-2)
- **理由**:运营每日必看;端点已就位;~5d(2 frontend + 2 backend 补字段 + 1 QA)

### 4.4 工作量

- 2 eng-day(frontend:shadcn Card + 4 KPI + Recharts 折线)
- 2 eng-day(backend:补 `trend7d` 聚合 + `lowStock` query)
- 1 eng-day(E2E + 视觉)

---

## 5. ad-03 商品列表

### 5.1 功能点

- 顶部筛选:分类 / 状态(在售/缺货/下架) / 关键字搜索
- 表格:缩略图 / 名称 / 分类 / 价格 / 库存 / 状态 / 更新时间 / 操作列
- 批量操作:勾选 → 批量上架/下架/删除
- 单行操作:编辑 / 上下架切换 / 复制 / 删除
- 导出 CSV
- 分页(size=20)

### 5.2 后端对接

- `GET /api/products?page=&size=&category=&status=&q=` — ✅ 已有,**直接复用**
- `PATCH /api/products/{id}` 上下架 — ✅ 已有
- `POST /api/products/{id}/duplicate` — ❌ **需扩展**(2d)
- `POST /api/admin/products/export` — ❌ **需扩展**(3d,CSV 流)

### 5.3 MoSCoW 倾向

- 🟥 **Must**(A-3)
- **理由**:商品管理是后台核心;复用 `/api/products` 大幅降低工作量;~5d

### 5.4 工作量

- 2 eng-day(frontend:shadcn DataTable + 筛选 + 批量)
- 2 eng-day(backend:`duplicate` 端点 + `export` 端点)
- 1 eng-day(E2E + 视觉)

---

## 6. ad-04 商品表单

### 6.1 功能点

- 基础信息:名称 / 描述(富文本)/ 分类(下拉) / 状态(单选)
- 价格(¥) + 库存(件) — 库存 < 0 报错
- 图片上传(多图,主图标记,拖拽排序)
- 规格(SKU):行内编辑(规格名 + 单价 + 库存),行内 add/remove
- 物流信息:重量 / 产地 / 保存方式
- 保存 / 保存并发布 / 预览

### 6.2 后端对接

- `POST /api/products` 新建 — ✅ 已有
- `PUT /api/products/{id}` 完整更新 — ✅ 已有
- 图片文件上传 `POST /api/admin/uploads` — ❌ **需扩展**(3d,接 OSS/S3 决策)
- SKU 规格 — ❌ **domain 待扩展**(`Product.skus: List<SKU>` 字段 + 仓储查询支持,3d)

### 6.3 MoSCoW 倾向

- 🟨 **Should**(A-4)
- **理由**:基础 CRUD 已可用,扩展部分是增量价值;~8d(需后端配合 + SKU 领域扩张)

### 6.4 工作量

- 3 eng-day(frontend:shadcn Form + 富文本 + 多图上传 UI + SKU 行内编辑)
- 3 eng-day(backend:`/api/admin/uploads` 端点 + `Product.skus` 字段)
- 2 eng-day(SKU 仓储 / 列表查询 / 验证规则)
- 1 eng-day(E2E + 视觉)

---

## 7. ad-05 订单列表

### 7.1 功能点

- 顶部筛选:订单号 / 状态(全部/待付款/待发货/待收货/已完成/已取消)/ 时间范围
- 状态 tabs(active 用 accent)
- 表格:订单号(单号 = ORD-20260607-0184)/ 用户 / 商品 N 件 / 实付 / 状态 / 下单时间 / 操作
- 批量:批量发货 / 批量导出
- 单行:发货 / 取消 / 关闭 / 拣货单打印 / 查看详情(跳 ad-06)
- 分页(size=20)

### 7.2 后端对接

- `GET /api/orders?page=&size=&status=&from=&to=&q=` — ✅ 已有
- `POST /api/orders/{id}/ship` — ✅ 已有
- `POST /api/orders/{id}/cancel` — ✅ 已有
- `POST /api/admin/orders/batch-ship` — ❌ **需扩展**(2d,配套 `orders.paid{...,batch=true}` counter)
- `POST /api/admin/orders/{id}/print-picklist` — ❌ **需扩展**(2d,返回 PDF 流)
- `GET /api/admin/orders/export?...&format=csv` — ❌ **需扩展**(2d)

### 7.3 MoSCoW 倾向

- 🟨 **Should**(A-5)
- **理由**:单订单操作已有,批量是高频运营需求;~5d

### 7.4 工作量

- 2 eng-day(frontend:shadcn DataTable + 状态 tabs + 批量操作)
- 2 eng-day(backend:`batch-ship` + `print-picklist` + `export`)
- 1 eng-day(E2E + 视觉)

---

## 8. ad-06 订单详情

### 8.1 功能点

- 顶部:订单号(等宽 mono 字体) / 状态(色标 chip) / 下单时间
- 3 列布局:
  - 左(2/3):订单商品(表格)/ 物流轨迹(时间线)/ 操作历史
  - 右(1/3):用户信息(姓名/电话/微信号)/ 收货地址 / 金额明细
- 底部操作(随状态变):发货 / 取消 / 关闭 / 退款审核
- 退款详情(若有):退款原因 / 退款金额 / 处理人 / 处理时间

### 8.2 后端对接

- `GET /api/admin/orders/{id}/detail` — ✅ 已就位(`AdminBffController.orderDetail()`)
- 物流轨迹 — ❌ **`Order.tracking` 字段待扩展**(同 mp-08 C-1,3d backend)
- 退款审核 — ❌ **退款模型待扩展**(同 mp-08 C-2,`REFUNDING` status + 退款表,4d backend)

### 8.3 MoSCoW 倾向

- 🟦 **Could**(A-6)
- **理由**:端点已有,核心订单信息可看;物流/退款需要后端扩展;Sprint 3 与 mp-08 C-1/C-2 同步
- **降级路径**:Sprint 3 末尾若后端扩展未完,ad-06 暂时去掉"物流轨迹"和"退款详情"两个模块,标灰

### 8.4 工作量

- 2 eng-day(frontend:3 列布局 + 订单商品表 + 用户信息卡 + 金额明细)
- 1 eng-day(backend:确认 `orderDetail()` payload 包含 OD 期望的所有字段)
- 物流 / 退款:与 C-1 / C-2 共享(若 C-1/C-2 未完,ad-06 标"待 C-1/C-2 落地")

---

## 9. 与 backend `/api/admin/**` 现状对齐(总览)

| OD 屏 | 调用的端点 | 已有 / 待扩展 | 状态 |
|---|---|---|---|
| ad-01 | `POST /api/admin/auth/login` | 已有 | ✅ |
| ad-02 | `GET /api/admin/dashboard` | 已有 + 需补 `trend7d` + `lowStock` | ⚠️ |
| ad-03 | `GET /api/products` (复用) | 已有 | ✅ |
| ad-03 | `POST /api/admin/products/{id}/duplicate` | 待扩展 | ❌ |
| ad-03 | `POST /api/admin/products/export` | 待扩展 | ❌ |
| ad-04 | `POST /api/products` / `PUT /api/products/{id}` | 已有 | ⚠️ |
| ad-04 | `POST /api/admin/uploads` | 待扩展 | ❌ |
| ad-04 | SKU 规格(`Product.skus`) | domain 缺 | ❌ |
| ad-05 | `GET /api/orders` | 已有 | ✅ |
| ad-05 | `POST /api/admin/orders/batch-ship` | 待扩展 | ❌ |
| ad-05 | `POST /api/admin/orders/{id}/print-picklist` | 待扩展 | ❌ |
| ad-05 | `GET /api/admin/orders/export` | 待扩展 | ❌ |
| ad-06 | `GET /api/admin/orders/{id}/detail` | 已有 | ✅ |
| ad-06 | `Order.tracking` 物流轨迹 | 待扩展(同 mp-08 C-1) | ❌ |
| ad-06 | 退款模型 | 待扩展(同 mp-08 C-2) | ❌ |

**结论**:核心读路径(看数据)已就位,**所有写路径(批量/导出/上传)与新模型(SKU/物流/退款)
都待扩展**。本路线图落地时,后端 owner 需先做一轮"对账",把 OD 演示态的字段补齐。

---

## 10. 与 mp-01~08 的对齐

| 共享点 | mp 侧 | ad 侧 | 状态 |
|---|---|---|---|
| 订单状态机 | mp-08 5 状态 → 5 组操作按钮 | ad-05 / ad-06 显示同一份 `Order.status` | 共享同一份后端 `OrderService` |
| 物流轨迹 | mp-08 可见(SHIPPED 后) | ad-06 可见(全程) | 共享 `Order.tracking` 字段(待扩) |
| 退款 | mp-08 申请(完结后) | ad-06 审核 | 共享退款表(待扩) |
| 商品 SKU | mp-03 显示规格 | ad-04 编辑 SKU | 共享 `Product.skus`(待扩) |
| 设计 token | mp 使用 OKLch 11 token | ad 使用同一份 token(React + Tailwind) | Sprint 0 同步落 |

**关键设计决策**:mp 与 ad 共享**同一份后端 `Order` 聚合根** + **同一份设计 token**。后端扩展
(SKU/物流/退款)同时服务两侧,前后端 owner 必须紧密对齐。

---

## 11. 与 `openspec/specs/admin-ui/spec.md` 衔接

- 该 spec 当前是骨架;本文件(02)是首份实质 design input
- 建议启动本路线图 Sprint 0 时,同步开 1 个 `openspec/changes/v2-visual-redesign/`(含 mp + ad 全部 8+6=14 屏)
- `mini-program` spec § "Design-token parity with admin-ui" 真正落地 — 同一份 `tokens.json` 被两侧消费

---

## 12. admin-ui 技术栈(规划)

| 维度 | 选型 | 备注 |
|---|---|---|
| 框架 | React 18 + TypeScript 5.x | 与 CLAUDE.md §9 一致 |
| 构建 | Vite 5.x | CLAUDE.md §9 一致 |
| UI 库 | shadcn/ui + Tailwind CSS 3.x | CLAUDE.md §9 一致 |
| 表格 | TanStack Table 8.x(shadcn 包装) | 大表格 + 排序 + 筛选 |
| 图表 | Recharts 2.x | ad-02 趋势图 |
| 路由 | React Router 6.x | — |
| 状态 | Zustand 4.x 或 React Context | 视复杂度 |
| 鉴权 | localStorage 存 JWT_ADMIN_SECRET 签发 token | 与 mp 端 token 隔离 |
| API 客户端 | axios + 拦截器(401 → 跳登录) | 与 mp 端 React Query 风格类似 |

**目录结构建议**(与 `frontend/src/features/*` 对齐):

```
admin-ui/
├── src/
│   ├── features/
│   │   ├── auth/         (ad-01)
│   │   ├── dashboard/    (ad-02)
│   │   ├── product/      (ad-03, ad-04)
│   │   └── order/        (ad-05, ad-06)
│   ├── shared/
│   │   ├── components/   (与 frontend/src/shared 镜像)
│   │   ├── api/          (axios + 拦截器)
│   │   └── tokens/       ← 与 mp 端 tokens.json 同步(同源)
│   ├── App.tsx
│   └── main.tsx
├── tailwind.config.ts    ← OKLch token 注入
├── vite.config.ts
└── package.json
```

---

## 13. 何时启动本路线图?

**现在就是本路线图的工作范围**(用户 2026-06-13 确认 admin-ui 要做)。Sprint 1 起 admin-ui 与
mp 视觉对齐并行启动,见 `05-moscow-roadmap.md` § 3 Sprint 切分。
