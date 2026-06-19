## Why

Jacoco 行覆盖率 gate(80%)只能证明"代码被执行过",证明不了"断言真的在验证行为"——一个把方法体清空、却仍全绿的测试套件可以轻松刷到 80% 行覆盖。Sprint 4 C1 原计划用 PIT 变异测试堵这个缺口,但因 PIT + JDK 25 + Gradle 9 的工具链兼容性未验证而被推迟。本 change 独立承接 C1:先验证工具链可跑通,再以变异分(mutation score)作为测试**有效性**的第二道闸,补上行覆盖率看不见的盲区。

## What Changes

- 引入 `info.solidsoft.pitest` Gradle 插件 + PIT 运行时,选定与 JDK 25 / Gradle 9 / JUnit 5(Jupiter 6.x)兼容的版本组合
- 新增 `./gradlew pitest` 任务:对核心领域包(`order` / `product` 状态机与定价逻辑)跑变异测试,产出 HTML + XML 报告
- 设定变异分基线 gate(初版 **70%**,仅作用于核心包,非全量),低于阈值时任务失败
- PIT **不**进 `check` 主链(运行慢),仅手动 / nightly 触发,避免拖慢 PR CI
- README 增加 mutation score 说明(与现有 coverage badge 并列)

## Capabilities

### New Capabilities
- `mutation-testing`: PIT 变异测试的接入方式、作用范围(核心包白名单)、变异分阈值 gate、报告产物、以及 CI 触发策略(手动/nightly,不入 PR 主链)

### Modified Capabilities
<!-- 无:jacoco 行覆盖率 gate 行为不变,mutation-testing 是新增的正交质量闸 -->

## Impact

- `backend/build.gradle`:新增 pitest 插件声明 + `pitest { }` 配置块(targetClasses 白名单、mutators、threshold、JVM args)
- 工具链兼容性风险:PIT 读取 JDK 25 字节码(class file v69)需匹配的 ASM 版本;JUnit Jupiter 6.x 需 `pitest-junit5-plugin` 对应版本——这是 C1 推迟的根因,设计阶段需先做 spike 验证
- CI:新增 nightly job 跑 pitest 并上传报告 artifact(复用 Sprint 4 C3 nightly.yml 模式)
- 无生产代码改动,无 API 变更,无 BREAKING
