# Spec: mutation-testing

## Purpose

PIT 变异测试的接入契约:工具链可运行性、作用域(核心领域包白名单)、变异分阈值 gate、报告产物、以及 CI 触发策略(全量 nightly + PR 内对改动模块的增量 gate)。变异分验证"断言真的在卡行为",补行覆盖率看不见的盲区。源自 change `sprint-4-pit-mutation`(Sprint 4 C1)。

## Requirements

### Requirement: PIT 变异测试任务可在 JDK 25 工具链上运行

后端构建 SHALL 提供 `./gradlew pitest` 任务,在 Java 25 + Gradle 9 + JUnit Jupiter 6.x 工具链上成功完成变异分析并产出报告,不得因字节码版本(class file v69)或 JUnit 5 桥接不兼容而失败。

#### Scenario: 在核心包上运行 pitest 成功产出报告

- **WHEN** 开发者在 `backend/` 执行 `./gradlew pitest`
- **THEN** 任务以 exit 0 完成,并在 `build/reports/pitest/` 下生成 HTML 与 XML 报告
- **AND** 报告中至少包含 `order` 与 `product` 核心包的变异结果

#### Scenario: 工具链版本不兼容时构建可诊断

- **WHEN** PIT 因 ASM 不识别 JDK 25 字节码或 JUnit5 插件版本不匹配而中断
- **THEN** 构建以非零退出码失败,且日志包含可定位根因的错误信息(而非静默全绿)

### Requirement: 变异测试作用域限定核心领域包

PIT 的 `targetClasses` SHALL 限定为核心领域逻辑(`order` 与 `product` 的 domain/application 层),不得对全量代码(DTO、Document、配置类、BFF 装配)跑变异,以控制运行时长并聚焦高价值断言验证。

#### Scenario: 白名单内的核心包被纳入变异

- **WHEN** pitest 运行
- **THEN** `com.seafood.order.domain.*` 与 `com.seafood.product.domain.*` 的类被纳入变异分析

#### Scenario: 白名单外的代码被排除

- **WHEN** pitest 运行
- **THEN** `**/dto/**`、`**/infra/**Document`、`SeafoodApplication` 等不参与变异,不计入变异分

### Requirement: 变异分阈值作为测试有效性 gate

`pitest` 任务 SHALL 设定变异分阈值(全量核心包 70%);当实际变异分低于阈值时任务 MUST 失败,使"清空方法体仍全绿"这类无效测试无法通过。

#### Scenario: 变异分达标时任务通过

- **WHEN** 核心包变异分 ≥ 70%
- **THEN** `pitest` 任务以 exit 0 通过

#### Scenario: 变异分不达标时任务失败

- **WHEN** 核心包变异分 < 70%
- **THEN** `pitest` 任务以非零退出码失败,并在报告中标出存活变异(surviving mutants)

### Requirement: 全量变异测试不进入 `check` 主链

全量 `pitest` 任务 SHALL NOT 被链入 `check`,以免拖慢本地 / PR CI;它通过手动调用或 nightly CI 触发,报告作为 artifact 留存。

#### Scenario: check 主链不触发 pitest

- **WHEN** 开发者执行 `./gradlew check`
- **THEN** pitest 任务不被执行,`check` 时长不受变异分析影响

#### Scenario: nightly 触发全量 gate 并留存报告

- **WHEN** nightly CI 运行
- **THEN** 执行 `./gradlew pitest`(全核心包,gate 70%)并将 `build/reports/pitest/` 上传为构建 artifact(保留期 ≥ 30 天)

### Requirement: PR 内对改动核心模块跑增量变异 gate

PR CI SHALL 检测本 PR 改动了哪些核心模块(`order` / `product`),仅对改动模块跑 scoped PIT 并按该模块基线 floor 卡门;未改动核心模块的 PR 不触发变异分析。各模块 floor 按当前基线 grandfather、只防回退(`order` 80% / `product` 40%),不强求统一 70%——避免对当前测试债(`product.application` 32%)设不可达 gate。

#### Scenario: PR 改动 order 模块时按 order floor 卡门

- **WHEN** PR 改动了 `com/seafood/order/**` 下的代码,PR CI 运行
- **THEN** 执行 `./gradlew pitest -PpitScope=order`,变异分 < 80% 时 job 非零退出、PR 不可合并

#### Scenario: PR 改动 product 模块时按 product floor 卡门

- **WHEN** PR 改动了 `com/seafood/product/**` 下的代码,PR CI 运行
- **THEN** 执行 `./gradlew pitest -PpitScope=product`,变异分 < 40% 时 job 非零退出

#### Scenario: PR 未改动核心模块时跳过

- **WHEN** PR 只改动了非 `order`/`product` 核心代码(如 docs、前端、配置)
- **THEN** 增量变异 job 直接以 exit 0 跳过,不跑 PIT,不拖慢无关 PR
