# Test Suite 完善路线图 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把当前 77 测试 / Jacoco ~80% / 无 PIT / 无 k6 / 无 dashboard 的测试体系,按 6 个月 4 子项目路线图推到 120+ 测试 / Jacoco 80% / PIT 70% / PR CI < 8 min / mp e2e flaky < 5% / 14 页面 4 层全自动化。

**Architecture:** **本 plan 是 roadmap-level orchestration plan,不写子项目级 bite-sized 步骤**。每个子项目在 Sprint 启动时单走 brainstorm → sub-plan → 实施循环(子项目 plan 写在本目录的同级 `openspec/changes/sprint-N-<sub>/tasks.md`)。本 plan 只规定 ① 子项目启动顺序 ② 跨子项目衔接检查项 ③ 6 个月整体验收。

**Tech Stack:** 后端 Java 25 + Spring Boot 4 + JUnit 5 + Testcontainers + ArchUnit + (Sprint 4+) PIT;前端 mp Jest 29 + miniprogram-automator;admin-ui Vitest + Testing Library;CI GitHub Actions + (Sprint 4+) Codecov + k6。

---

## 关键前置

- **每个 sub-plan 必须**:
  1. 走完整 `proposal.md` → `design.md` → `specs/<capability>.md` → `tasks.md` 流程(对齐 OpenSpec 惯例)
  2. 引用本路线图 spec `openspec/changes/2026-06-18-test-suite-roadmap/design.md` §2 对应子项目,确认完成判据不变
  3. 实施前在 PR 描述里写 "sub-plan of roadmap: aac9e29"
- **跨子项目检查项**(Sprint 末):见本 plan §"Sprint 衔接 checklist"
- **验收基准**:
  - Sprint 3 末测一次 PR CI 基线时长(给 Sprint 4 优化提供 baseline)
  - Sprint 4 末测 PIT 全量 mutation score 基线 + k6 5 endpoint P99 数字(给"是否达标"提供 baseline)

---

## Phase 1: 路线图 orchestration(本 PR)

### Task 1: 路线图 review + 本 plan commit

**Files:**
- Modify: `openspec/changes/2026-06-18-test-suite-roadmap/tasks.md`(本 plan 是 sub-task of 路线图 review 闭环)

- [ ] **Step 1**: 用户 review `proposal.md` + `design.md` + 本 plan
- [ ] **Step 2**: 用户确认 4 子项目目标/缺口/候选/交付/判据无误
- [ ] **Step 3**: 用户确认 Sprint 切分(Sprint 2 → D1 + A 后端,Sprint 3 → A 续 + B,Sprint 4 → D3 + C1 + C3,Sprint 5+ → C2/C4/C5 备选)
- [ ] **Step 4**: 用户确认验收基准(数量 / 质量 / 速度 / 可观测 / 可持续 5 类)
- [ ] **Step 5**: commit 本 plan

```bash
git add openspec/changes/2026-06-18-test-suite-roadmap/plan.md
git commit -m "docs(sprint1-closure): test suite 路线图 plan — 6 个月 Sprint orchestration

延续 commit aac9e29 (路线图 spec),本 plan 是 orchestration 层,
子项目级 bite-sized 步骤在 sub-plan 中。

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Phase 2: Sprint 2(2 周)— D1 + A 后端

### Task 2: 创建 sub-plan — Sprint 2 Test Data Builder(子项目 ④ D1)

**Files:**
- Create: `openspec/changes/sprint-2-test-builder/proposal.md`
- Create: `openspec/changes/sprint-2-test-builder/design.md`
- Create: `openspec/changes/sprint-2-test-builder/specs/test-data-builder.md`
- Create: `openspec/changes/sprint-2-test-builder/tasks.md`

- [ ] **Step 1**: 新建子 change 目录,跑 `openspec` CLI 拉 proposal 模板
- [ ] **Step 2**: 走 brainstorm(单独 session)— 设计 `OrderBuilder` / `ProductBuilder` / `UserBuilder` / `CartBuilder` / `RefundBuilder` 5 个 builder 的 API 形态(默认值原则 / `.withXxx()` 链式 / 不引入 reflection)
- [ ] **Step 3**: 写 `proposal.md`(Why / What Changes / Capabilities / Impact),引用本路线图 design.md §子项目 ④
- [ ] **Step 4**: 写 `design.md`(架构 / 接口 / 错误处理 / 测试),包含至少 3 个示范测试用 builder
- [ ] **Step 5**: 写 `specs/test-data-builder.md`(需求 delta),5 个 builder 各自 MUST 条款
- [ ] **Step 6**: 写 `tasks.md`(bite-sized,每个 builder 一个 Phase)
- [ ] **Step 7**: 用户 review sub-plan
- [ ] **Step 8**: commit sub-plan + 开 PR

```bash
git checkout -b feat/sprint-2-test-builder
# ... 编辑文件 ...
git add openspec/changes/sprint-2-test-builder/
git commit -m "docs(sprint-2): test data builder sub-plan (sub of roadmap aac9e29)"
gh pr create --base main --title "Sprint 2: Test Data Builder sub-plan" --body "..."
```

### Task 3: 实施 Sprint 2 Test Data Builder

**Files:**
- Create: `backend/src/test/java/com/seafood/testfixtures/builders/OrderBuilder.java`
- Create: `backend/src/test/java/com/seafood/testfixtures/builders/ProductBuilder.java`
- Create: `backend/src/test/java/com/seafood/testfixtures/builders/UserBuilder.java`
- Create: `backend/src/test/java/com/seafood/testfixtures/builders/CartBuilder.java`
- Create: `backend/src/test/java/com/seafood/testfixtures/builders/RefundBuilder.java`
- Create: `backend/src/test/java/com/seafood/testfixtures/builders/DocumentMappers.java`(Mongo Document 互转 helper)
- Create: `frontend/src/test/builders/orderBuilder.ts` + `productBuilder.ts` + `cartBuilder.ts`
- Create: `admin-ui/src/test/builders/orderBuilder.ts` + `productBuilder.ts`
- Modify: 现有 ≥ 3 个测试改用 builder(给团队打样)

执行细则:见 `openspec/changes/sprint-2-test-builder/tasks.md` 第 1-5 Phase(TDD 红绿循环)

- [ ] **Step 1**: 实施 sub-plan tasks.md 全部 task
- [ ] **Step 2**: `./gradlew build` 绿;`npm test` 绿
- [ ] **Step 3**: 至少 3 个现有测试改用 builder(示范)
- [ ] **Step 4**: PR review + merge
- [ ] **Step 5**: openspec sync + archive

```bash
openspec sync sprint-2-test-builder --type change
openspec archive sprint-2-test-builder --yes
```

### Task 4: 创建 + 实施 sub-plan — Sprint 2 Coverage Backend(子项目 ① A 后端部分)

**Files:**
- Create: `openspec/changes/sprint-2-coverage-backend/{proposal,design,tasks}.md + specs/`
- Create: `backend/src/test/java/com/seafood/product/api/ProductControllerTest.java`
- Create: `backend/src/test/java/com/seafood/order/api/OrderControllerTest.java`
- Create: `backend/src/test/java/com/seafood/order/api/RefundControllerTest.java`
- Create: `backend/src/test/java/com/seafood/bff/admin/api/AdminBffControllerTest.java`
- Create: `backend/src/test/java/com/seafood/product/infra/ProductRepositoryTest.java`
- Create: `backend/src/test/java/com/seafood/order/infra/OrderRepositoryTest.java`
- Create: `backend/src/test/java/com/seafood/user/infra/UserRepositoryTest.java`
- Create: `backend/src/test/java/com/seafood/order/infra/RefundRepositoryTest.java`

执行细则:见 sub-plan tasks.md

- [ ] **Step 1**: 走 brainstorm(单独 session)— audit 现状 6 个 controller,定 slice 范围(每个 controller 2-3 个 happy + 1-2 个 error)
- [ ] **Step 2**: 写 sub-plan(proposal/design/specs/tasks)
- [ ] **Step 3**: 实施 — 4 controller slice + 4 repository slice(用 D1 builders)
- [ ] **Step 4**: `./gradlew check` 绿(ArchUnit 守 DDD 边界 + SecurityHeader 守 setHeader 白名单)
- [ ] **Step 5**: PR + merge + archive

### Task 5: Sprint 2 末衔接 checklist

- [ ] **C1**: `./gradlew build` 绿;新 builder 至少被 3 个测试引用(示范有效)
- [ ] **C2**: backend 测试数 ≥ 90(从 77 + ~6 controller slice + ~4 repo slice + 一些被触发的现有测试)
- [ ] **C3**: sub-plan 1(sub-builder)+ sub-plan 2(coverage-backend)都已 merge + archived
- [ ] **C4**: 团队 onboarding:PR 模板新增 "新测试默认用 builder" check item

---

## Phase 3: Sprint 3(2 周)— A 续(mp + admin-ui) + B(CI 速度)

### Task 6: 创建 + 实施 sub-plan — Sprint 3 Coverage MP(子项目 ① A mp 部分)

**Files:**
- Create: `openspec/changes/sprint-3-coverage-mp/{proposal,design,tasks}.md + specs/`
- Create: `frontend/e2e/<page>-visual-verification.test.ts` × 14(banner / category / product-detail / cart / checkout / order-list / order-detail / refund / search / etc.)
- Create: `frontend/src/features/<feature>/components/<Component>/<Component>.test.ts`(Banner / ProductCard / CartItem / AddressPicker / etc.)
- Modify: 现有 mp-3layer.test.ts + mp-od-design.test.ts + token-parity.test.ts 接入 visual-verification 模式

- [ ] **Step 1**: 走 brainstorm — 14 页面清单(`docs/redesign/01-functional-mp.md` §2)+ 每个页面 4 层断言细则
- [ ] **Step 2**: 写 sub-plan
- [ ] **Step 3**: 实施 — 14 页面 visual-verification 测试 + 5+ 组件测试
- [ ] **Step 4**: `npm test -- frontend/e2e/` 绿;flaky rate 测 3 轮取平均(基线)
- [ ] **Step 5**: PR + merge + archive

### Task 7: 创建 + 实施 sub-plan — Sprint 3 Coverage Admin UI(子项目 ① A admin-ui 部分)

**Files:**
- Create: `openspec/changes/sprint-3-coverage-admin-ui/{proposal,design,tasks}.md + specs/`
- Create: 缺测组件 + 错误路径 + 表单 validation 边界测试

- [ ] **Step 1**: 走 brainstorm — audit admin-ui 当前 coverage,定缺口
- [ ] **Step 2**: 写 sub-plan
- [ ] **Step 3**: 实施
- [ ] **Step 4**: `vitest --coverage` ≥ 80% 全 feature
- [ ] **Step 5**: PR + merge + archive

### Task 8: 创建 + 实施 sub-plan — Sprint 3 CI Speedup(子项目 ② B)

**Files:**
- Create: `openspec/changes/sprint-3-ci-speedup/{proposal,design,tasks}.md + specs/`
- Modify: `.github/workflows/ci.yml` — 拆 3-4 job 并行(backend / mp / admin-ui / lint)
- Modify: `.github/workflows/native.yml` — Testcontainers reuse + cache 策略
- Modify: `backend/build.gradle` — Gradle parallel + Testcontainers singleton(`@Container static` + `@Testcontainers(disabledWithoutDocker = true)`)
- Modify: `frontend/scripts/run-e2e.js`(或 `jest.config.js`)— mp e2e retry 1-2 次 + WeChat DevTools 端口健康检查

- [ ] **Step 1**: 走 brainstorm — Sprint 2 末测的 PR CI 基线时长,定优化目标(8 min)
- [ ] **Step 2**: 写 sub-plan — 重点处理 R4(Testcontainers reuse 跨 job 失效)+ R5(mp e2e flaky 兜底)
- [ ] **Step 3**: 实施
- [ ] **Step 4**: PR CI 测 3 次取平均 < 8 min;mp e2e flaky rate < 5%(3 轮跑测)
- [ ] **Step 5**: PR + merge + archive

### Task 9: Sprint 3 末衔接 checklist

- [ ] **C5**: PR CI 总时长 < 8 min(从估 ~12 min 降,实测基线)
- [ ] **C6**: mp e2e flaky rate < 5%
- [ ] **C7**: backend 测试数 ≥ 100;mp 14 页面全有 visual-verification 测试;admin-ui 覆盖率 ≥ 80%
- [ ] **C8**: 3 个 sub-plan(coverage-mp / coverage-admin-ui / ci-speedup)都已 merge + archived

---

## Phase 4: Sprint 4(2 周)— D3 + C1 + C3

### Task 10: 创建 + 实施 sub-plan — Sprint 4 PIT Mutation(子项目 ③ C1)

**Files:**
- Create: `openspec/changes/sprint-4-pit-mutation/{proposal,design,tasks}.md + specs/`
- Modify: `backend/build.gradle` — 加 PIT plugin(`info.solidsoft.gradle.pitest`)
- Create: `.github/workflows/pit.yml` — 夜间 cron 跑全量 mutation + 入库
- Modify: `.github/workflows/ci.yml` — PR 内 PIT 只跑 changed module(`-Dpit.target.tests=com.seafood.<changed>.*`)

- [ ] **Step 1**: 走 brainstorm — PIT 配置(目标类 / 变异算子 / 阈值)
- [ ] **Step 2**: 写 sub-plan
- [ ] **Step 3**: 实施 + 跑全量基线(看默认 mutation score,可能要从 50% 提到 70%)
- [ ] **Step 4**: PR(只配 PR 反馈)+ cron workflow(全量);夜间入库
- [ ] **Step 5**: PR + merge + archive

### Task 11: 创建 + 实施 sub-plan — Sprint 4 K6 Load Testing(子项目 ③ C3)

**Files:**
- Create: `openspec/changes/sprint-4-k6-load/{proposal,design,tasks}.md + specs/`
- Create: `k6/products.js` + `k6/orders.js` + `k6/admin-orders.js` + `k6/admin-login.js` + `k6/order-create.js`
- Create: `.github/workflows/k6-weekly.yml` — 每周日跑全量
- Create: `infra/wiremock/`(可选)— 模拟慢依赖

- [ ] **Step 1**: 走 brainstorm — 5 endpoint + vus / duration / ramp-up 固定;阈值用中位数 ± 10%
- [ ] **Step 2**: 写 sub-plan
- [ ] **Step 3**: 实施 + 跑基线(看 P99 当前值)
- [ ] **Step 4**: weekly workflow + Grafana 报告(若已有)或 README 报告
- [ ] **Step 5**: PR + merge + archive

### Task 12: 创建 + 实施 sub-plan — Sprint 4 Coverage Dashboard(子项目 ④ D3)

**Files:**
- Create: `openspec/changes/sprint-4-coverage-dashboard/{proposal,design,tasks}.md + specs/`
- Modify: `backend/build.gradle` — Jacoco XML 输出 + Codecov 集成
- Create: `.github/workflows/coverage.yml` — PR 内跑 + 上传 Codecov + PR comment(diff +x% / -y%)
- Create: `README.md` 修改 — coverage badge

- [ ] **Step 1**: 走 brainstorm — Codecov vs 自建 GitHub Pages 选 1(默认 Codecov)
- [ ] **Step 2**: 写 sub-plan
- [ ] **Step 3**: 实施 + 验证 PR comment 含 diff
- [ ] **Step 4**: README badge 上线
- [ ] **Step 5**: PR + merge + archive

### Task 13: Sprint 4 末衔接 checklist

- [ ] **C9**: backend mutation score ≥ 70%(全量基线)
- [ ] **C10**: 5 endpoint k6 报告 P99 < 500ms(基线值入文档)
- [ ] **C11**: coverage trend 在 PR comment + Codecov dashboard
- [ ] **C12**: 3 个 sub-plan(pit / k6 / coverage-dashboard)都已 merge + archived

---

## Phase 5: Sprint 5+(按需)— C2 / C4 / C5 备选

### Task 14: 评估是否启动 C2 / C4 / C5 sub-plan

**Files:**
- Create: `openspec/changes/sprint-5-<chosen>/{proposal,design,tasks}.md + specs/`

**C2 触发条件**:BFF 复杂化(> 5 个 admin endpoint,跨 3+ 模块组合)
**C4 触发条件**:domain 出现不易枚举的边界(如 order price 计算涉及多 currency + discount + tax 组合)
**C5 触发条件**:mp 重设计 或 admin-ui 大改版

- [ ] **Step 1**: 团队 review 触发条件是否满足
- [ ] **Step 2**: 满足 → 走标准 brainstorm → sub-plan → 实施循环
- [ ] **Step 3**: 不满足 → 跳过,Sprint 5+ 用作 buffer(吸收前面 Sprint 滚动 + 紧急修复)

---

## Phase 6: 6 个月末整体验收(对齐路线图 spec §5)

### Task 15: 6 个月验收 checklist

- [ ] **A1 数量**: backend 测试 ≥ 120;mp 14 页面都有 4 层自动化测试;admin-ui 100% feature 都有 test + e2e
- [ ] **A2 质量**: Jacoco 全局 ≥ 80% / domain & application ≥ 90%;PIT mutation score ≥ 70%
- [ ] **A3 速度**: PR CI < 8 min;Gradle check 缓存命中 < 2 min;mp e2e flaky rate < 5%
- [ ] **A4 可观测**: coverage trend 在 PR comment + Codecov;k6 数字入 weekly 报告
- [ ] **A5 可持续**: PR 模板 check "新测试默认用 builder";覆盖率 + mutation score 趋势卡点
- [ ] **A6 文档**: 4 子项目 sub-change 都已 `openspec archive`;本路线图 spec `archive/2026-06-18-test-suite-roadmap/`

### Task 16: 路线图 spec 归档

```bash
openspec sync 2026-06-18-test-suite-roadmap --type change
openspec archive 2026-06-18-test-suite-roadmap --yes
```

- [ ] **Step 1**: 确认 4 子项目 sub-change 都已 archive
- [ ] **Step 2**: 本路线图 spec archive

---

## 风险与回退(摘自 spec §4)

| ID | 风险 | 回退 / 缓解 |
|---|---|---|
| R1 | 集中 backfill 打断节奏 | 已规避:用 A1 渐进式,每 PR Jacoco 卡点 |
| R2 | PIT 全量慢(30+ min) | PR 内只跑 changed module;夜间 cron 全量入库 |
| R3 | k6 数字漂移 | 固定 vus / duration;阈值用中位数 ± 10% |
| R4 | Testcontainers reuse 跨 job 失效 | 单 job 内复用;跨 job 不强求 |
| R5 | mp e2e 仍 flaky | 设硬指标 < 5%,超 3 个月未达标降级到 happy path only |
| R6 | test data builder 演进失控 | 默认值最少;PR review 卡 builder API |
| R7 | coverage dashboard 沦为"看数字" | 必须配 PR comment diff |

---

## 跨子项目硬约束(PR review 必查)

- 🚫 新测试不用 builder(违反 D1 持续原则)— 拒绝合并
- 🚫 新测试有空 assertion(违反覆盖率真实性)— 拒绝合并
- 🚫 新测试改架构层(DDD 4 条)— ArchUnit 红即拒
- 🚫 新测试改 SecurityHeader 写入白名单外— `SecurityHeaderArchitectureTest` 红即拒
- 🚫 PR 缺 coverage diff(违反 D3)— 缺则拒
- 🚫 mutation score 降超 2% — 必须有 explanation

---

## 完成判据(本 plan 整体)

完成 = 4 子项目 sub-plan 都已 archive + Phase 6 16 条 checklist 全勾 + 路线图 spec archived。
