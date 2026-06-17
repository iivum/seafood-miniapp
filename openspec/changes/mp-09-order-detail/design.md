# mp-09 Order Detail 设计(海鲜商城 · 微信小程序)

> 2026-06-18 起草 · Sprint 1 closure 后 OD 设计稿补全
> 状态: 已批准(用户对 4 个澄清问题 + 3 个设计 section 全部选 OK)

## 1. 背景与目标

### 1.1 现状

`mp-09 order detail` 是海鲜商城小程序的**订单详情页**,从 `mp-08 order list` 卡片 tap 进入。当前 v2.1 signoff 后端有 `GET /api/orders/{id}` + `OrderResponse`,但**前端页面 `pages-sub/order/order-detail/` 不存在**(v2 路线图 S-1 标 known gap,Sprint 2 实现)。

OD 设计稿已出(`mp-09-order-detail.html`,28117 字节,带 Fraunces 衬线 + oklch 色系),含 5 个 section + 3 个底部 action。

### 1.2 目标

- 实现 mp-09 页面,布局严格对齐 OD 设计稿
- 页面数据真接后端(`GET /api/orders/{id}` 一次拉够)
- 3 个底部 action 全接业务(refund / confirm / logistics)
- 3 层断言覆盖(结构 / 数据 / 行为)+ 颜色 token parity

### 1.3 非目标

- ❌ 接入第三方顺丰/京东物流 API(Sprint 2+)
- ❌ 退款表单多步流程(本期用 `wx.showModal` 单步确认;后端用既有 `POST /api/orders/{id}/refund` 默认 reason)
- ❌ timeline 6 节点细分(本期只显示 3 个真实节点)

## 2. 架构

### 2.1 后端改动(2 改 1 加)

| 文件 | 改动 |
|---|---|
| `order/domain/Order.java` | 加 `estimatedDelivery: Instant` 字段(aggregate root,不可变) |
| `order/api/dto/OrderResponse.java` | 加 `estimatedDelivery` record 字段 + `from(Order)` 透传 |
| `order/application/OrderService.java#create` | 创建订单时 `estimatedDelivery = Instant.now().plus(Duration.ofHours(24))` |

**后端零数据库迁移**:`OrderDocument.estimatedDelivery` 新字段,null 时 Spring Data Mongo 不写,旧订单无此字段反序列化为 null(向后兼容,零迁移)。

### 2.2 前端改动(4 新 + 2 改)

| 文件 | 改动 |
|---|---|
| `pages-sub/order/order-detail/order-detail.js` | 新 — onLoad + 3 action handler + 2 derive 函数 |
| `pages-sub/order/order-detail/order-detail.wxml` | 新 — 5 section + bottom-bar(对齐 OD) |
| `pages-sub/order/order-detail/order-detail.wxss` | 新 — 颜色 token 复用 `frontend/src/shared/tokens/tokens.wxss` |
| `pages-sub/order/order-detail/order-detail.json` | 新 — navigationStyle: custom,usingComponents: shared-loading/shared-empty |
| `pages-sub/order/order-list/order-list.wxml` | 改 — 卡片 `bindtap` 跳 `order-detail?id={id}` |
| `frontend/app.json` | 改 — pages 数组加 `pages-sub/order/order-detail/order-detail` |

### 2.3 测试改动(2 改)

| 文件 | 改动 |
|---|---|
| `frontend/e2e/mp-od-design.test.ts` | `OD_SPECS` 数组加 `mp-09-order-detail` spec,7 个 required class + 3 个 token |
| `frontend/e2e/mp-3layer.test.ts` | `PAGES` 数组加 `mp-09-order-detail`(storage 注入 userInfo + token,url 用真实 mongo _id) |

## 3. 数据流

### 3.1 onLoad

```
1. onLoad(options) 拿 options.id
2. wx.showLoading({ title: '加载中...' })
3. request({ url: '/api/orders/{id}', method: 'GET' })
   → 200: OrderResponse { id, userId, items[], totalAmount, status, tracking?, refundId?, estimatedDelivery?, createdAt, updatedAt }
4. setData({ order, statusBanner: deriveBanner(order), timeline: deriveTimeline(order) })
5. wx.hideLoading()
```

### 3.2 applyRefund(用户点"申请退款")

```
1. wx.showModal({ title: '申请退款', content: '确定要申请退款?' })
2. confirm → POST /api/orders/{id}/refund
   200:  new OrderResponse(status='REFUNDING', refundId='xxx')
3. setData({ order: newOrder, statusBanner: deriveBanner(newOrder) })
4. wx.showToast({ title: '退款申请已提交', icon: 'success' })
5. 错误:wx.showToast({ title: err.message, icon: 'none' }) + 保留原 data
```

### 3.3 confirmReceive(用户点"确认收货")

```
1. wx.showModal({ title: '确认收货', content: '请检查海鲜鲜度后再确认' })
2. confirm → POST /api/orders/{id}/confirm
   200:  new OrderResponse(status='COMPLETED', tracking.deliveredAt=now)
3. setData({ order: newOrder, statusBanner: deriveBanner(newOrder) })
4. wx.showToast({ title: '已确认收货', icon: 'success' })
```

### 3.4 viewLogistics(用户点"查看物流")

```
1. 订单 status != SHIPPED → wx.showToast({ title: '订单尚未发货', icon: 'none' })
2. 订单 status == SHIPPED:
   tracking.trackingNumber 存在 → wx.setClipboardData({ data: trackingNumber })
   → wx.showToast({ title: '物流单号已复制,请到顺丰/京东小程序查询', icon: 'none' })
   tracking.trackingNumber 为空 → wx.showToast({ title: '物流单号暂未生成', icon: 'none' })
```

## 4. 派生函数(纯函数,放 `utils/order-detail-derive.js`)

### 4.1 `deriveBanner(order)` → `{ statusText, statusColor, estimatedText, distanceText, actionText }`

| status | statusText | statusColor |
|---|---|---|
| PENDING | 待支付 | warning(amber) |
| PAID | 待发货 | info(blue) |
| SHIPPED | 冷链在途 | success(green)— OD 设计稿用这个 |
| COMPLETED | 已签收 | neutral(gray) |
| CANCELLED | 已取消 | error(red) |
| REFUNDING | 退款中 | error(red) |
| REFUNDED | 已退款 | neutral(gray) |

- `estimatedText`: `estimatedDelivery ? '预计 ' + format(estimatedDelivery, 'HH:mm') + ' 前送达' : null`
- `distanceText`: `tracking?.trackingNumber ? '顺丰 ' + tracking.carrier + ' · ' + tracking.trackingNumber : null`(本期不接距离 API,只显示物流公司 + 单号)
- `actionText`:SHIPPED 状态时显示「查看物流 / 联系客服」,其他状态显示「再次购买」

### 4.2 `deriveTimeline(order)` → `[{ label, time, desc, state }]` 长度 3

| 顺序 | label | time | desc | state |
|---|---|---|---|---|
| 1 | 下单成功 | `format(order.createdAt, 'MM-DD HH:mm')` | 订单已提交 | `done` |
| 2 | 商家拣货 | `order.tracking?.deliveredAt ? 'completed' : format(order.updatedAt, 'MM-DD HH:mm')` | 商家已完成拣货 | `order.status` in `[SHIPPED, COMPLETED]` → `done` else `current` |
| 3 | 顺丰揽收 | `order.tracking?.deliveredAt ? format(order.tracking.deliveredAt, 'MM-DD HH:mm') : '—'` | 冷链运输中 | `order.status === COMPLETED` → `done` else `future` |

**严格 3 节点**,无 predictedAt 假数据。OD 设计稿的 6 节点全部 ignore,只保留前 3。

## 5. UI 布局(对齐 OD 设计稿 5 section + bottom-bar)

```
+--------------------------------------------+
|  ← 订单详情                          💬   | topbar
+--------------------------------------------+
|  ●  冷链在途 · 顺丰 SF1024              | status-banner
|     预计 14:30 前送达                   | (success-soft gradient)
|     顺丰 SF1024                         |
|     [查看物流]  [联系客服]              |
+--------------------------------------------+
|  物流轨迹                                | timeline-card
|  ●  下单成功    06-08 09:42              | (3 节点)
|  ●  商家拣货    06-08 11:18              |
|  ○  顺丰揽收    —                       |
+--------------------------------------------+
|  林一帆  138****8842  [家]              | address-card
|  📍 福建省厦门市思明区软件园二期...      |
+--------------------------------------------+
|  商品 · 2 件            [已支付]        | items-card
|  [img] 帝王蟹·俄罗斯直运                 |
|        活蟹·公蟹 4-5 斤/只·净膛         |
|        [活鲜] [-18℃ 冷链]               |
|                            ¥688 × 1 只  |
+--------------------------------------------+
|  商品小计              ¥856.00           | price-card
|  顺丰冷链配送          ¥18.00            |
|  新人立减              −¥30.00           |
|  ──────────────────────────────          |
|  实付                  ¥844.00           |
+--------------------------------------------+
|  订单编号      ORD-20260607-0186         | info-card
|  下单时间      2026-06-07 09:42:18       |
|  支付方式      微信支付                  |
|  配送方式      顺丰冷链 SF1024           |
+--------------------------------------------+
|  [申请退款]  [查看物流]  [确认收货]   | bottom-bar
+--------------------------------------------+
```

颜色:全部从 `tokens.wxss` 取(已有 success/info/warning/error/accent),与 mp-08 / mp-06 风格统一。**不用 OD 设计稿的 oklch 直接色值**(小程序不识别),转 hex(已由 token-parity 验过 WCAG)。

## 6. 错误处理(4 类)

| 类型 | 触发 | 处理 |
|---|---|---|
| 网络失败 | onLoad 时 wx.request fail | `wx.hideLoading` + `wx.showToast('加载失败,请重试')` + 显示 retry 按钮(绑 onRetry) |
| 404 订单不存在 | HTTP 404 | `wx.showModal({ content: '订单不存在', showCancel: false, success: () => wx.navigateBack() })` |
| 401 / 403 未登录 | HTTP 401/403 | `wx.showModal({ content: '请先登录', showCancel: false, success: () => wx.navigateTo({ url: '/pages-sub/user/login/login' }) })` |
| action 失败 | POST /refund 或 /confirm fail | `wx.showToast({ title: err.message, icon: 'none' })` + 保留原 data,不刷新 |

**action 错误不刷新的理由**:用户看到失败 toast 后能立刻重试,data 不变避免页面闪动。

## 7. 测试(3 层 TDD)

### 7.1 TDD 顺序(强约束 — 看过 RED 再写 GREEN)

| 步骤 | 任务 | 期望 RED |
|---|---|---|
| 1 | 后端 `Order.estimatedDelivery` 字段加 | `OrderResponseJsonTest.estimatedDeliveryRoundTrip` 编译失败(red) |
| 2 | 写 `mp-od-design.test.ts mp-09` spec | 找不到 `order-detail.wxml` → 7/7 fail(red) |
| 3 | 写 `mp-3layer.test.ts mp-09` PAGES 项 | 找不到页面 → 3/3 fail(red) |
| 4 | GREEN:后端加字段 + mp-09 4 文件 | 3 测试都过(green) |
| 5 | REFACTOR:`deriveBanner` / `deriveTimeline` 抽到 utils | 测试仍过 |

### 7.2 测试详情

**L4 颜色**:`token-parity.test.ts` 已存在,自动跑所有 token,本期新增 0 测试。

**L1 结构 + OD 对齐**:`mp-od-design.test.ts` `mp-09-order-detail` spec:
- required: `status-banner` / `timeline-card` / `tl-node` / `addr-card` / `items-card` / `price-card` / `info-card` / `bottom-bar`
- tokens: `status-success` / `status-info` / `status-warning` (ratio >= 4.5)

**L1+L2+L3 e2e**:`mp-3layer.test.ts` PAGES 加:
- name: `mp-09-order-detail`
- url: `/pages-sub/order/order-detail/order-detail?id={mongo _id}`
- storage: userInfo + token
- wxmlMust: `[/status-banner/, /timeline-card/, /addr-card/, /items-card/, /price-card/, /info-card/]`
- dataMust: `['order', 'statusBanner', 'timeline']`
- fromBackend: `order: { id, status, items, totalAmount }` + `timeline.0: { label, time, state }`

## 8. 文件清单(本轮要改/加的)

### 新文件
1. `frontend/pages-sub/order/order-detail/order-detail.js`
2. `frontend/pages-sub/order/order-detail/order-detail.wxml`
3. `frontend/pages-sub/order/order-detail/order-detail.wxss`
4. `frontend/pages-sub/order/order-detail/order-detail.json`
5. `frontend/utils/order-detail-derive.js`
6. `frontend/utils/order-detail-derive.d.ts`
7. `openspec/changes/mp-09-order-detail/design.md`(本文件)

### 改文件
1. `backend/src/main/java/com/seafood/order/domain/Order.java`
2. `backend/src/main/java/com/seafood/order/api/dto/OrderResponse.java`
3. `backend/src/main/java/com/seafood/order/application/OrderService.java`
4. `backend/src/test/java/com/seafood/order/api/dto/OrderResponseJsonTest.java`(加 estimatedDelivery roundtrip)
5. `frontend/pages-sub/order/order-list/order-list.wxml`
6. `frontend/app.json`
7. `frontend/e2e/mp-od-design.test.ts`
8. `frontend/e2e/mp-3layer.test.ts`

## 9. Sprint 1 closure 关联

- `sprint1-closure-checkpoint` 标记 mp-05 / mp-09 详情页为 known gap
- 本 spec 关闭 **mp-09** gap(mp-05 等 mp-05 OD 设计稿)
- 关闭后 v2.1 signoff 14 屏 → 15 屏 mp 全部覆盖
- `mp-od-design.test.ts` mp-09 spec 加入后,**24 项 OD gap 减为 23**(mp-09 7 个 required)
- 端到端 `/api/admin/orders/{id}/detail` 验证在 task #30 端到端跑过(200 + 998 字符 JSON)
