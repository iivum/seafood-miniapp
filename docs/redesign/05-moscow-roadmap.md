# 05 · MoSCoW 排序 + Sprint 切分建议(mp-01~08 + ad-01~06)

> 输入:`01-functional-mp.md`(8 屏 mp)+ `02-functional-ad.md`(6 屏 ad)+ `04-gap-analysis.md`
> 输出:MoSCoW 排序表(14 屏 + 2 跨屏系统) + Sprint 0/1/2/3 切分 + 验收
>
> **2026-06-13 决策**:admin-ui 全 6 屏纳入本路线图(单卖家内部运营,不做外部商家接入)。
> 见 `02-functional-ad.md` § 1 范围边界。

---

## 1. 评估维度

| 维度 | 权重 | 理由 |
|---|---|---|
| 业务价值(转化/留存/复购 + 运营效率) | 高 | mp-01~04 漏斗顶端 + ad-02 仪表盘是运营每日入口 |
| 现有完成度 | 高 | mp 端 8 屏有 wxml + ad 端 3 后端端点已就位,主要差距是视觉 + 部分后端扩展 |
| 依赖关系 | 中 | 设计 token 替换是其他全部的前置(mp + ad 共享) |
| 技术风险 | 中 | 字体包大小 / admin-ui React 脚手架 / 后端扩展(批量/SKU/物流/退款)同步 |

---

## 2. MoSCoW 排序表

> 编号约定:`M-*` = mp Must;`S-*` = mp Should;`C-*` = mp Could;`A-*` = ad(MoSCoW 倾向);
> 跨屏系统 token / 死交互 算 mp 侧 Must(因 mp 范围更广)。

### 🟥 Must(6 项,不做不能上线 v2 视觉)

| 编号 | 范围 | 屏 | 工时 | 依赖 | 验收 |
|---|---|---|---|---|---|
| **M-1** | 设计系统 token 替换:`shared/tokens/tokens.json` + `tokens.wxss` + build step;替换 4 features 的 `ProductCard` / `ProductList` / `CartItemRow` / `OrderItemRow`;替换 `Button` / `Empty` / `Loading` 共享组件;**admin-ui tokens 同步**(Tailwind theme 消费同一份 token) | mp + ad 跨屏 | 5d | 无 | `npm test -- --coverage` ≥ 80%;mp Row 快照测试更新;admin-ui Tailwind theme 渲染 OK |
| **M-2** | mp-01/02/03/04 视觉对齐:5 分类入口 / Hero / 6 卡瀑布 / 4 chips / 4 tab;左侧分类 + 右侧 2 列瀑布;商品大图 / 价格 / stepper / 3 按钮;购物车卡 / 全选 / stepper / 结算栏 | mp-01~04 | 8d | M-1 | 4 屏在 WeChat DevTools 渲染与 OD HTML 视觉差异 < 5%;E2E 跑通"首页 → 加购 → 购物车" |
| **M-3** | mp-08 订单状态机核心:5 状态 → 5 操作按钮(取消/付款/提醒发货/确认收货/再次购买);后端 4 端点封装 | mp-08 | 5d | M-1, M-2 | 5 状态各 1 个 E2E;`orders.*` 3 counter 正确埋点 |
| **A-1** | ad-01 登录:React 18 表单 + 失败 3 次锁 + `users.login.attempts{result=locked}` 联调 | ad-01 | 2.5d | M-1 | E2E 跑通"登录 → 进 ad-02";失败 3 次锁 15 分钟;locked counter 埋点 |
| **A-2** | ad-02 仪表盘:4 KPI + 7 天趋势(Recharts)+ 近期订单 + 库存预警;后端 `GET /api/admin/dashboard` 补 `trend7d` + `lowStock` 字段 | ad-02 | 5d | M-1, 后端补字段 | E2E 跑通"登录 → 看 4 KPI → 跳近期订单" |
| **A-3** | ad-03 商品列表:shadcn DataTable + 筛选 + 批量 + 单行 + 导出 CSV;后端扩 `/api/admin/products/{id}/duplicate` + `/api/admin/products/export` | ad-03 | 5d | M-1, M-2(共享 token) | E2E 跑通"筛选 → 批量上架 → 导出";duplicate 端点正确复制 |

**Must 小计:30.5 eng-day**

### 🟨 Should(5 项,v2 视觉后做以补全体验)

| 编号 | 范围 | 屏 | 工时 | 依赖 | 验收 |
|---|---|---|---|---|---|
| **S-1** | mp-05/06/07 视觉对齐:用户卡 + 4 状态卡 + 工具列表;地址 + 商品清单 + 配送方式 + 备注 + 金额明细 + 提交;地址列表 + 编辑 + 默认 + 选择模式 | mp-05~07 | 7d | M-1, M-2 | 3 屏视觉对齐;E2E 跑通"登录 → 我的 → 改地址 → 下单" |
| **S-2** | 死交互修复(OD 示范的 `is-clicked` 模式落 WXSS):所有 `[data-action]` / `[data-toggle]` / chips / tab / 全选 / stepper 加 0.18s 闪一下 → 0.6s fade-out 反馈 | mp 跨屏 | 2d | M-1 | 视觉回归覆盖所有交互元素;`is-clicked` WXSS 定义生效 |
| **S-3** | mp-06 金额明细实时算 + 备注 max 50 字 + 配送方式切运费 | mp-06 | 2d | M-3(状态机) | 4 金额项实时联动;备注超 50 字阻断;配送切换运费变 |
| **A-4** | ad-04 商品表单:shadcn Form + 富文本 + 多图上传 UI + SKU 行内编辑;后端扩 `/api/admin/uploads` + `Product.skus` 字段 | ad-04 | 8d | M-1, 后端扩展 upload + SKU | E2E 跑通"新建商品 + 上传 3 图 + 加 2 个 SKU + 发布";SKU 字段正确落库 |
| **A-5** | ad-05 订单列表:shadcn DataTable + 状态 tabs + 批量操作(批量发货 / 导出);后端扩 `/api/admin/orders/batch-ship` + `/print-picklist` + `/export` | ad-05 | 5d | M-3(共享 OrderService) | E2E 跑通"筛选已付款 → 批量发货 → 拣货单打印";`orders.paid{...,batch=true}` counter 埋点 |

**Should 小计:24 eng-day**

### 🟦 Could(3 项,有价值但需后端扩展或超出 MVP)

| 编号 | 范围 | 屏 | 工时 | 依赖 | 验收 |
|---|---|---|---|---|---|
| **C-1** | mp-08 + ad-06 物流轨迹:`Order.tracking` 字段(对接第三方物流 API 或自填);mp-08 订单详情时间线;ad-06 时间线 | mp-08 + ad-06 | 5d | M-3, 后端扩 `Order.tracking` | 时间线 3 节点(已发货/运输中/已签收);`SHIPPED` 后 mp-08 可见 |
| **C-2** | mp-08 + ad-06 申请售后 + 退款审核:`COMPLETED` 后 mp-08"申请售后";ad-06 退款审核流;后端增 `REFUNDING` status + 退款表 + `orders.refunded` counter | mp-08 + ad-06 | 8d | M-3, 后端扩退款模型 | `POST /api/orders/{id}/refund` 走通;`REFUNDING` 状态可见;counter 埋点 |
| **A-6** | ad-06 订单详情:3 列布局 + 订单商品 + 用户信息 + 金额明细;物流 / 退款模块与 C-1/C-2 共享 | ad-06 | 5d | M-3, A-5;物流/退款待 C-1/C-2 | 若 C-1/C-2 未完,ad-06 标"待 C-1/C-2 落地",订单核心信息可看 |

**Could 小计:18 eng-day**

### ⬜ Won't(2 项,本路线图明确不做)

| 编号 | 范围 | 原因 |
|---|---|---|
| **W-1** | 评价系统(`COMPLETED` 后"评价"按钮) | 后端无评价模型;超出 MVP 范围;mp-08 按钮先占位 toast "开发中" |
| **W-2** | 外部商家接入(注册 / 入驻 / 多 seller / 自助门户 / 结算 / 分账) | 单卖家模型 = 1 个商家,内部运营;详见 `02-functional-ad.md` § 1.2 |

---

## 3. Sprint 切分建议

> **总工作量:72.5 eng-day**(Must 30.5 + Should 24 + Could 18,不含 Won't)
> **2-4 人协作,~7 周(1 + 2 + 2 + 2)收尾 v2 视觉 + admin MVP**

### Sprint 0 — 设计系统 + 脚手架(1 周,~5 eng-day)

| 任务 | 出处 | 工时 | owner |
|---|---|---|---|
| 写 `tokens.json`(19 token / 3 字体 / 6 圆角 / 3 阴影)+ `tokens.wxss` build step | M-1 | 2d | frontend design owner |
| 替换 mp 4 类 Row 组件 + 3 共享组件(Button / Empty / Loading) | M-1 | 1.5d | frontend eng |
| admin-ui 脚手架:Vite + React 18 + TS + Tailwind + shadcn 初始化;Tailwind theme 消费同一份 token | M-1 + A-1 前置 | 1d | admin-ui eng |
| 替换 `app.json` 颜色 + `app.wxss` `@import tokens.wxss` | M-1 | 0.5d | mp eng |

**验收**:
- mp 端:`npm test` 全部通过,覆盖率 ≥ 88%;4 类 Row 快照测试通过
- mp 端:WeChat DevTools 渲染新 token
- admin-ui:`npm run dev` 启动,空白页用新 token
- `docs/DESIGN.md` 已重写(6 条 posture)

### Sprint 1 — Must mp-01~04 + ad-01/02(2 周,~10 工作日)

**mp 路径(13 eng-day,1-2 frontend eng + 1 backend eng):**
| 任务 | 出处 | 工时 |
|---|---|---|
| mp-01 视觉对齐(5 分类 / Hero / 6 卡瀑布 / 4 chips / 4 tab) | M-2 | 3d |
| mp-02 视觉对齐(左侧分类 / 右侧 2 列 / chips) | M-2 | 1.5d |
| mp-03 视觉对齐(大图 / 价格 / stepper / 3 按钮) | M-2 | 1.5d |
| mp-04 视觉对齐(列表 / 全选 / stepper / 删 / 结算) | M-2 | 2d |
| mp-08 状态机(5 状态 × 5 操作按钮 + 4 端点封装) | M-3 | 5d |
| E2E"首页 → 加购 → 购物车 → 订单确认 → 提交 → 订单列表" | M-2+M-3 | 2d |

**admin 路径(7.5 eng-day,1 frontend eng + 1 backend eng):**
| 任务 | 出处 | 工时 |
|---|---|---|
| ad-01 登录(React 表单 + 失败 3 次锁联调) | A-1 | 2.5d |
| ad-02 仪表盘(4 KPI + Recharts 7d 趋势 + 近期订单 + 库存预警) | A-2 | 2d(frontend) |
| ad-02 后端补 `trend7d` + `lowStock` 字段 | A-2 | 2d(backend) |
| E2E ad-01/02 + ad 端 mock 鉴权 | A-1+A-2 | 1d |

**人力**:2-3 人(2 frontend + 1 backend),2 周 ≈ 10 工作日,部分 mp 路径(M-2 部分)可
由 admin frontend eng 顺带做 1-2 屏。

**验收**:
- mp 4 屏视觉与 OD HTML 差异 < 5%
- mp-08 5 状态各 1 E2E 路径
- ad-01/02 可登录 + 仪表盘数据正确(用 seed 数据)
- `orders.*` 3 counter 在 mp-08 状态切换时正确埋点

### Sprint 2 — Must ad-03 + Should ad-04 + mp-05~07(2 周,~10 工作日)

**admin 路径(13 eng-day,1 frontend + 1 backend eng):**
| 任务 | 出处 | 工时 |
|---|---|---|
| ad-03 商品列表(筛选 + DataTable + 批量 + 单行 + 导出) | A-3 | 2d(frontend) |
| ad-03 后端扩 `/duplicate` + `/export` | A-3 | 2d(backend) |
| ad-04 商品表单(shadcn Form + 富文本 + 多图上传 UI + SKU 行内编辑) | A-4 | 3d(frontend) |
| ad-04 后端扩 `/api/admin/uploads` + `Product.skus` 字段 | A-4 | 3d(backend) |
| ad-04 SKU 仓储 / 列表查询 / 验证 | A-4 | 2d(backend) |
| E2E"新建商品 + 上传 3 图 + 加 2 SKU + 发布" | A-3+A-4 | 1d |

**mp 路径(11 eng-day,1-2 frontend eng):**
| 任务 | 出处 | 工时 |
|---|---|---|
| mp-05 视觉对齐(用户卡 / 4 状态卡 / 工具列表) | S-1 | 1.5d |
| mp-06 视觉对齐(地址 / 商品清单 / 配送方式 / 备注 / 金额明细 / 提交) | S-1 + S-3 | 2d + 2d |
| mp-07 视觉对齐(列表 / 编辑 / 默认 / 选择模式) | S-1 | 1.5d |
| 死交互修复(`is-clicked` 落 WXSS) | S-2 | 2d |
| E2E"登录 → 我的 → 改地址 → 下单"完整路径 | S-1 | 2d |

**人力**:3-4 人(2 admin + 1-2 mp frontend),2 周 ≈ 10 工作日。
**风险**:A-4 SKU 领域扩张是 backend 1 个 owner 的重负载,可能滑到 Sprint 3 — 若滑,ad-04
Sprint 3 末才落地,商品表单 Sprint 2 仅做"无 SKU 的简化版"。

**验收**:
- mp 3 屏(05/06/07)视觉对齐
- ad-03 商品 CRUD 走通(含 duplicate)
- ad-04 基础商品 CRUD + 多图上传(若 SKU 滑到 Sprint 3,Sprint 2 末 SKU 行内编辑可 disabled)

### Sprint 3 — Should ad-05 + Could 收尾(2 周,~10 工作日)

**mp Could 路径(13 eng-day,1 frontend + 1 backend):**
| 任务 | 出处 | 工时 |
|---|---|---|
| C-1 物流轨迹(`Order.tracking` 字段 + mp-08 时间线) | C-1 | 2d(backend) + 2d(frontend) + 1d E2E |
| C-2 退款模型(`REFUNDING` status + 退款表 + 退款审核) | C-2 | 3d(backend) + 2d(frontend) + 1d E2E |
| C-2 退款 counter `orders.refunded` 埋点 | C-2 | 0.5d |
| 滑入:mp-08 状态机剩余(若 Sprint 1 没做完)+ mp-08 收尾 | M-3 收尾 | 1.5d |

**admin 路径(10 eng-day,1 frontend + 1 backend eng):**
| 任务 | 出处 | 工时 |
|---|---|---|
| ad-05 订单列表(状态 tabs + DataTable + 批量发货 + 拣货单 + 导出) | A-5 | 2d(frontend) + 2d(backend) + 1d E2E |
| ad-06 订单详情(3 列布局 + 订单商品 + 用户信息 + 金额明细) | A-6 | 2d(frontend) + 1d(backend payload 确认) + 0.5d E2E |
| 滑入:Sprint 2 A-4 SKU 收尾(若滑) | A-4 收尾 | 1.5d |

**人力**:3-4 人(2 mp + 2 admin 或 1 mp + 2 admin + 1 backend owner),2 周 ≈ 10 工作日。
**风险**:后端 1 个 owner 同时支持 C-1(物流) + C-2(退款) + A-5(批量) + A-6(payload) 4 条线,
负载重。建议 backend owner 优先做 C-1 + A-5 读路径,C-2 + A-6 滑到 Sprint 4 风险。

**验收**:
- mp-08 物流 / 售后 可见(若后端 C-1/C-2 完成)
- ad-05 单订单 + 批量发货走通
- ad-06 订单核心信息可看(物流/退款模块标"待 C-1/C-2 落地"也可接受)
- 所有 Could / A-6 在 Sprint 3 末做最后决策:落地 / 滑到 Sprint 4 / 降级

---

## 4. 总览 Sprint 切分表(精简版)

| Sprint | 时长 | mp 路径 | admin 路径 | 跨屏 / 依赖 |
|---|---|---|---|---|
| **0** | 1 周 | tokens + 4 Row + 3 shared + app.json | admin-ui 脚手架 + Tailwind theme | `docs/DESIGN.md` 重写 |
| **1** | 2 周 | M-2 (mp-01~04) + M-3 (mp-08 状态机) + E2E | A-1 (ad-01) + A-2 (ad-02) + E2E | mp-08 ↔ ad-05/06 共享 OrderService |
| **2** | 2 周 | S-1 (mp-05~07) + S-2 (死交互) + S-3 (金额明细) | A-3 (ad-03) + A-4 (ad-04 + upload + SKU) | 共享 OKLch token |
| **3** | 2 周 | C-1 (物流) + C-2 (退款) + mp-08 收尾 | A-5 (ad-05) + A-6 (ad-06) | 后端 owner 重负载,可能滑到 Sprint 4 |

---

## 5. 与 Sprint 2 已就位工作的关系

| 在飞工作 | 是否冲突 | 备注 |
|---|---|---|
| `setup-observability-stack` PR #15 | 无 | mp-08 / ad-05 操作埋点复用 `orders.*` counter;ad-01 登录复用 `users.login.attempts` |
| `setup-runbook-and-oncall` in-flight | 无 | 本路线图上线后,runbook 需补 1 段 "v2 视觉 + admin-ui" 发布前 design owner 验 14 屏 |
| `introduce-feature-flag-platform` in-flight | 低 | 若用 flag 控制 v2 视觉灰度,需在 Sprint 0 决定 "全员发布" 还是 "灰度";建议 Sprint 0 末决策 |
| `sprint2-native-security` PR #8 | 无 | 颜色/字体替换 + admin-ui 都不影响 GraalVM Native |
| `add-miniapp-e2e-tests` in-flight | 低 | E2E 路径本路线图会大量用上;若 in-flight 已写部分 mp-* E2E,本路线图覆盖它 |

---

## 6. 风险与缓解

| 风险 | 等级 | 缓解 |
|---|---|---|
| 后端 1 个 owner 同时支持 C-1 + C-2 + A-5 + A-6 + A-4 | 🔴 **高** | Sprint 3 前置讨论:哪些必交付,哪些可滑到 Sprint 4;MVP 降级 = 物流/退款延后 |
| Fraunces / Inter Tight / Geist Mono 包大小影响(mp 端) | 中 | Sprint 0 评估;若超预算,MVP 降级 = 1 套 Inter Tight(衬线 display 用 system serif 替代) |
| admin-ui React 脚手架 + shadcn 学习曲线 | 中 | Sprint 0 末 1 天 spike 跑通 1 个空 page;admin frontend eng 2 人至少 1 人有 React 经验 |
| SKU 领域扩张对 mp 端 `ProductCard` / 详情屏影响 | 中 | Sprint 2 末做 mp 端 SKU 选规格的 UI(若 ad-04 SKU 滑到 Sprint 3,mp 端相应延后) |
| 设计 owner 不在 | 高 | 必须 design owner 拍板"烤虾橙"是否被认,主色是品牌层决策;Sprint 0 启动前确认 |
| OKLch 在老 iOS(微信内置 WebView)兼容 | 低 | 微信小程序 8.0+ WebView 支持 OKLch |
| admin-ui 范围蔓延(把外部商家接入混进来) | 中 | `02-functional-ad.md` § 1.2 明确排除;Sprint review 时坚持 |
| 后端批量操作 + 导出对 MongoDB 性能影响 | 中 | Sprint 1 末 spike:1000 单批量发货 + 1 万行 CSV 导出,看 p99 |

---

## 7. 验收标准总览(全部 Sprint 收尾时统一过)

### mp 端(8 屏)

- [ ] mp-01~08 视觉与 OD HTML 差异 < 5%
- [ ] mp-08 5 状态各 1 E2E 路径
- [ ] mp-06 4 金额项实时联动 + 备注 max 50 字
- [ ] 死交互修复覆盖全部交互元素
- [ ] `orders.*` 3 counter 正确埋点
- [ ] `npm test -- --coverage` ≥ 88%
- [ ] `docs/DESIGN.md` 已重写
- [ ] `shared/tokens/tokens.json` + build step 落 `tokens.wxss`

### admin-ui 端(6 屏)

- [ ] ad-01 可登录,失败 3 次锁 15 分钟
- [ ] ad-02 4 KPI + 7d 趋势 + 近期订单 + 库存预警 4 模块可见
- [ ] ad-03 筛选 / 批量 / duplicate / 导出 4 功能可用
- [ ] ad-04 基础 CRUD + 多图上传 + SKU(若 Sprint 2 完成)
- [ ] ad-05 状态 tabs + 批量发货 + 拣货单 + 导出
- [ ] ad-06 订单核心信息可看;物流 / 退款模块标"待 C-1/C-2 落地"也可接受
- [ ] `npm test -- --coverage` ≥ 80%(admin 端)
- [ ] `admin-ui/tailwind.config.ts` 消费 mp 端 `tokens.json`(设计 parity)

### 跨屏

- [ ] `openspec/specs/mini-program/spec.md` 加 3 条 "ADDED Requirements"(订单状态机 / 地址管理 / 直购)
- [ ] 建议开 1 个 `openspec/changes/v2-visual-redesign/` change 归档本路线图
- [ ] mp + ad 共享同一份 `tokens.json` source of truth

---

## 8. 一句话总结

> **4 个 Sprint,7 周(2-4 人协作)收尾**:
> Sprint 0(1 周) = 落设计 token + admin-ui 脚手架;
> Sprint 1(2 周) = mp-01~04 + mp-08 状态机 + ad-01/02;
> Sprint 2(2 周) = mp-05~07 + 死交互 + ad-03/04;
> Sprint 3(2 周) = 物流 + 退款(Could)+ ad-05/06。
> 排除项:评价系统 + 外部商家接入。
> 最大风险:后端 1 个 owner 重负载 → 需 Sprint 3 前置对齐哪些必交付。
