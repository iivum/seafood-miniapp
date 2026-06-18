# Sprint 2 / D1 — Test Data Builders Tasks

> 实施计划细节见同目录 `design.md` + `plan.md`。本文件是 openspec apply 阶段追踪用,所有 task 已 commit(74f9651 是最后 commit)。

## 1. Builders — RED → GREEN (TDD per builder)

- [x] 1.1 OrderBuilder + OrderBuilderTest(6 cases)— commit `bb209c4`
- [x] 1.2 ProductBuilder + ProductBuilderTest(4 cases)— commit `9ace8b1`
- [x] 1.3 UserBuilder + UserBuilderTest(4 cases)— commit `d51281e`(subagent 修正 plan 错:`Role` 在 `com.seafood.shared.security`)
- [x] 1.4 CartBuilder + CartBuilderTest(3 cases)— commit `caa74d6`
- [x] 1.5 RefundBuilder + RefundBuilderTest(3 cases)— commit `440f8db`

## 2. Refactor & Verify

- [x] 2.1 OrderTest sample() 改用 OrderBuilder(团队打样)— commit `74f9651`
- [x] 2.2 全 backend `./gradlew test` verify(20 builder cases + 15 OrderTest + 其它既有,零 regression)— verify by T7 subagent
