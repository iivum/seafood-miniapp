# Tasks: Sprint 3 A 续 — Service-Layer Unit Test Backfill

> 实施细节见 `design.md`(9 节)。本文件为 OpenSpec apply 入口的 checkbox 跟踪。

## 1. Service-layer unit tests(4 个 test 类,19+ cases)

- [x] 1.1 写 `OrderServiceSliceTest`(6 cases):batchShip partial / findRecent truncation / listRefunds empty / renderPicklist not-found / requestRefund over-amount / rebuy cancelled
- [x] 1.2 跑 OrderServiceSliceTest,验 6/6 PASS(实验性 pilot — 验 5-arg constructor + MeterRegistry mock)
- [x] 1.3 写 `ProductServiceSliceTest`(6 cases):listPublic null/non-null / update not-found / updateStatus valid / decrementStock over-quantity / replaceSkus > 50
- [x] 1.4 跑 ProductServiceSliceTest,验 6/6 PASS
- [x] 1.5 写 `UserServiceSliceTest`(3 cases):role assignment on create / findByOpenId empty / findByOpenId found
- [x] 1.6 跑 UserServiceSliceTest,验 3/3 PASS
- [x] 1.7 写 `AdminBffServiceSliceTest`(4 cases):dashboard aggregation / productStats byCategory / orderDetail not-found / dashboard recentOrders
- [x] 1.8 跑 AdminBffServiceSliceTest,验 4/4 PASS

## 2. Threshold 调整 + coverage-gap 同步

- [x] 2.1 改 `backend/build.gradle` `jacocoTestCoverageVerification.minimum` 从 0.75 升到 0.80
- [x] 2.2 跑 `./gradlew check -PexcludeTags=docker -x processTestAot` 验 gate @ 0.80 通过
- [x] 2.3 改 `openspec/changes/sprint-3-ci-speed/coverage-gap.md` mark A 续 完成

## 3. Final commit + archive

- [x] 3.1 Commit 4 个 test 文件(每文件 1 commit,共 4)
- [x] 3.2 Commit build.gradle 阈值调整(1 commit)
- [x] 3.3 Commit coverage-gap.md 同步(1 commit)
- [x] 3.4 标记本 change `tasks.md` 全 ✅
- [x] 3.5 跑 `/opsx:archive sprint-3-coverage-a-cont`
- [x] 3.6 更新 `openspec/changes/test-suite-roadmap/tasks.md` T5 勾 "Sprint 3 全部 4 子项目完成"

## 4. Reference

- Spec: `specs/service-layer-tests/spec.md`(4 R, 14 S)
- Design: `design.md`(9 节,180 行)
- Proposal: `proposal.md`
- 父路线图:`test-suite-roadmap/design.md` §3.2 Sprint 3
- 5% 缺口跟踪:`openspec/changes/sprint-3-ci-speed/coverage-gap.md`
- 前置:`sprint-2-test-data-builders` + `sprint-2-backend-coverage` + `sprint-3-ci-speed`(均已 archive)
- 后续:Sprint 4 D3(coverage dashboard)+ C1(PIT)+ C3(k6)
