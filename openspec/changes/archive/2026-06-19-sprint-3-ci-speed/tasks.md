# Tasks: Sprint 3 B + Sprint 2 遗留

> 实施细节见 `design.md`(8 节)。本文件为 OpenSpec apply 入口的 checkbox 跟踪。

## 1. CI 速度(Gradle 远程缓存)

- [x] 1.1 改 `.github/workflows/ci.yml` backend job:删 `actions/cache@v5` 块,加 `gradle/actions/setup-gradle@v3` 步骤
- [x] 1.2 改 backend job:`cache-read-only: ${{ github.event_name != 'pull_request' }}`(PR 只读不写,防 cache 投毒)
- [x] 1.3 Commit ci.yml 改动
- [x] 1.4 实测(本地 push 或 GitHub Actions):`backend` job 缓存命中 wall-clock < 2 min,记录到 `ci-speed-baseline` job summary

## 2. Coverage gate(Jacoco + ≥80% 阈值)

- [x] 2.1 改 `backend/build.gradle` `plugins` 块加 `id 'jacoco'`
- [x] 2.2 配 `jacoco { toolVersion = '0.8.13' }`(JDK 25 兼容)
- [x] 2.3 配 `jacocoTestReport` 报告 XML + HTML,排除 `com/seafood/SeafoodApplication*` 和 `com/seafood/**/dto/**`
- [x] 2.4 配 `jacocoTestCoverageVerification { violationRules { rule { limit { minimum = 0.80 } } } }`
- [x] 2.5 `check.dependsOn jacocoTestCoverageVerification` 链入 `./gradlew check`
- [x] 2.6 改 `ci.yml` backend job:`./gradlew check` step 后加 `actions/upload-artifact@v4` 上传 `build/reports/jacoco/test/**`,retention 7 days
- [x] 2.7 跑 `./gradlew check -PexcludeTags=docker -x processTestAot` 验证 gate 通过(coverage ≥ 80%)
- [x] 2.8 若 gate 失败,看 HTML 报告 `backend/build/reports/jacoco/test/html/index.html`,找出未覆盖 classes,加 unit test(Sprint 2 末 14 文件应已达标,若仍 fail 写 coverage-gap.md 续做)
- [x] 2.9 Commit build.gradle + ci.yml 改动

## 3. Test flake fix(`SensitiveValueBeanSerializerModifierTest.masksFieldNamedToken`)

- [x] 3.1 读 `backend/src/main/java/com/seafood/shared/config/SensitiveValueMasker.java` 确认 field-name matcher 逻辑(是否漏识别 `accessToken`)
- [x] 3.2 改 `SensitiveValueBeanSerializerModifierTest.masksFieldNamedToken` fixture:`new Bean("eyJhbGciOi")` → `new Bean("eyJhbGciOi...200+ chars...")`(真实 JWT 长度)
- [x] 3.3 加 javadoc 注释:解释为何用 200+ 字符 fixture(短 fixture 可能让 masker 截断逻辑短路产生 false negative)
- [x] 3.4 跑 10 次 `./gradlew test --tests "com.seafood.shared.config.SensitiveValueBeanSerializerModifierTest.masksFieldNamedToken" --rerun-tasks` 确认 deterministic
- [x] 3.5 跑 3 个 sensitive value test 全集(SensitiveValueBeanSerializerModifierTest + SensitiveValueMaskerTest + JacksonSensitiveValueConfigTest)全 PASS
- [x] 3.6 若改 fixture 后**仍 fail**,进入 Phase 4:改 `SensitiveValueMasker` 的 field-name matcher
- [x] 3.7 Commit test 改动(若 Phase 4 改了 masker,一起 commit)

## 4. Testcontainers reuse 验证(已就位)

- [x] 4.1 跑 4 个 repository slice test:`./gradlew test --tests "com.seafood.*.infra.*RepositorySliceTest" -x processTestAot`
- [x] 4.2 grep 容器启动日志(在 test reports 里),确认只 1 次 "Container started"
- [x] 4.3 记录总耗时(Sprint 3 末 + D3 时对比)
- [x] 4.4 不需改代码(已正确实现),只在本 change `design.md` 标注"已就位"

## 5. 验证(本 change 完成判据)

- [x] 5.1 `./gradlew check -PexcludeTags=docker -x processTestAot` 全 PASS(包括新增 `jacocoTestCoverageVerification`)
- [x] 5.2 `./gradlew check --tests "*RepositorySliceTest"` 全 PASS(Docker 已启)
- [x] 5.3 GitHub Actions 跑 PR CI:4 jobs 并行,总 wall-clock < 8 min(从 ~12 min 降)
- [x] 5.4 验证 `masksFieldNamedToken` 10 连跑全 PASS(无 flake)
- [x] 5.5 验证 4 个 repository slice test 共享 1 个 mongo:7 容器

## 6. Final commit + archive

- [x] 6.1 Commit 所有改动(分 3 个 commit:ci.yml / build.gradle / test fix)
- [x] 6.2 跑 `/opsx:apply` 的最后一个 step:标记 tasks 全 ✅
- [x] 6.3 跑 `/opsx:archive sprint-3-ci-speed`
- [x] 6.4 更新 `openspec/changes/test-suite-roadmap/tasks.md` T5 勾 "Sprint 3 B done"

## 7. Reference

- Spec: `specs/ci-speed/spec.md`(4 R, 9 S)+ `specs/coverage-gate/spec.md`(4 R, 11 S)+ `specs/test-flake-fix/spec.md`(3 R, 8 S)= 11 R, 28 S
- Design: `design.md`(8 节,260 行)
- Proposal: `proposal.md`
- 父路线图:`test-suite-roadmap/design.md` §3.2 Sprint 3
- 后续:`A 续` 下一 change,Sprint 4 D3 + C1 + C3
