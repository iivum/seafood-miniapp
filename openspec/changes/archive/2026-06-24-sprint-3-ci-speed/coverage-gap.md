# Sprint 3 B Coverage Gap — 4% remaining to 80%

> Jacoco gate WORKED 两次。18 个新 service-layer unit test 把 coverage 从 75% 提到 76.5%,还差 3.5% 到 80% 阈值。Gate 仍 @ 0.75 — A 续 部分收口,完整收口留后续 sub-change。

## 现状(2026-06-19 实测)

`./gradlew check` 跑完:
- 422 + 18 = 440 tests pass
- Jacoco `instructions covered ratio`: **76.5%**(XML: 8497 covered / 11102 total)
- branch coverage 60% 附近
- 4% 缺口 = 4420 missed instructions(76.5% → 80% 还需 ~390 covered)

## A 续(本次 change)做了什么

4 个新 service-layer unit test 文件,18 cases:
- `OrderServiceSliceTest`(6) — batchShip partial / findRecent / listRefunds / get / ship / 等等
- `ProductServiceSliceTest`(6) — listPublic null/non-null / get/delete/listSkus not-found / decrementStock
- `UserServiceSliceTest`(3) — findByOpenId 路径
- `AdminBffServiceSliceTest`(3) — productStats / orderDetail paths

提了 1.5%(75 → 76.5)。剩 3.5% 在:
1. **BFF aggregation 完整路径** — `AdminBffService.dashboard()` 内部 `topProducts()` 走 `findRecent(500)` + 内存聚合 + `products.get(id)` catch NotFoundException,需要至少 2-3 个 test case
2. **trend7d() helper** — 7 天趋势点聚合,需要 1 个 test case
3. **Product.replaceSkus / addSku / updateSku / removeSku / listSkus** — 4-5 个 SKU 操作方法,虽然 `replaceSkus > 50` 测了一个,但其它未测
4. **Order state machine 边缘** — `Order.transition()` 的所有 `OrderAction` 分支(目前只测了 ship)
5. **CartService** — 完全没有 service-layer unit test(只测了 controller)

## 完整收口需 ~15-20 个 test case

预计再 1 个 change(可叫 `sprint-3-coverage-a-cont-2` 或类似):
- `AdminBffService` dashboard 内部 helpers(topProducts / trend7d)+ recentOrders
- `ProductService` SKU 系列方法
- `OrderService` state machine 全分支
- `CartService` 基本 CRUD
- 也许 `UserService` addAddress / updateAddress / removeAddress(4 个方法)

## Gate 临时维持 0.75

`backend/build.gradle` 改 `minimum = 0.80` 会 fail 当前 76.5% 跑不过 `check`。暂维持 0.75 gate 让本 change 通过(否则 CI 红)。注释里写明 "A 续 部分完成,真正 0.80 阈值需后续 sub-change"。

完整收口后,改 `minimum = 0.80` + 删本 file。
