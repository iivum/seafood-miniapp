## Why

Sprint 1 closure 后 PR CI 总时长 ~12 min(单 job 跑 backend + mp + admin-ui + native 全套),且无 coverage 阈值卡点 → 改 test 集无安全网,refactor 风险高。Sprint 2 末已完成 D1(test data builders)+ A 后端(14 test 文件 / 43 cases),但**两个 Sprint 2 遗留没处理**:① Jacoco plugin 未配置,coverage 阈值无法在 CI 卡点(只能本地看)② `SensitiveValueBeanSerializerModifierTest.masksFieldNamedToken` 1 个 pre-existing flake 阻塞 `./gradlew check` 红。父路线图 `test-suite-roadmap/design.md` §3.2 明确 Sprint 3 范围是 **A(续) + B**,本 change 集中做 B(CI 速度)+ 修 2 个遗留;`A 续` 留作下一 change 单独开。

## What Changes

- **CI 并行 jobs**(父路线图 §3.2 B 主项):拆 `ci.yml` 为 backend / mp / admin-ui / native 4 个并行 job,每个独立 status,1 个失败不阻塞其他
- **Gradle 增量缓存**(B 子项):`gradle check` 缓存命中 < 2 min,通过 `org.gradle.caching=true` + `gradle/actions/setup-gradle@v3` 启用
- **Testcontainers reuse**(B 子项,4 个 repository slice test 已经用 `@Container static` + `@Testcontainers` — 验证同一 JVM 只启 1 个 mongo:7)
- **Jacoco plugin + coverage gate**(Sprint 2 遗留 ①):`backend/build.gradle` 加 `id("jacoco")` + `jacocoTestReport` + `jacocoTestCoverageVerification`(line ≥ 80% 失败时 exit non-zero)
- **Fix `SensitiveValueBeanSerializerModifierTest.masksFieldNamedToken`**(Sprint 2 遗留 ②):trace 根因(Jackson `BeanSerializerModifier` 在 JDK 25 下行为变化 / token 截断长度),改成 deterministic
- **CI 性能 baseline**(B 完成判据):本 change 末尾跑一次 PR CI(本地 `act` 或 GitHub Actions),记录 baseline,给 Sprint 4 优化提供对比

**BREAKING**: 无(纯 CI / build 配置 + 1 个 test 修复)

## Capabilities

### New Capabilities
- `ci-speed`: CI 并行 jobs + Gradle 缓存 + Testcontainers reuse,PR CI 总时长 < 8 min(从 12 min 降)
- `coverage-gate`: Jacoco line coverage ≥ 80% 在 CI hard fail,PR comment 显示 diff(Sprint 4 D3 落 dashboard 的前置)
- `test-flake-fix`: 修 `SensitiveValueBeanSerializerModifierTest.masksFieldNamedToken` 确定性失败,Sprint 2 末遗留问题清零

### Modified Capabilities
- `test-suite-roadmap`: 本 change 完成 Sprint 3 内的 B 子项目 + Sprint 2 两个遗留;A 续仍未做,需在 Sprint 3 末开新 change

## Impact

- **CI 配置**:`.github/workflows/ci.yml`(已有,需改 1 个 job → 4 个并行)
- **Build 配置**:`backend/build.gradle` 加 `id("jacoco")` + `jacocoTestCoverageVerification` 配置(Sprint 2 末 `coverage-gap.md` 已记)
- **Test 修复**:`backend/src/test/java/com/seafood/shared/config/SensitiveValueBeanSerializerModifierTest.java`(1 个 test method)
- **CI 时间**:从 ~12 min 降到目标 < 8 min(40% ↓)
- **GraalVM native**:`./gradlew nativeTest` 不变,仅 `processTestAot` 仍需 GraalVM JDK(JDK 25 toolchain 锁)— 本 change 不动 native 路径
- **下游**:
  - Sprint 3 末的 A 续 change 复用 Jacoco 阈值做 PR 卡点
  - Sprint 4 D3(coverage dashboard)接本 change 跑出的 Jacoco XML
  - Sprint 4 C1(PIT mutation)以本 change 的测试集为 mutation 对象
- **PR 风险**:本 change 改 ci.yml,触发 GitHub Actions 重新跑 baseline CI;若新流程有问题可快速 revert ci.yml 恢复旧流程
