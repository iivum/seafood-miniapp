# Tasks: Sprint 3 A 续-2 — Service-Layer Coverage Completion

> 实施细节见 `design.md`(9 节)。本文件为 OpenSpec apply 入口的 checkbox 跟踪。

## 1. Service-layer unit tests(4 个 test 类,17-18 cases)

- [x] 1.1 写 `CartServiceSliceTest`(4 cases):get / addItem / removeItem / clear
- [x] 1.2 跑 CartServiceSliceTest,验 4/4 PASS
- [x] 1.3 写 `ProductServiceSkuSliceTest`(5 cases):listSkus not-found / replaceSkus valid / replaceSkus tooMany / addSku sortOrder / removeSku re-order
- [x] 1.4 跑 ProductServiceSkuSliceTest,验 5/5 PASS
- [x] 1.5 写 `OrderServiceStateMachineSliceTest`(5 cases):cancel / markPaid / confirmReceive / rebuy / requestRefund
- [x] 1.6 跑 OrderServiceStateMachineSliceTest,验 5/5 PASS
- [x] 1.7 写 `AdminBffDashboardSliceTest`(3-4 cases):topProducts aggregation / topProducts catch missing / trend7d / lowStock
- [x] 1.8 跑 AdminBffDashboardSliceTest,验 3-4/3-4 PASS

## 2. Threshold 调整 + coverage-gap 同步

- [x] 2.1 改 `backend/build.gradle` `jacocoTestCoverageVerification.minimum` 从 0.75 升到 0.80
- [x] 2.2 删 `openspec/changes/sprint-3-ci-speed/coverage-gap.md`(跟踪 purpose 结束)
- [x] 2.3 跑 `./gradlew check -PexcludeTags=docker -x processTestAot` 验 gate @ 0.80 通过
- [x] 2.4 跑 `./gradlew jacocoTestReport` 验 coverage ≥ 80%(HTML report index.html Total ≥ 80%)

## 3. Final commit + archive

- [x] 3.1 Commit 4 个 test 文件(每文件 1 commit,共 4)
- [x] 3.2 Commit build.gradle 阈值调整(1 commit)
- [x] 3.3 Commit coverage-gap.md 删除(1 commit)
- [x] 3.4 标记本 change `tasks.md` 全 ✅
- [x] 3.5 跑 `/opsx:archive sprint-3-coverage-a-cont-2`
- [x] 3.6 更新 `openspec/changes/test-suite-roadmap/tasks.md` T5 勾 "Sprint 3 全部 4 子项目完成" + 删本 change 设计稿的 3.5% 缺口

## 4. Reference

- Spec: `specs/service-layer-coverage-completion/spec.md`(5 R, 21 S)
- Design: `design.md`(9 节,200 行)
- Proposal: `proposal.md`
- 父路线图:`test-suite-roadmap/design.md` §3.2 Sprint 3
- 3.5% 缺口跟踪:`openspec/changes/sprint-3-ci-speed/coverage-gap.md`(本 change 删)
- 前置:`sprint-2-test-data-builders` + `sprint-2-backend-coverage` + `sprint-3-ci-speed` + `sprint-3-coverage-a-cont`(均已 archive)
- 后续:Sprint 4 D3(coverage dashboard)+ C1(PIT)+ C3(k6)
