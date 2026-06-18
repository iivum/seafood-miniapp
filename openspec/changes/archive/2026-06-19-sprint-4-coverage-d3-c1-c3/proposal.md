## Why

Sprint 3 末 4 个子项目(D1 + A + B + A 续含 A 续-2)已归档,Jacoco coverage 推到 79.3%,gate @ 0.79 跑通。但还差 3 件事:
- **0.5% coverage gap** 到 80% 阈值(`openspec/changes/sprint-3-coverage-a-cont-2/coverage-gap.md` 跟踪)
- **无 coverage dashboard** — Jacoco XML report 在 CI artifact,但 PR 没 comment 显示 diff,review 看不出 coverage 趋势
- **无 mutation testing** — 不知道现有测试集是否真抓到 bug,可能在测实现细节
- **无 load testing baseline** — k6 5 个核心 endpoint 的 P99 数字从未落库,CLAUDE.md 性能预算 `< 500ms` 是承诺没数据

父路线图 `test-suite-roadmap/design.md` §3.2 Sprint 4 = **D3 + C1 + C3**。本 change 集中做 3 件事 + 顺手收口 0.5% coverage gap。

## What Changes

- **D3 Coverage dashboard**(子项目 ④):
  - 升级 Jacoco gate 0.79 → 0.80(1-2 case 补完最后 0.5%)
  - Codecov 集成(或自托管 GitHub Pages),PR comment 显示 per-file diff + 全局 trend
  - README.md 加 coverage badge
- **C1 PIT mutation testing**(子项目 ③):
  - PIT 0.9.x plugin 加到 backend/build.gradle
  - 配置跑 domain/ + application/ 包(跳过 controller/repo/BFF 慢路径)
  - mutation score ≥ 70% 卡点(降级策略:首次 run 测基线,后续 PR 不降)
  - 单独 gradle task `pitest`,CI 跑一次(5-10 min)
- **C3 k6 load testing**(子项目 ③):
  - 5 个核心 endpoint:`GET /api/products` / `GET /api/orders` / `POST /api/orders` / `POST /api/admin/auth/login` / `GET /api/admin/orders`
  - baseline script `backend/scripts/k6-baseline.js` + README 文档
  - 跑一次记录 P50/P95/P99 到 `backend/scripts/k6-results.json`
  - CI nightly job 跑(暂不强制,baseline only)

**BREAKING**: 无(CI 配置 + 新 tool 加,不动 main 业务代码)

## Capabilities

### New Capabilities
- `coverage-dashboard`: PR comment + README badge 显示 Jacoco coverage diff,per-file breakdown,trend
- `pit-mutation-testing`: PIT 跑 domain + application 包,mutation score ≥ 70% gate
- `k6-load-baseline`: 5 个核心 endpoint 的 k6 baseline,记录 P50/P95/P99 到 JSON
- `coverage-80-percent`: 1-2 个 case 补完最后 0.5% coverage gap,Jacoco gate 升回 0.80

### Modified Capabilities
无(纯 CI / build / docs 配置,不动既有 spec 行为)

## Impact

- **CI 配置**:`.github/workflows/ci.yml` 加 coverage comment step + `.github/workflows/nightly.yml` 新建 k6 跑 baseline
- **Build 配置**:`backend/build.gradle` 加 `id('pitest')` plugin + 1-2 case 推到 ≥ 80% + Jacoco 阈值 0.79 → 0.80
- **新增 test**:`backend/src/test/java/...` 1-2 个 case 补 OrderService.requestRefund 状态机路径
- **新增 script**:`backend/scripts/k6-baseline.js` + `backend/scripts/k6-run.sh`
- **Deps**:`org.pitest:pitest-gradle-plugin:1.15.0` + 跑 k6 Docker image(本地不强制)
- **依赖本 change 之前完成的**:
  - `sprint-2-test-data-builders`(D1)
  - `sprint-2-backend-coverage`(A 后端)— 75% baseline + 14 test 类
  - `sprint-3-ci-speed`(B + 遗留)— Jacoco gate 0.75 → 0.79
  - `sprint-3-coverage-a-cont` + `sprint-3-coverage-a-cont-2`(A 续)— 79.3% coverage
- **后续**:
  - Sprint 4 末:本 change 完成 + D3 + C1 + C3 全跑通 = Sprint 4 末 4/4 子项目归档
  - Sprint 5+(如需):C2 Spring Cloud Contract / C4 jqwik property-based / C5 visual diff
- **性能预算**:k6 跑 5 min,nightly CI 可接受(白天 PR 不跑 k6)
- **GraalVM native**:`processTestAot` 仍需 GraalVM JDK,本 change 不动 native 路径
