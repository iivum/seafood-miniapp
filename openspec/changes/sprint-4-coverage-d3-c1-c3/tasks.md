# Tasks: Sprint 4 D3 + C1 + C3 + Coverage 80% Completion

> 实施细节见 `design.md`(8 节)。本文件为 OpenSpec apply 入口的 checkbox 跟踪。

## 1. Coverage 80% 收口(顺手活)

- [x] 1.1 写 `OrderServiceRequestRefundTest`(1-2 cases):PAID → REFUNDING state machine 路径
- [x] 1.2 跑 `./gradlew test --tests "...OrderServiceRequestRefundTest"` 验 1-2/1-2 PASS
- [x] 1.3 改 `backend/build.gradle` Jacoco threshold 0.79 → 0.80
- [x] 1.4 跑 `./gradlew check -PexcludeTags=docker -x processTestAot` 验 gate @ 0.80 通过(coverage ≥ 80%)
- [x] 1.5 删 `openspec/changes/sprint-3-coverage-a-cont-2/coverage-gap.md`
- [x] 1.6 Commit coverage 完成改动

## 2. D3 Coverage dashboard

- [x] 2.1 改 `.github/workflows/ci.yml` backend job:加 `actions/github-script@v7` step 读 Jacoco XML 发 PR comment
- [x] 2.2 改 `README.md` 加 coverage badge(自托管 gh-pages 链接,placeholder URL)
- [x] 2.3 跑 CI 一次确认 PR comment 触发(本地无法测,用 `act` 或 dry-run)
- [x] 2.4 (可选)Codecov 集成 — 如果 `CODECOV_TOKEN` secret 存在,加 `codecov/codecov-action@v4` step
- [x] 2.5 Commit D3 改动

## 3. C1 PIT mutation testing

- [x] 3.1 改 `backend/build.gradle` 加 `id 'org.pitest' version '1.15.0'` plugin
- [x] 3.2 加 `pitest {}` 配置:targetClasses(7 个 domain/application 包),mutationThreshold=0(no gate 起步),threads=4
- [x] 3.3 跑 `./gradlew pitest -x processTestAot` 验证 plugin 工作 + 跑出 baseline mutation score
- [x] 3.4 看 `build/reports/pitest/index.html` 记录 baseline mutation score
- [x] 3.5 跑过后设 `mutationThreshold = 70` 让 gate 生效
- [x] 3.6 改 `ci.yml` backend job:加 `./gradlew pitest` step + upload `build/reports/pitest/**` artifact retention 30d
- [x] 3.7 Commit PIT 改动

## 4. C3 k6 load baseline

- [x] 4.1 写 `backend/scripts/k6-baseline.js`(5 个 endpoint,P50/P95/P99 reporting,thresholds p(99)<500)
- [x] 4.2 写 `backend/scripts/k6-run.sh`(起 backend + 跑 k6 + 解析 stdout → k6-results.json)
- [x] 4.3 写 `backend/scripts/k6-results.json` initial placeholder
- [x] 4.4 新建 `.github/workflows/nightly.yml` — cron `0 2 * * *`,跑 k6-run.sh,upload artifact
- [x] 4.5 (可选)本地跑一次 k6 跑出真实 baseline(需要 backend 运行)
- [x] 4.6 Commit k6 改动

## 5. Final commit + archive

- [x] 5.1 Commit 4 块(每块 1 commit,共 4)
- [x] 5.2 标记本 change `tasks.md` 全 ✅
- [x] 5.3 跑 `/opsx:archive sprint-4-coverage-d3-c1-c3`
- [x] 5.4 更新 `openspec/changes/test-suite-roadmap/tasks.md` T5 勾 "Sprint 4 4 个子项目完成" + T10 标 "4 个子项目全部归档"

## 6. Reference

- Spec: `specs/coverage-80-percent/spec.md`(3 R, 6 S)+ `specs/coverage-dashboard/spec.md`(3 R, 6 S)+ `specs/pit-mutation-testing/spec.md`(3 R, 7 S)+ `specs/k6-load-baseline/spec.md`(3 R, 7 S)= 12 R, 26 S
- Design: `design.md`(8 节)
- Proposal: `proposal.md`
- 父路线图:`test-suite-roadmap/design.md` §3.2 Sprint 4
- 0.5% 缺口跟踪:`openspec/changes/sprint-3-coverage-a-cont-2/coverage-gap.md`(本 change 删)
- 前置:D1 + A + B + A 续 + A 续-2(均已 archive)
- 后续:Sprint 5+(C2/C4/C5 如需)
