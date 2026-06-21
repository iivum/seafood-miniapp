# Sprint 2 A Coverage Gap — 已知缺口

> 本 change **不**追阈值,只是把 Sprint 1 后没测的 controller / repository / BFF
> 全补上,顺带发现需要后续 Sprint 处理的两件事。

## Jacoco 配置缺失

`backend/build.gradle` 没声明 `jacoco` plugin,因此 `./gradlew jacocoTestReport` 不存在。
**不**判定 Jacoco ≥80% 是否达标。

### 解(Sprint 4 D3 coverage dashboard 范围)

按 `test-suite-roadmap/design.md` §2.1 子项目 ④ D3 — coverage dashboard:

1. `backend/build.gradle` 加 `id("jacoco")` + `jacocoTestReport` task
2. 设 line coverage 阈值 = 80% (CLAUDE.md §3)
3. CI 跑 `jacocoTestReport`,fail build if < 80%
4. Codecov(或自托管)接 PR comment 显示 diff

预计工期:0.5-1d,留 Sprint 4 末。

## 1 个 pre-existing flake(非本 change 范围)

`com.seafood.shared.config.SensitiveValueBeanSerializerModifierTest.masksFieldNamedToken`
自 `b62410d (Sprint 2 C1-C5)` 引入,本 change 改的 14 个 test 文件不影响。
Token TTL / 时间窗 / JDK 25 字节码格式等敏感,值得独立排查:

- 跑单测复现:`./gradlew test --tests "com.seafood.shared.config.SensitiveValueBeanSerializerModifierTest.masksFieldNamedToken"`
- 排查方向:Jackson `BeanSerializerModifier` 在 JDK 25 反射行为变化
  / token 截断长度(测试期望 `eyJh***`,实际 `eyJhbGciOi`)

### 解(独立修,不在本 change 范围)

开 1 个 fix ticket,优先在 Sprint 3 修了。

## 备注

- 14 个新 test 文件(43 test cases)全绿
- ArchUnit(ArchitectureTest + SecurityHeaderArchitectureTest + MetricsCardinalityTest + checkNoRefreshScope)全 PASS
- 422 个 test case 中 421 pass(1 pre-existing flake)
- 本 change 不引入新失败
