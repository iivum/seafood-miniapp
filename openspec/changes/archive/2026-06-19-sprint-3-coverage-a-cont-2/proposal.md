## Why

Sprint 3 A 续(`sprint-3-coverage-a-cont`)补了 18 个 service-layer cases,coverage 从 75% 提到 76.5%,**CLAUDE.md §3 目标 80% 仍未达成**。剩 3.5% 缺口在 4 个 service 的边缘路径(见 `openspec/changes/sprint-3-ci-speed/coverage-gap.md`):
1. **CartService** — 0% service-layer test(只测过 controller)
2. **ProductService** SKU 系列方法 — `replaceSkus` / `addSku` / `updateSku` / `removeSku` / `listSkus` 5 个方法未测(只测了 `decrementStock`)
3. **OrderService** state machine — `OrderAction` 6 个分支只测了 ship,缺 `cancel` / `pay` / `confirmReceive` / `rebuy` / `refund` / `remindShip` 全分支
4. **AdminBffService** dashboard 内部 helpers — `topProducts()` (findRecent 500 + 内存聚合 + products.get catch) / `trend7d()` 7 天趋势点 / `lowStock(10)` 三层 aggregation

A 续-2 收口 3.5% 缺口 + **最终把 `jacocoTestCoverageVerification` 阈值从 0.75 升回 0.80**(CLAUDE.md §3 硬规则),删 `coverage-gap.md`。

## What Changes

- **4 个新 service-layer test 类,~15-20 cases**:
  - `CartServiceSliceTest`(4 cases):get / addItem / removeItem / clear 路径 + 1 个异常路径
  - `ProductServiceSkuSliceTest`(5 cases):listSkus / replaceSkus / addSku / updateSku / removeSku
  - `OrderServiceStateMachineSliceTest`(5 cases):cancel / pay / confirmReceive / rebuy / refund
  - `AdminBffDashboardSliceTest`(3-4 cases):topProducts / trend7d / lowStock aggregation paths
- **`backend/build.gradle` 改 `minimum = 0.80`**:coverage 提上来后 gate 升回 CLAUDE.md 目标
- **删 `openspec/changes/sprint-3-ci-speed/coverage-gap.md`**:缺口收口,文件 purpose 结束

**BREAKING**: 无(只加 test + 改 build.gradle 阈值 + 删一个跟踪文件)

## Capabilities

### New Capabilities
- `service-layer-coverage-completion`: 4 个 service 剩余边缘路径的 unit test,合 `sprint-3-coverage-a-cont` 的 18 cases 把 coverage 推到 ≥ 80%

### Modified Capabilities
无(纯新增 test + 改 build.gradle 阈值,不动既有 spec 行为)

## Impact

- **测试文件**:仅 `backend/src/test/java/com/seafood/`,新增 4 个 service-layer test 类
- **Build 配置**:`backend/build.gradle` `jacocoTestCoverageVerification.minimum` 从 0.75 升到 0.80
- **删除**:`openspec/changes/sprint-3-ci-speed/coverage-gap.md`(跟踪文件 purpose 结束)
- **Fixtures**:复用 D1 builders + Mockito mock 合作 service / repo
- **依赖本 change 之前完成的**:
  - `sprint-2-test-data-builders`(D1)
  - `sprint-2-backend-coverage`(A 后端)
  - `sprint-3-ci-speed`(B)— Jacoco gate 0.75 临时
  - `sprint-3-coverage-a-cont`(A 续)— 18 cases 把 coverage 提到 76.5%
- **后续**:
  - Sprint 4 D3 coverage dashboard 用本 change 升回 80% 的 baseline
  - Sprint 4 C1 PIT mutation 以本 change 升完的测试集为对象
- **性能预算**:新增 ~15-20 case,`./gradlew test` 总时长 +30-60s(在 ~2m 基础上)
- **GraalVM native**:`processTestAot` 仍需 GraalVM JDK,本 change 不动 native 路径
