## Why

Sprint 3 B(`sprint-3-ci-speed`)Jacoco gate 落地时实测 global line coverage = 75%,`./gradlew check` 卡在 76% < 80% 阈值。`backend/build.gradle` 临时降到 0.75 才过 gate,**CLAUDE.md §3 硬规则 80% 仍未达标**。父路线图 `test-suite-roadmap/design.md` §3.2 Sprint 3 范围是 "A(续) + B" — B 已完,A 续(更多 service-layer unit test 补 5% coverage gap)收口。

## What Changes

- **Service-layer unit test backfill**(本 change 主项):给 `OrderService` / `ProductService` / `UserService` / `AdminBffService` 加直接单测,目标把 global line coverage 从 75% 提到 ≥ 80%,让 `jacocoTestCoverageVerification` 阈值从 0.75 升回 0.80
- **`backend/build.gradle` 改 `minimum = 0.80`**:coverage 提上来后 gate 升回 CLAUDE.md 目标阈值
- **`openspec/changes/sprint-3-ci-speed/coverage-gap.md` 同步标记 A 续完成**:关闭 5% 缺口跟踪

**BREAKING**: 无(只加 test + 改 build.gradle 阈值)

## Capabilities

### New Capabilities
- `service-layer-tests`: 4 个 service(`OrderService` / `ProductService` / `UserService` / `AdminBffService`)的直接 unit test,覆盖 BFF aggregation 边缘路径 + service 边缘 case + 异常 catch 路径

### Modified Capabilities
无(纯新增 test + 改 build.gradle 阈值,不动既有 spec 行为)

## Impact

- **测试文件**:仅 `backend/src/test/java/com/seafood/`,新增 ~6-10 个 service-layer test 类(每个 service 1-3 个 slice test)
- **Build 配置**:`backend/build.gradle` `jacocoTestCoverageVerification.minimum` 从 0.75 升到 0.80
- **Fixtures**:复用 D1 builders(`OrderBuilder` / `ProductBuilder` / `UserBuilder` / `RefundBuilder`)+ Mockito mock 合作 service
- **依赖本 change 之前完成的**:
  - `sprint-2-test-data-builders`(D1)— 提供 5 个 builder
  - `sprint-2-backend-coverage`(A 后端)— 提供 14 个 controller slice / repo slice / BFF test,共同贡献 75% baseline
  - `sprint-3-ci-speed`(B + 遗留)— 配置 Jacoco gate,本 change 改阈值
- **后续**:
  - Sprint 4 D3 coverage dashboard 用本 change 升回 80% 的 baseline
  - Sprint 4 C1 PIT mutation 以本 change 升完的测试集为 mutation 对象
- **性能预算**:新增 ~15-30 case,`./gradlew test` 总时长 +30-60s(在 ~2m 基础上)
- **GraalVM native**:`processTestAot` 仍需 GraalVM JDK,本 change 不动 native 路径
