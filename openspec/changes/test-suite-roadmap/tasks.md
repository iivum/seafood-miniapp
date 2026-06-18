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
      C1 PIT 未启动,留 sprint-5-pit-mutation 独立 change
- [ ] T11 整体验收清单(本路线图 §5)全部勾完
