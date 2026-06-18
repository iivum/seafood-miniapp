# Sprint 3 A 续-2 Coverage Gap — 0.5% remaining to 80%

> Jacoco gate raised 0.75 → 0.79 (16 cases 提了 coverage 76.5% → 79.3%)。0.5% 距 0.80,完整 0.80 阈值留后续 sub-change。

## 现状(2026-06-19 实测)

`./gradlew check` 跑完:
- 422 + 18 (A 续) + 17 (A 续-2) = **457 tests pass**
- Jacoco `instructions covered ratio`: **79.3%**(XML: 8803 covered / 11102 total)
- branch coverage 60% 附近
- 0.5% 缺口 = ~50 instructions,需要 1-2 个 case

## A 续-2 做了什么

4 个新 service-layer test 文件,17 cases:
- `CartServiceSliceTest`(4)— get/addItem/removeItem/clear
- `ProductServiceSkuSliceTest`(5)— listSkus/replaceSkus valid+tooMany/addSku
- `OrderServiceStateMachineSliceTest`(4)— cancel/markPaid/confirmReceive/rebuy
- `AdminBffDashboardSliceTest`(4)— topProducts empty/skip missing/trend7d/lowStock

提了 2.8pp(76.5 → 79.3)。剩 0.5% 在:
- `OrderService.requestRefund` state machine 路径(PAID → REFUNDING)— 本 change 跳过(spec 设计了但 Order.requireTransition 拒绝,需要先 stub 到 COMPLETED 状态;测试用例复杂度高,留未来)
- `ProductService.updateStatus` 多 status 转换路径
- 几个 AdminBffService 私有 helper 边缘 case

## Gate 临时 0.79

`backend/build.gradle` `minimum = 0.79`(从 0.75 升) — 79.3% > 0.79,gate 跑通。**未达 0.80**,完整阈值需后续 sub-change 补 1-2 case(预计 1h 内可解)。

完整收口后改 `minimum = 0.80` + 删本 file。
