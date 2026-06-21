# Test Suite 完善路线图 — Proposal

## Why

Sprint 1(`feat/sprint-1-closure`)已完成 test-foundation(Boot 4 注解迁移、Testcontainers、ArchUnit DDD)、observability(metrics + 结构化日志)、native-security(SecurityHeaders + AdminRateLimit)。后续 Sprint 2+ 需要一份**测试体系路线图**指导持续投入,避免测试债务累积。

当前测试现状(详见 `design.md` §1.2):

- **Backend**: 77 测试 / Jacoco 估 ~80%;Controller slice 只 2/8、Repository slice = 0;无 PIT mutation、无 k6 负载、无 coverage dashboard
- **Frontend mp**: 3 个 e2e 文件 + 2 个 feature e2e 覆盖部分页面;14 页面 4 层视觉验证未自动化;e2e flaky 已知
- **admin-ui**: 整体较全,缺错误路径 + 表单边界

如不系统规划,Sprint 2+ 会出现:① 新功能测试缺口累积 ② CI 越跑越慢 ③ 测试有效性无验证手段 ④ 性能预算(API P99 < 500ms)无监控。

## What Changes

**本 change 是 meta-change(路线图),不开新 capability、不改代码**。仅交付 1 份 `design.md` 把后续工作切成 4 个子项目 + 依赖关系 + Sprint 切分 + 验收。

4 个子项目(各自独立 plan 循环):

1. **Coverage Gap Closure(子项目 ①)** — backend 77 → 120+ 测试,补 controller slice / repo slice / BFF integration;mp 14 页面 4 层视觉验证自动化;admin-ui 缺测补全
2. **CI 跑测速度 + 稳定性(子项目 ②)** — `./gradlew check` 跑测时间减半;mp e2e flaky rate 降到 < 5%
3. **新增测试能力(子项目 ③)** — PIT mutation 验证测试有效性 + k6 负载对齐性能预算
4. **测试基础设施(子项目 ④)** — Test data builder(Java + TS 双版)+ Coverage dashboard

详细目标 / 缺口 / 候选方案 / 交付物 / 完成判据见 `design.md` §2。

## Capabilities

### New Capabilities

- `test-roadmap`: 元能力,只描述测试体系路线图本身;不引入新运行时能力,由 4 个后续 sub-changes(子项目 ① ④ 各自 openspec change)落地。

### Modified Capabilities

_None._ 本 change 是 meta 文档,不动现有 capability requirements。

## Impact

- **Spec only**: 本 PR 只新增 1 份 `design.md` + 本 `proposal.md` + `tasks.md`(任务为路线图本身的 review 闭环),不触及任何代码、build、CI 配置。
- **后续 sub-changes**(本 spec 之外,各自独立):
  - `openspec/changes/sprint-2-test-builder/` — 实施子项目 ④
  - `openspec/changes/sprint-2-coverage-backend/` — 实施子项目 ①(后端部分)
  - `openspec/changes/sprint-3-coverage-mp/` — 实施子项目 ①(mp 部分)
  - `openspec/changes/sprint-3-ci-speedup/` — 实施子项目 ②
  - `openspec/changes/sprint-4-pit-and-k6/` — 实施子项目 ③ + ④ 的 D3 部分
  - (命名示例,具体开哪个由用户挑)
- **CI / Build**: 无变化。
- **No runtime / production code impact.**

## 后续入口

用户 review 通过本 spec 后,挑 1 个子项目(推荐先 D1 test data builder 或 A 覆盖率补缺)重新走 brainstorm → writing-plans → 实施。
