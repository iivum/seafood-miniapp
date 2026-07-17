# Proposal: fix-order-amount-contract

## Why

2026-07-13 E2E 实测:结算页前端硬编码「满100减10」占位优惠(`order-confirm.js` 本地计算),向用户承诺实付 ¥135.11;后端创建的订单 `totalAmount=145.11`(后端无任何优惠逻辑),订单列表/详情随即显示 145.11。**用户可见金额自相矛盾**——结算承诺什么、订单就该是什么,这是电商金额契约的底线,真实支付接入后会直接变成资损/客诉。

**根因排查后发现范围更大**:不只是优惠——`shippingMethod`(配送方式:免运费/顺丰12/中通8)从未传给后端,`OrderService.persistOrder` 的 `totalAmount` 只是 `Σ 商品小计`,完全不含运费也不含优惠。整个「运费+优惠」摘要卡都是前端本地幻觉。只修优惠、不修运费是同一类缺口的局部修复,故本 change 范围扩大为两者一并下沉到后端。

## What Changes

- **单一定价权归后端**:`CreateOrderRequest` 新增 `shippingMethod` 字段;后端按配送方式查运费表 + 满 100 减 10 规则计算 `shippingFee`/`discount`,`totalAmount = 商品小计 + shippingFee - discount`
- mp 结算页保留本地预估计算(即时反馈配送方式切换),但提交订单后的所有金额展示(成功页/订单列表/订单详情)一律改为读取后端返回值,不再用本地计算值
- 补测试:① mp Jest——预估规则常量与后端保持一致 + 提交后展示源改为后端字段;② 后端单测/IT——`totalAmount` 计算纳入运费与优惠且不回归退款上限/CSV导出/metrics 分桶等下游;③ E2E 断言「结算页预估 == 创建后订单 totalAmount」

## Capabilities

- **New Capabilities**: 无
- **Modified Capabilities**:
  - `mini-program`:结算页金额展示要求改为「提交后必须与后端订单金额一致,预估计算规则须与后端同步」
  - `backend-api`:`POST /api/orders` 请求/响应契约补充 `shippingMethod`/`shippingFee`/`discount` 字段

## Impact

- 前端:`frontend/pages-sub/order-confirm/`(新增字段提交 + 展示源切换)、下单成功/订单列表/订单详情页
- 后端:`order/api`(DTO)、`order/domain`(Order 聚合字段)、`order/application`(OrderService.persistOrder 计算逻辑)、`order/infra`(OrderDocument)
- 下游需一并核实不回归:退款金额上限校验、`orders.paid` amountBucket 分桶、订单 CSV 导出
- 测试:mp Jest + 后端单测/IT + E2E 金额对账断言
- 证据:E2E 报告 j5-order-confirm.png(135.11)vs DB 订单 145.11
