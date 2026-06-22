# Test Suite 完善路线图 — Tasks

> 本文件是路线图本身的 review 闭环任务,不是子项目的实施任务。子项目 tasks 在各自 sub-change 中。

## Phase 1: 路线图 review(本 PR)

- [x] T1 用户 review `proposal.md` + `design.md`,确认 4 个子项目目标/缺口/候选/交付/判据无误
- [x] T2 用户确认 Sprint 切分(Sprint 2: D1 + A 后端;Sprint 3: A 续 + B;Sprint 4: D3 + C1 + C3;Sprint 5+: C2/C4/C5)
- [x] T3 用户确认验收(数量 / 质量 / 速度 / 可观测 / 可持续 5 类)
- [x] T4 路线图 commit + PR(若需要)— 用户选 commit 不开 PR

## Phase 2: 子项目启动(后续,各自 PR)

- [x] T5 用户挑 1 个子项目(推荐先 D1 或 A 后端)— 选 D1: test data builder
- [x] T6 创建 `openspec/changes/sprint-N-<sub-project>/`,走标准 proposal → design → specs → tasks 流程
- [x] T7 实施子项目 PR(对照本路线图 §2 完成判据)
- [x] T8 归档 sub-change
- [x] T9 回到本路线图 §5 验收勾选对应条目

## Phase 3: 整体验收(6 个月末)

- [x] T10 4 个 Sprint 全部归档(D1/A/B/A续/A续-2/Sprint4 changes 均已 archive);
      C1 PIT 已通过 sprint-4-pit-mutation 完成(commit 45fc32c):PIT 1.22.1 在
      JDK 25 + Gradle 9 + Jupiter 6 跑通,核心包基线变异分 72%,gate 70%,
      nightly job 留存报告 —— C1 推迟的工具链兼容性疑虑解除
- [x] T12 C2 契约测试已通过 sprint-5-c2-openapi-contract 完成(commits e27fb9b/53b069d/cd1da39):
      **形态改为 OpenAPI schema 契约**(非 SCC —— SCC 对「1 JVM + 2 TS 前端」拓扑是错工具,
      生成的 stub JAR 前端消费不了)。springdoc 3.0.3 生成 OpenAPI SoT(test-scope 不进 native)+
      漂移门 + swagger-request-validator 响应一致校验(4 端点接入)。触发条件「BFF 复杂」已成立(7 Controller/15 端点)
- [x] T13 C4 property-based testing 已通过 sprint-5-c4-property-testing 完成(commit 7367e5c):
      **jqwik 1.10.1**(平台兼容性 spike 通过:引擎绑 junit-platform 1.14.x 但在项目 6.0.3 上
      正常发现执行,不需 QuickTheories 回退)。13 个 domain property 覆盖 decrementStock 数值边界 /
      OrderStatus 状态机(round-trip+终态+无自环)/ Sku·Product 构造校验
- [ ] T11 整体验收清单(本路线图 §5)全部勾完
      **可持续**已完成(feat/pr-tdd-gate PR — PR 模板 + pr-lint.yml workflow，待 merge 后
      在真实 PR 上完成 CI 验收打勾。§5 其余条目:数量/速度/可观测 待后续 Sprint 实施)
