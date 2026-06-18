# Tasks — PIT 变异测试接入

> 顺序严格按 design Migration Plan:spike 先行,失败即止损,不 hack。

## 1. 工具链兼容性 spike(D2 — 阻塞后续全部)

- [x] 1.1 在 `backend/build.gradle` plugins 块加 `id 'info.solidsoft.pitest' version '1.19.0'`(捆绑 PIT 1.22.1)
- [x] 1.2 加最小 `pitest { }`:`junit5PluginVersion = '1.2.3'`、`targetClasses = ['com.seafood.order.domain.OrderStatus']`
- [x] 1.3 跑 `./gradlew pitest`——发现并修复 3 层阻塞(均非 JDK25/ASM):① `-x processTestAot` 触发 provider 错误 → 禁用 processTestAot;② minion launcher 1.12.2 vs engine 6.0.3 错位;③ 该错位由 Spring 依赖管理 rule 钉死,force/dependencyManagement 均被反盖 → 用 `dependencySubstitution` 把 launcher 替换成 6.0.3
- [x] 1.4 **判定:成功**。PIT 1.22.1 在 JDK 25(class file v69)+ Gradle 9 + Jupiter 6 完整跑通(OrderStatus: 8 变异全杀,100%)。根因是 Spring Boot 4.0.6 BOM 的 launcher/engine 版本错位,与 ASM 无关——C1 推迟的兼容性疑虑解除

## 2. 扩到核心包 + 跑出真实基线(对应 spec:作用域限定核心领域包)

- [x] 2.1 把 `targetClasses` 扩到白名单:`order.domain.*` / `product.domain.*` / `order.application.*` / `product.application.*`(D3)
- [x] 2.2 加 `excludedClasses`:`**/dto/*` / `**/*Document` / `SeafoodApplication*`;`targetTests` 设 `order.*` / `product.*`
- [x] 2.3 跑 `./gradlew pitest`(暂不设 threshold)——**核心包基线变异分 = 72%**(357 变异 / 杀 257),Line Coverage 82%,Test strength 87%,61 个无覆盖变异
- [x] 2.4 报告含 `order.domain` / `order.application` / `product.domain` / `product.application` 四个核心包;dto/Document 未被纳入 ✓

## 3. 设变异分 gate(对应 spec:变异分阈值作为测试有效性 gate)

- [x] 3.1 实测 72% ≥ 70% → 直接设 `mutationThreshold = 70`(留 2pt 余量)
- [x] 3.2 加 `outputFormats = ['HTML','XML']` + `timestampedReports = false`
- [x] 3.3 gate 强制力已验证:临时设 threshold=95 → `Mutation score of 72 is below threshold of 95` + EXIT=1,已还原 70
- [x] 3.4 确认 `./gradlew check` **不**触发 pitest(build.gradle:116 check.dependsOn 仅 jacoco)

## 4. CI 接入 + 文档(对应 spec:不进 PR 主链 / nightly 留存)

- [x] 4.1 `.github/workflows/nightly.yml` 新增 `pitest-mutation` job:`./gradlew pitest -PexcludeTags=docker`(无需 MongoDB)
- [x] 4.2 上传 `backend/build/reports/pitest/` 为 artifact,`retention-days: 30`,`if: always()`
- [x] 4.3 README 加 mutation badge(72%)+ 测试有效性一行说明
- [x] 4.4 commit 45fc32c:`build(pitest): C1 变异测试接入 + nightly job + 70% gate(基线 72%)`

## 5. 收尾

- [x] 5.1 回填 `openspec/changes/test-suite-roadmap/tasks.md` T10:C1 PIT 由 deferred 改为 done(commit 45fc32c)
- [ ] 5.2 `/opsx:archive sprint-4-pit-mutation` 归档并 sync `mutation-testing` spec 到 `openspec/specs/`

> **分歧待用户定夺**:roadmap `test-roadmap` spec 写 PIT "gates PR merge",本 change(design D4)
> 选 nightly-only(PR CI 速度预算)。plan.md Task 10 原规划的 ci.yml PR-scoped changed-module
> PIT 未做。若需 PR 级 PIT,可后续加 `-Dpit.target.tests=com.seafood.<changed>.*` 增量跑。
