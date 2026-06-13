# 海鲜商城 · OD 视觉系统重设计 + admin-ui 路线图(2026-06)

> 本文件是基于 Open Design 项目 `686e3434-0233-451e-9c99-debee025a336`(14 屏设计原型 v2)
> + 单卖家内部管理后台需求的**审计 + 现状对比 + 落地路线图**。OD 项目最终状态为 `failed`
> (MP 8 屏 + Admin 6 屏 + 索引全部成功生成),本文件消费的是已落盘的 HTML/CSS 资产。

---

## 0. 读者与决策摘要

### 读者群

| 角色 | 主要读 | 关注什么 |
|---|---|---|
| PM | `01-functional-mp.md` + `02-functional-ad.md` + `05-moscow-roadmap.md` | 功能拆解 + 优先级 + Sprint 切分 |
| 设计师 | `03-design-system.md` | Token / 字体 / posture / 跨 surface 同步 |
| 前端(mp) | `01-functional-mp.md` + `04-gap-analysis.md` | 屏 → 现状 → 差距 → 工作量 |
| 前端(admin) | `02-functional-ad.md` + `04-gap-analysis.md` § 6 | 6 屏 → admin-ui 脚手架 → 后端对接 |
| 后端 | `01-functional-mp.md` § "API 依赖" + `02-functional-ad.md` § 9 | BFF / Order 扩展 / SKU / 物流 / 退款 |
| 全员 | 本文件(索引) + `05-moscow-roadmap.md` | 路线图 |

### 决策摘要(6 项,2026-06-13 对齐)

| # | 决策 | 选择 | 影响 |
|---|---|---|---|
| 1 | 文档落点 | 纯 `docs/` 下的独立 .md | 不走 OpenSpec / superpowers 流程 |
| 2 | `ad-*` 6 屏处理 | **纳入 MoSCoW**(单卖家内部运营,见第 6 项) | 路线图排 14 屏 = 8 mp + 6 ad |
| 3 | MVP 格式 | MoSCoW 标签 | 1 张表 + 4 档颜色 |
| 4 | 文档结构 | 拆分小文件 + 索引 | 6 份 .md,主索引 ≤ 200 行 |
| 5 | 05 末尾 | 加"实施顺序建议"段 | Sprint 0/1/2/3 切分 + 验收 |
| 6 | admin-ui 范围 | **全 6 屏**(单卖家内部运营) | 明确**不**做:外部商家入驻/多 seller/自助门户/结算分账 |

---

## 1. 范围

### 1.1 包含

- **mp-01~08**(8 屏用户端) — 完整功能点拆解 + 现状对比 + MoSCoW
- **ad-01~06**(6 屏管理后台) — 完整功能点拆解 + 与 backend `/api/admin/**` 3 端点对齐盘点 + MoSCoW
- **OD 设计系统** — OKLch 11 token / 3 字体 / 6 圆角 / 3 阴影 / 6 posture(mp + ad 共享)
- **现状 vs OD 差距分析** — 屏一对一映射 + 设计系统替换面(mp + ad)
- **MoSCoW 排序 + Sprint 切分建议** — Sprint 0/1/2/3 共 7 周,2-4 人协作

### 1.2 不包含(明确排除)

- ❌ **外部商家入驻 / 多 seller / 多租户**(单卖家 = 1 个商家始终)
- ❌ **商家自助门户 / 商家结算 / 分账 / 提现**(无商家角色,只有内部运营)
- ❌ 真实微信支付对接(Sprint 3 起,`OrderService.markPaid()` 当前 mock)
- ❌ 评价系统(后端无评价模型,`COMPLETED` 后"评价"按钮先占位 toast"开发中")
- ❌ 现有可观测性 / runbook 重做(已有 PR #15 + in-flight,本路线图复用其 counter / runbook 模板)

### 1.3 与 `CLAUDE.md` / `openspec/specs/` 的关系

- 现行 `openspec/specs/mini-program/spec.md` § "Design-token parity with admin-ui" 提到
  "consume same tokens.json as admin UI"。本路线图首次让该 spec 真的被消费 — mp 与 ad 共享
  同一份 `tokens.json`
- `openspec/specs/admin-ui/spec.md` 当前是骨架;本路线图(尤其 `02-functional-ad.md` § 12
  admin-ui 技术栈)是该 spec 的首份实质 design input
- 建议启动本路线图 Sprint 0 时,同步开 1 个 `openspec/changes/v2-visual-redesign/`
  change 把 14 屏 + token 同步归档

---

## 2. 文件索引

```
docs/
├── redesign-requirements.md          ← 本文件(总览 + MoSCoW 简表 + Sprint 切分)
└── redesign/
    ├── 01-functional-mp.md           ← mp-01~08 8 屏功能点拆解
    ├── 02-functional-ad.md           ← ad-01~06 6 屏功能点拆解(已纳入 MoSCoW)
    ├── 03-design-system.md           ← OD 设计系统 token / 字体 / posture
    ├── 04-gap-analysis.md            ← 现状 vs OD 差距分析(mp + ad)
    └── 05-moscow-roadmap.md          ← MoSCoW 详细表 + Sprint 0/1/2/3 切分 + 验收
```

---

## 3. 总览 MoSCoW 表(详细见 `05-moscow-roadmap.md`)

| 档 | 数量 | mp 侧 | ad 侧 |
|---|---|---|---|
| **Must** | 6 | M-1 设计 token(mp+ad 共享);M-2 mp-01~04 视觉;M-3 mp-08 状态机 | A-1 ad-01 登录;A-2 ad-02 仪表盘;A-3 ad-03 商品列表 |
| **Should** | 5 | S-1 mp-05~07 视觉;S-2 死交互修复;S-3 mp-06 金额明细 | A-4 ad-04 商品表单;A-5 ad-05 订单列表 |
| **Could** | 3 | C-1 物流轨迹; C-2 退款模型 | A-6 ad-06 订单详情 |
| **Won't** | 2 | W-1 评价系统 | W-2 外部商家接入 |

**总工作量:72.5 eng-day**,2-4 人协作 ~7 周。

---

## 4. Sprint 切分建议(简版,详细见 `05-moscow-roadmap.md` § 3)

| Sprint | 时长 | mp 路径 | admin 路径 | 关键依赖 |
|---|---|---|---|---|
| **Sprint 0** | 1 周 | tokens + 4 Row + 3 shared + app.json | admin-ui 脚手架 + Tailwind theme | `docs/DESIGN.md` 重写 |
| **Sprint 1** | 2 周 | M-2 mp-01~04 + M-3 mp-08 状态机 | A-1 ad-01 + A-2 ad-02 | mp-08 ↔ ad-05/06 共享 OrderService |
| **Sprint 2** | 2 周 | S-1 mp-05~07 + S-2 死交互 + S-3 金额明细 | A-3 ad-03 + A-4 ad-04(upload + SKU) | 共享 OKLch token |
| **Sprint 3** | 2 周 | C-1 物流 + C-2 退款 + mp-08 收尾 | A-5 ad-05 + A-6 ad-06 | 后端 owner 重负载,可能滑到 Sprint 4 |

---

## 5. 已落定决策(2026-06-13,共 8 项)

> 8 项决策经分组讨论(Sprint 0 前置 3 项 + Sprint 1 前置 2 项 + Sprint 2/3/Backlog 3 项)逐项拍板。
> Q5 + Q6 都选了"高工作量"选项,意味着 Sprint 1 + Sprint 3 **无 MVP 降级空间**;若执行遇阻,
> 优先回退的是 Q5(后端 owner 全做)而非 Q6(账号管理在 Sprint 1)。

| # | 决策项 | 选项 | Sprint 落点 | Trade-off / 备注 |
|---|---|---|---|---|
| 1 | tokens.json 落点 | **A** `docs/redesign/tokens.json` 单一源 + build step 派生 mp `tokens.wxss` + admin `tokens.tailwind.ts` | Sprint 0 | 设计 owner 一处改,eng 两端受益;跨目录耦合 |
| 2 | Order 状态机后端支持 | **B** 退款新表 `Refund` + 新增 `Order.status = REFUNDING`;"提醒发货" = 派生(客户端提示) | Sprint 1 起 | Order 状态机变复杂但语义清晰;`COMPLETED → REFUNDING → REFUNDED` 流 |
| 3 | 字体加载 | **A** Sprint 0 末做字体子集化 spike(取数字+英文+常用汉字 ~500 字);mp `@font-face` 打包;admin `fontsource` npm | Sprint 0 末 | 子集化后 mp ~200KB / admin ~150KB;1d 字频统计 spike |
| 4 | mp-03 SKU 选规格 UI | **A** 底部 sheet(轻量,1 次额外点击) | Sprint 2 中 | 主详情页保持简洁;选完关闭;不放在主详情 tab |
| 5 | 后端 owner 负载 | **B** Sprint 3 全做 5 条线(C-1 物流 + C-2 退款 + A-5 批量 + A-6 payload + 滑入 A-4 收尾),**接受延期风险** | Sprint 3 | 🔴 高风险:1 owner 同时 5 条线;无降级缓冲;遇阻回退 Q5 |
| 6 | 账号管理优先级 | **B** Sprint 1 就做账号 CRUD(后端 `/api/admin/users` 表 + 前端 ad-07 屏,多 3-4d) | Sprint 1 扩 | Sprint 1 总工作量原估 21d → ~25d(2-3 人 12 工作日 ≈ 仍可 2 周) |
| 7 | admin-ui 部署 | **A** 第 3 独立 image(Vite build 产物 + nginx),`docker-compose.yml` 加 `admin-ui` 服务 | Sprint 1 末 | 职责清晰;`depends_on: backend` + env 配 `VITE_API_BASE_URL` |
| 8 | admin-ui 鉴权 | **A** Sprint 1 启动就走 httpOnly Cookie(后端 `/api/admin/auth/cookie-login` 端点 + XSS/CSRF 防护 + logout 端点) | Sprint 1 启动 | 不走 localStorage 中间态;后端多 1-2d(cookie 端点 + CSRF token) |

---

## 6. 引用

- OD 项目入口:`http://127.0.0.1:49317/api/projects/686e3434-0233-451e-9c99-debee025a336/raw/index.html`
- OD 项目目录:`/Users/linbinghui/Library/Application Support/Open Design/namespaces/release-stable/data/projects/686e3434-0233-451e-9c99-debee025a336`
- 现行 spec:`openspec/specs/mini-program/spec.md` / `openspec/specs/admin-ui/spec.md`
- `CLAUDE.md` § 项目架构 / § 管理后台架构 / § 设计准则 / § 安全要求
- 现行后端 admin 端点:`backend/src/main/java/com/seafood/bff/admin/AdminBffController.java`
- 设计系统对比基线:`docs/DESIGN.md`(待按本路线图重写)
