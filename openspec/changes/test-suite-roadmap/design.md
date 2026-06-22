# Test Suite 完善路线图 — Design

| 字段 | 值 |
|---|---|
| Date | 2026-06-18 |
| Status | Approved(待用户 review 终稿) |
| Scope | 全量路线图(4 个子项目,各自独立 plan 循环) |
| Branch | `feat/sprint-1-closure`(本 spec 仅文档,不动代码) |
| 后续入口 | 用户挑 1 个子项目 → 重新走 brainstorm → writing-plans → 实施 |

---

## 1. 背景与现状

### 1.1 项目背景

海鲜商城单仓(backend + frontend mp + admin-ui),当前在 `feat/sprint-1-closure` 收尾。Sprint 1 已完成:
- test-foundation:Boot 4 注解迁移、Testcontainers 接入、ArchUnit DDD 4 条规约(`archive/2026-06-05-sprint1-test-foundation/`)
- observability-stack:metrics + 结构化日志(`archive/2026-06-12-setup-observability-stack/`)
- native-security:SecurityHeaders + AdminRateLimit(`archive/2026-06-13-sprint2-native-security/`)

Sprint 1 闭合后,需要一份**测试体系路线图**指导 Sprint 2+ 持续投入。

### 1.2 测试现状矩阵

| 层 | backend (Java 25 / Boot 4 / JUnit 5) | frontend mp (TS / Jest 29 / jsdom) | admin-ui (React 18 / Vitest) |
|---|---|---|---|
| Unit | ✅ domain 全(7 类)、application 9+、shared 15+ | ✅ features stores + components 8+ | ✅ lib + 各 feature store |
| Slice | ⚠️ 2 controller、**0 repository** | n/a | n/a |
| Integration | ⚠️ MongoIntegrationTest base 存在;BFF 集成 2 个 | n/a | n/a |
| Architecture | ✅ ArchUnit 4 条(DDD + SecurityHeader + MetricsCardinality) | n/a | n/a |
| E2E | n/a | ✅ 3 e2e 文件 + 2 feature e2e;但 flaky 已知 | ✅ 6 feature e2e + 1 OD design |
| Visual | n/a | ✅ 4 层手工(outerWxml / page.data / console / chroma) | ✅ OD design snapshot |
| Performance | ❌ 无 k6 / JMH | ❌ 无 | ❌ 无 |
| Mutation | ❌ 无 PIT | ❌ 无 | ❌ 无 |
| Contract | ❌ 无 | n/a | n/a |
| Coverage 可视化 | ❌ 无 dashboard | ❌ 无 | ❌ vitest --coverage 即可,无 badge |

**关键缺口**:
1. **Controller slice 缺 ~6 个**(只 2/8);Repository slice = 0
2. **mp 14 页面 4 层验证未自动化**(只 3 个 e2e 文件覆盖部分;14 页面清单见 `docs/redesign/01-functional-mp.md` §2 屏拆解)
3. **CI 速度**:native.yml 10.3K 偏大,mp e2e flaky 已知
4. **测试有效性无验证手段**(无 PIT mutation)
5. **性能预算(API P99 < 500ms)无监控**(无 k6)
6. **测试代码重复多**(无 test data builder, fixture 散落)

---

## 2. 4 个子项目

每个子项目独立交付、独立走 plan → impl 循环。本路线图只规定**目标、缺口、候选、交付、判据**,具体实施细节在子项目 plan 阶段细化。

---

### 子项目 ① Coverage Gap Closure

**目标**:backend 77 → ~120+ 测试;mp 14 页面视觉验证从手工 → 自动化;admin-ui 全 feature 覆盖率达标。

**当前缺口**(已 audit):

- **Backend**:
  - Controller slice:`AdminCookieAuthController`、`AuthControllerLogout` 已写;**缺 ~6 个**(产品/订单/退款/BFF admin/商品/购物车)
  - Repository slice:0 个 `@DataMongoTest`(应补 `OrderRepository`、`ProductRepository`、`UserRepository`、`RefundRepository` + index 验证)
  - BFF integration:仅 `AdminBffServiceTest`、`UploadWritabilityCheckerTest`;**缺端到端**(订单列表/详情、退款审核、admin product CRUD)
  - `MongoIntegrationTest` base 存在但 usage 不明(spec 阶段 audit)
- **Frontend mp**:
  - 14 页面只有 3 个 e2e 文件 + 2 个 feature e2e
  - 组件测:OrderActionRow / RefundSheet / OrderTrackingTimeline 写过;**缺 Banner / ProductCard / CartItem / AddressPicker / 空状态组件**
- **admin-ui**:
  - 整体较全;**缺**:`RequireAuth` 错误路径、表单 validation 边界(如 refund reason 长度)、纯展示组件

**候选方案**:
- **A1** = 边做新功能 TDD 驱动(Sprint 2+ 持续)
- **A2** = 集中 1 个 Sprint 专做 audit + backfill(2 周全人力)
- **A3** = Jacoco HTML 报告 + GitHub Pages 接入,显式标每行 uncovered → 按 hot path 优先级补
- **推荐 = A1 + A3 组合**:不打断 Sprint 节奏;每个 PR 都有 Jacoco 趋势卡点

**交付物**:
- backend: 6+ controller slice、4+ repo slice、5+ BFF integration
- mp: 14 页面都有 `*.visual-verification.test.ts`
- admin-ui: 全 feature vitest --coverage ≥ 80%

**完成判据**:
- backend Jacoco 全局 ≥ 80%、domain/application ≥ 90%(CLAUDE.md 硬规则)
- mp 14 页面都有 4 层自动化测试
- admin-ui 全 feature ≥ 80%

---

### 子项目 ② CI 跑测速度 + 稳定性

**目标**:`./gradlew check` PR 内耗时降一半;mp e2e flaky rate 降到 < 5%。

**当前痛点**:
- `native.yml` 10.3K 偏大(估 10+ min,未实测)
- mp e2e 已知 flaky(`seafood-mp-e2e-debug` skill 存在本身就说明问题)
- Gradle Testcontainers 每个测试类启停容器(Ryuk 慢 ~5s × N)

**候选方案**:
- **B1** Gradle parallel + Testcontainers reuse(单 build 1 个 MongoDBContainer,`@Container static` + `@Testcontainers(disabledWithoutDocker = true)`)
- **B2** 矩阵拆分 — backend / mp / admin-ui / native 4 个独立 job 并行
- **B3** 增量跑测(`--tests "*Test" -Pmodules=changed`)
- **B4** mp e2e retry 1-2 次 + 启动期 health check(WeChat DevTools `auto-port` 端口探测)
- **B5** Docker layer cache + BuildKit
- **推荐 = B1 + B2 + B4**:性价比最高;B3 风险大(增量测试漏回归),B5 收益小

**交付物**:
- ci.yml 拆 3-4 job 并行
- Testcontainers reuse singleton
- mp e2e retry + 端口健康检查

**完成判据**:
- PR CI 总时长 < 8 min(当前估 ~12 min,实测基线在 Sprint 3 末)
- mp e2e flaky rate < 5%
- Gradle check 缓存命中 < 2 min

---

### 子项目 ③ 新增测试能力

**目标**:在 unit + integration + e2e 之上,加 1-2 项新能力。

**候选方案**:
- **C1** PIT mutation testing — 验证测试有抓到真 bug
- **C2** Spring Cloud Contract / OpenAPI Schema Validator — BFF 跨模块契约
- **C3** k6 负载测试 — 对齐 API P99 < 500ms 预算(CLAUDE.md 性能预算)
- **C4** Property-based testing(jqwik)— domain 边界暴力扫
- **C5** Visual diff 自动化(Playwright trace)— 替代手工 4 层验证
- **推荐 = C1 + C3**:C1 直接提升"测试有效性",C3 把性能预算变成可监控的指标
- **延后 = C2 / C4 / C5**:C2 待 BFF 复杂后再做,C4/C5 优先级低

**交付物**:
- PIT 报告 + CI 卡点 mutation score ≥ 70%
- k6 scripts + 报告 + wiremock 模拟慢依赖

**完成判据**:
- backend mutation score ≥ 70%
- 5 个核心 endpoint(GET /products, GET /orders, POST /orders, POST /admin/login, GET /admin/orders)的 k6 报告 P99 < 500ms

---

### 子项目 ④ 测试基础设施

**目标**:消灭测试重复代码,统一 test data builder + coverage 可视化。

**候选方案**:
- **D1** Test data builder(Java + TS 双版)— `OrderBuilder` / `ProductBuilder` / `UserBuilder` / `CartBuilder` / `RefundBuilder`
- **D2** Test fixture 重用 — `MongoIntegrationTest` base + RestAssured request spec
- **D3** Coverage dashboard — Jacoco + Codecov(或自建 GitHub Pages)
- **D4** Mutation score 报告接入(子项目 ③ 配合)
- **D5** 跨仓测试报告聚合
- **推荐 = D1 + D3**:D1 是地基(其它三个子项目都受益),D3 让覆盖率有 trend 可看

**交付物**:
- backend:`test/fixtures/builders/` 5+ 个 Builder
- frontend/admin-ui:`test/builders/` factory
- coverage badge 接入 README + PR comment

**完成判据**:
- 50%+ 现有测试改用 builder
- 覆盖率趋势可查(PR comment + dashboard)
- 新写测试默认用 builder(PR review 卡点)

---

## 3. 依赖矩阵与实施序列

### 3.1 依赖矩阵

```
              D1    D3    A     B     C1    C3
   D1          -     -     U     U     U     -
   D3          -     -     D     -     D     -
   A           D     -     -     D     D     -
   B           D     -     D     -     -     -
   C1          D     D     D     -     -     -
   C3          -     -     -     -     -     -
```

(U = unblock 上游; D = depends on 下游;横看自身)

**关键依赖链**:
- **D1** → unblock A / B / C1(没有 builder,补测试时重复造轮子)
- **A** → 是 C1 的前提(没有足量测试,PIT mutation score 失真)
- **A** → 加重 B 的紧迫性(测试越多 → CI 越慢)
- **D3** → depends on A + C1 产出数据(有数据才有 dashboard)
- **C3** → 完全独立,任何 sprint 可插队

### 3.2 推荐 Sprint 切分(6 个月)

| Sprint | 周期 | 子项目 | 关键交付 |
|---|---|---|---|
| **Sprint 2** | 2 周 | **D1** + **A(后端部分)** | `OrderBuilder`/`ProductBuilder`/`UserBuilder` 等 5+ 个;启动 backend controller slice + repo slice backfill(边做边建 builder) |
| **Sprint 3** | 2 周 | **A(续)** + **B** | BFF integration 测试 5+ 个;mp 14 页面 4 层视觉验证自动化;ci.yml 拆 3-4 job 并行;Testcontainers reuse |
| **Sprint 4** | 2 周 | **D3** + **C1** + **C3** | Coverage dashboard(Jacoco + Codecov);PIT mutation 上 CI;k6 5 个核心 endpoint 跑基线 |
| **Sprint 5+** | 按需 | **C2 / C4 / C5** | 选 1-2 个:C2 Spring Cloud Contract(若 BFF 复杂化)或 C5 视觉 diff(若 mp 重设计) |

### 3.3 关键时序理由

- **D1 排第一**:不开这条路,A / B / C1 都会写重复 fixture,4 个 Sprint 后还得回头补
- **A 分两段不集中**:集中 backfill(Sprint 2 全量)风险高 — 团队节奏打断、知识未沉淀。A1 渐进式更好
- **C1 排 Sprint 4**:不是不重要,是必须先有足量 A 才有意义。PIT 全量跑一次 30+ min,卡 mutation score 阈值需要稳定测试集
- **C3 可插队**:k6 独立,任何 sprint 都能加 5 行 config

### 3.4 Sprint 间衔接检查项

- **Sprint 2 末**:`./gradlew build` 仍绿;新增 builder 写示范测试 ≥ 3 个(给团队打样)
- **Sprint 3 末**:PR CI 总时长 baseline 测一次(为 Sprint 4 优化提供 baseline)
- **Sprint 4 末**:mutation score 基线值;k6 数字首次落库;coverage badge 上 README

---

## 4. 风险与反模式

### 4.1 风险(按发生概率 × 影响排序)

| ID | 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|---|
| R1 | 集中 backfill 打断 Sprint 节奏(若误选 A2) | 中 | 高 | 用 A1 渐进式,每 PR Jacoco 趋势卡点(不达 80% 不合并) |
| R2 | PIT 全量跑测慢(30+ min) | 高 | 中 | PR 内 PIT 只跑 changed module(快速反馈);夜间 cron 跑全量入库;**完成判据 70% mutation score 指全量基线**(非 PR 反馈) |
| R3 | k6 数字漂移(环境/网络波动) | 中 | 中 | 固定 vus / duration / ramp-up;每月跑 1 次定基线;阈值用百分位中位数 ± 10% |
| R4 | Testcontainers reuse 在 CI 跨 job 失效 | 中 | 中 | 单 job 内复用(`@Container static`);跨 job 不强求,接受启动开销 |
| R5 | mp e2e 仍 flaky(WeChat DevTools 自动化本质不稳定) | 高 | 中 | B4 retry + 启动期端口探测;但设硬指标 flaky rate < 5%,超 3 个月未达标就降级 mp e2e(只跑核心 3 个 happy path) |
| R6 | D1 test data builder 演进失控(每个测试都加字段) | 中 | 中 | Builder 默认值原则:必填字段最少,可选字段 `.withXxx()`;PR review 卡 builder API 变化 |
| R7 | coverage dashboard 沦为"看个数字" | 中 | 低 | D3 必须配 coverage diff(PR comment 显示 +x% / -y%);不允许无 diff 只贴数 |

### 4.2 反模式(明确禁止)

- 🚫 **把 E2E 当主测试**:E2E 慢 + 脆,真相在 unit + slice。E2E 只验"集成对了"
- 🚫 **mutation score 100%**:成本陡增,边际收益低;70% 即可,孤儿 mutation(mutation killer 等价物)允许忽略
- 🚫 **为覆盖率而覆盖率**:空 assertion 测试 = 假覆盖。`ArchUnit` 已守架构,可加 ArchUnit 规则禁止 `@Test` 方法体只 `assertEquals(true, true)`
- 🚫 **k6 在 PR 内跑**:PR 跑 k6 = 5+ min,且结果噪声大;k6 走夜间 + weekly 报告
- 🚫 **test data builder 一上来就全抽象**:Sprint 2 只需要 5 个最常用 builder,别搞"通用 base class + reflection";YAGNI
- 🚫 **覆盖率指标当 KPI 考核**:会导致数字游戏;覆盖率是手段,缺陷拦截率才是目的

---

## 5. 整体验收(路线图 6 个月末)

- [ ] 4 个子项目全部完成(对应 Sprint tasks 关闭)
- [ ] **数量**:backend 测试 ≥ 120;mp 14 页面都有 4 层自动化测试;admin-ui 100% feature 都有 test + e2e
- [ ] **质量**:Jacoco 全局 ≥ 80% / domain & application ≥ 90%(CLAUDE.md 硬规则);PIT mutation score ≥ 70%
- [ ] **速度**:PR CI < 8 min(从估 ~12 min 降);Gradle check 缓存命中 < 2 min;mp e2e flaky rate < 5%
- [ ] **可观测**:coverage trend 在 PR comment + GitHub Pages;k6 数字入 Grafana / 报告仓
- [x] **可持续**:每个新功能 PR 默认 TDD(PR 模板 check);覆盖率 + mutation score 趋势卡点
- [ ] **文档**:openspec/changes/<sprint-N>-<sub-project>/ 每个子项目 1 份完整流程(proposal → design → specs → tasks → archived)

---

## 6. 不做什么(YAGNI 清单)

- ❌ SonarQube 自建(用 Codecov 即可)
- ❌ Gatling(用 k6,Node 生态更轻)
- ❌ Cypress(本项目 mp 是微信小程序,Playwright/mp-automator 才是真生态)
- ❌ BDD(Cucumber/Gherkin)— 团队未使用,引入成本 > 价值
- ❌ Contract testing(Sprint 4 内) — BFF 当前简单,Sprint 5+ 再评估
- ❌ Chaos engineering — YAGNI(单体 + 小规模)

---

## 7. 子项目实施流程(每次挑一个时)

1. 创建 `openspec/changes/<sprint-N>-<sub-project>/`(例:`sprint-2-test-builder`)
2. 走标准流程:proposal.md → design.md → specs/<capability>.md → tasks.md
3. 实施:tasks 逐项勾,每项配 PR
4. 验收:对照本 spec 第二节"完成判据"逐条勾
5. 归档:openspec sync → archive

---

## 8. 后续步骤(用户决定)

- 选 **D1**(test data builder,Sprint 2 启动)— 推荐起点
- 或选 **A**(覆盖率补缺,也是 Sprint 2 启动)
- 或选其它子项目

选定后重新走 brainstorm → writing-plans → 实施。
