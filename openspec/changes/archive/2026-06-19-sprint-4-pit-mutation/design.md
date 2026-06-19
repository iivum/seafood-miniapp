# Design — PIT 变异测试接入

## Context

Sprint 3 B 上线了 Jacoco 行覆盖率 gate(0.8.13,80% 阈值,`backend/build.gradle:98-112`)。行覆盖率回答"代码跑没跑到",回答不了"断言有没有真的在卡行为"。Sprint 4 C1 计划用 PIT 变异测试补这道闸,但因工具链兼容性未验证被推迟到本独立 change。

当前工具链:Java 25 + Gradle 9.x + Spring Boot 4.0.6 + JUnit Jupiter 6.x。已知约束:
- `gradle-pitest-plugin` 最新 `1.19.0`(2026-03-29)默认捆绑 PIT core `1.22.1`,changelog 明确"build and run with JDK 24"+"initial support for Gradle 9",**未声明 JDK 25**。
- PIT 通过 ASM 读字节码;JDK 25 = class file v69,需 ASM ≥ 9.8。PIT 1.22.x 捆绑的 ASM 版本是否覆盖 v69 = **本 change 第一道未知**。
- JUnit Jupiter 6.x 需 `pitest-junit5-plugin` 对应版本桥接;5.x 时代的插件版本在 Jupiter 6 上可能不识别 `TestEngine`。

## Goals / Non-Goals

**Goals**
- 验证 PIT 在 JDK 25 / Gradle 9 / Jupiter 6 上能跑通(spike 优先,失败则记录确切错误并止损)
- 对 `order` + `product` 核心领域包跑变异,产出可读报告
- 设 70% 变异分 gate(仅核心包),低于即失败
- pitest 不进 `check` 主链,只手动 / nightly 触发

**Non-Goals**
- 不对全量代码跑变异(DTO/infra/config/bff 排除)
- 不追求高变异分数字(初版 70% 是基线,不是冲分)
- 不改任何生产代码、不改 Jacoco 现有 gate
- 不把 pitest 设为 PR 必过项(运行慢,放 nightly)

## Decisions

### D1:版本组合 = gradle-pitest-plugin 1.19.0 + PIT 1.22.1 + junit5-plugin 1.2.x

选最新稳定版而非旧版,理由:JDK/Gradle 越新,越需要插件链上靠近 HEAD 的版本才有 ASM/字节码支持。
- **Alternative**:锁更老的 1.15.x(C1 原计划版本)→ 否决,1.15 时代 ASM 更旧,JDK 25 几乎必挂。
- **junit5PluginVersion** 显式设最新,不依赖默认,因为 Jupiter 6 是新依赖。

### D2:spike 先行 —— 兼容性验证是 Task 1,失败有明确止损口

不直接配好 gate 就跑全套。第一步只在**一个**核心类(如 `OrderStatus` 状态机)上跑 pitest,确认:
1. PIT 能加载 JDK 25 字节码不抛 ASM `IllegalArgumentException` / `UnsupportedOperationException`
2. junit5-plugin 能发现并跑 Jupiter 6 测试(不报 "no tests found")

- **若 spike 失败**:记录确切错误 + PIT/ASM issue 链接到 design Open Questions,本 change 转为"blocked,等上游修",**不**强行 hack。这是 spec "工具链不兼容时构建可诊断" 场景的兜底。

### D3:作用域用白名单 targetClasses,不用全量

```groovy
pitest {
    targetClasses = ['com.seafood.order.domain.*', 'com.seafood.product.domain.*',
                     'com.seafood.order.application.*', 'com.seafood.product.application.*']
    excludedClasses = ['com.seafood.**.dto.*', 'com.seafood.**.*Document', 'com.seafood.SeafoodApplication*']
    targetTests = ['com.seafood.order.*', 'com.seafood.product.*']
}
```
- **理由**:变异测试 O(变异点 × 测试数),全量会跑到分钟级甚至更久。核心领域逻辑(状态机/定价)是断言价值最高、最该验证有效性的地方;DTO/Document 是数据载体,变异它们无意义。
- **Alternative**:全量跑 + 高 timeout → 否决,拖慢 nightly 且信噪比低。

### D4:gate = `mutationThreshold = 70`,任务级失败,不链入 check

```groovy
pitest {
    mutationThreshold = 70
    timestampedReports = false
    outputFormats = ['HTML', 'XML']
}
// 注意:不写 check.dependsOn pitest
```
- **理由**:70% 是变异测试社区常见的"有意义但不严苛"起点(对照行覆盖 80%)。仅作用核心包白名单,不稀释。
- 不进 `check`:PR CI 时长是硬约束(性能预算外的开发体验),变异分析放 nightly,与 Sprint 4 C3 的 k6 nightly 同模式。

### D5:CI = 复用 nightly.yml 模式,新增 pitest job

在 `.github/workflows/nightly.yml` 加一个 `pitest-mutation` job(或独立 step),`./gradlew pitest` 后上传 `build/reports/pitest/` artifact,保留 30 天。与 k6 job 并列。

## Risks / Trade-offs

- **[PIT 1.22.1 的 ASM 不支持 class file v69]** → Mitigation:D2 spike 先验;失败则尝试 `pitest { pitestVersion = '<更新的快照>' }` 覆盖默认,或通过 dependency override 强升 ASM 到 9.8+;仍失败则 blocked 止损,不 hack。
- **[junit5-plugin 不识别 Jupiter 6 TestEngine]** → Mitigation:显式 `junit5PluginVersion` 设最新;查 pitest-junit5-plugin release notes 确认 Jupiter 6 支持。
- **[GraalVM Native 干扰]** → Mitigation:pitest 跑在 JVM 字节码层,与 nativeCompile 正交,不交叉;pitest 不读 `META-INF/native-image/`。
- **[变异分远低于 70%,gate 一上来就红]** → Mitigation:spike 后先以 `--summary` 模式看真实基线;若核心包实际只有 50%,先把 threshold 设到略低于实测(如 60%)落地,再迭代补测试拉到 70%,避免 change 卡死在不可达 gate 上。这是 tasks 里要决策的点。

## Migration Plan

1. 加插件 + 最小配置,spike 单类(Task 1)
2. spike 通过 → 扩到核心包白名单,跑出真实基线变异分(Task 2)
3. 按实测基线设 threshold(Task 3)
4. 接 nightly CI + README 说明(Task 4)
5. 归档,回填 test-suite-roadmap C1 验收

**Rollback**:pitest 是独立任务、不进 check,出问题直接从 build.gradle 摘掉插件块即可,零生产影响。

## Open Questions

- PIT 1.22.1 捆绑的 ASM 具体版本是否 ≥ 9.8(覆盖 JDK 25)?→ Task 1 spike 实测回答,不靠猜。
- 核心包真实变异分基线是多少?→ Task 2 跑出来才知道,直接决定 D4 的 threshold 落地值。

## Sources

- [gradle-pitest-plugin releases](https://github.com/szpak/gradle-pitest-plugin/releases) — 1.19.0 (2026-03-29) 最新
- [gradle-pitest-plugin CHANGELOG](https://github.com/szpak/gradle-pitest-plugin/blob/master/CHANGELOG.md) — 默认 PIT 1.22.1,JDK 24 + 初步 Gradle 9,未声明 JDK 25
- [info.solidsoft.pitest on Gradle Plugin Portal](https://plugins.gradle.org/plugin/info.solidsoft.pitest)
