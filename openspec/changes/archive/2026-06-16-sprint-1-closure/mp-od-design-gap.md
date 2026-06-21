# 2026-06-18 · mp 8 屏 vs OD 设计 GAP 报告

> 自动化测试网已就位:`frontend/e2e/mp-od-design.test.ts`。
> TDD RED 阶段:38 tests, **16 FAIL / 22 PASS**。
> 跑:`TZ=UTC npx jest e2e/mp-od-design.test.ts --runInBand`

## 1. 测试覆盖矩阵

| 层 | 描述 | 测的源 | 数量 |
|---|---|---|---|
| L4 颜色 | chroma.js 验 tokens.wxss build 产物 + OD status 徽标颜色 | tokens.wxss | 7 tests |
| L1 结构 | 8 屏 OD 关键元素在 wxml 存在性 | 8 wxml files | 31 tests |

## 2. PASS / FAIL 分布

| 屏 | PASS | FAIL | 缺什么 OD 元素 |
|---|---|---|---|
| **mp-01 home** | 2/5 | **3** | 顶部地址+搜索框 / 时令-上新-促销 标签 / 推荐商品大卡 |
| **mp-02 category** | 2/4 | **2** | 右侧 featured banner / "人气 TOP 6 · 本季新品" section header |
| **mp-03 product-detail** | 4/4 | 0 ✓ | (完整) |
| **mp-04 cart** | 3/5 | **2** | 顶部"购物车 · N"标题 / "一起买" 推荐区 |
| **mp-05 profile** | 1/3 | **2** | 订单状态 row / 设置/菜单 list |
| **mp-06 order-confirm** | 0/6 | **6** | 全部 OD 关键元素都缺(本页面 v1 时代就只是 placeholder) |
| **mp-07 address** | 1/2 | **1** | "新增地址" 按钮 |
| **mp-08 order-list** | 2/2 | 0 ✓ | (完整) |
| **L4 token parity** | 7/7 | 0 ✓ | 12 token + 6 status 颜色 |
| **合计** | **22/38** | **16** | |

## 3. 16 个 RED 详情(按屏分组)

### mp-01 home (3)
- ✗ 顶部地址 + 搜索框 — OD 截图:地址"厦门高崎码头" + 搜索框;当前 v2 无
- ✗ 时令/上新/促销 标签 — OD 3 个 chip 在 banner 下方单独一行;当前 v2 无
- ✗ 推荐商品大卡(2 列) — OD 是 2 大卡"波士顿龙虾 ¥288 + 大黄鱼 ¥199";当前 v2 是 6 卡瀑布

### mp-02 category (2)
- ✗ 右侧 featured banner — OD 棕红色"波龙季 返场"卡;当前 v2 无
- ✗ "人气 TOP 6" / "本季新品" section header — OD 文案;当前 v2 无

### mp-04 cart (2)
- ✗ 顶部"购物车 · 3" + 编辑按钮 — OD 标"购物车 · 3" + 右侧"编辑";当前 v2 无
- ✗ "一起买" 推荐区 — OD 底部"3 张推荐图 + 去看看";当前 v2 无

### mp-05 profile (2)
- ✗ 订单状态 row — OD "待付款/待发货/待收货/已完成"4 tab;当前 v2 无
- ✗ 设置/菜单 list — OD list 行;当前 v2 无

### mp-06 order-confirm (6)
- ✗ 收货地址 / 商品清单 / 配送方式 / 备注 / 金额明细 / 提交按钮
- v1 时代这个页面**就是 placeholder**,sprint 1 闭合前没真实实现(只是 layout stub)

### mp-07 address (1)
- ✗ "新增地址" 按钮 — OD 底部 sticky;当前 v2 缺

## 4. GREEN 阶段建议(分批)

### 批次 A(2-3h,改 wxml/wxss/data)
- **mp-01 home 3 fail** — 加 banners 字段 / 5 chip / 标签筛选 / 改 6 卡瀑布为 2 大卡 OD 风格
- **mp-02 category 2 fail** — 加 cat-banner / section header
- **mp-04 cart 2 fail** — 顶部 N 标题 / "一起买" 区
- **mp-07 address 1 fail** — sticky 新增按钮

### 批次 B(半天,~10 文件,需要 design)
- **mp-05 profile 2 fail** — 订单状态 row + 设置 list(需 OD layout 推断)
- **mp-06 order-confirm 6 fail** — 整个页 v1 placeholder,需要从 v1 重写,工作量大

### 不动(本轮已正确)
- mp-03 product-detail ✓ — sprint 1 closure 已实现
- mp-08 order-list ✓ — sprint 1 closure 已实现
- L4 token parity ✓ — design tokens 都 build

## 5. 5/8 屏的 v1 还原(sprint 1 闭合前已修的 mp 布局)

之前 v2.1 signoff 期间修了 4 个 mp 布局 bug:
- `usingComponents: {}` → 6 个页面注册 shared-empty/loading(Vant)
- `wx.request` GET 过滤 undefined → home 6 卡瀑布加载成功
- `getErrorMessage` 默认值 → '未知错误' 改 ''
- `.home-chips` 加 overflow-x:auto → Skyline scroll-view 修复

修后 mp-01 home 实际渲染:6 卡瀑布 20 商品可见。**但当前 wxml 仍不是 OD 设计**(6 卡瀑布 vs OD 2 大卡),**视觉/数据分两件事**。

## 6. 现状

- **测试网(RED 阶段)已 commit**(可作为 v2.2 路线图)
- **16 个 FAIL 是 roadmap** — sprint 2/3 逐屏修
- **不能 sprint 1 闭合前全修** — mp-06 整个页面重写工作量大,需要 design
- **Sprint 1 闭合已 commit** — 当前 v2.1 是"按 checkpoint 验收项 + 修发现的 bug"状态

## 7. 下一步建议

| 优先级 | 项 | 估时 |
|---|---|---|
| P0 | 批次 A(9 fail) | 2-3h |
| P0 | mp-04 cart 顶部 N 标题 + 一起买 + mp-07 sticky | 1h |
| P1 | 批次 B(mp-05 profile 2 fail) | 半天 |
| P1 | 批次 B(mp-06 order-confirm 6 fail) | 半天 — 需 design |
| P2 | token 硬 fail 改 hard fail | 0.2h |
| P2 | 改 banner 字段 + chip 数量(OD 5 chip) | 0.5h |

**建议**:本轮**只 commit RED 测试网**(16 fail roadmap 留 Sprint 2),不再动 wxml/wxss 大改 — sprint 1 已闭合,大改应走设计 review。
