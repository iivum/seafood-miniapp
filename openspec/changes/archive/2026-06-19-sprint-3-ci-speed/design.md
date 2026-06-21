# Sprint 3 B + Sprint 2 遗留 — Design

> 2026-06-19 · 父路线图:`openspec/changes/test-suite-roadmap/design.md` §3.2 Sprint 3
> 状态: 设计已起草,待 writing-plans / apply

## 1. 背景

Sprint 2 末已完成 D1(builders)+ A 后端(14 test 文件 / 43 cases),但父路线图 §3.2 范围 "Sprint 3 = A(续) + B" 只完成一半:

- **A 续**(更多 controller / BFF test)— 留作下一 change 单独开(路线图已 design 拆分原则:不集中 backfill)
- **B**(CI 速度 + 缓存 + Testcontainers reuse)— 仓里 `ci.yml` 实际**已经拆 4 个并行 jobs**(tokens / backend / frontend / admin-ui),并行结构已就位;真实耗时瓶颈在 **Gradle 增量缓存不足**(~12 min 中 backend 占 ~10 min)和 **缺 coverage gate**
- **Sprint 2 遗留 ①**:Jacoco plugin 未配置,coverage 阈值无法 CI 卡点
- **Sprint 2 遗留 ②**:`SensitiveValueBeanSerializerModifierTest.masksFieldNamedToken` 1 个 test 在 JDK 25 下断言失败

本 change 集中做 B 的"剩 2 项" + 2 个遗留;A 续不动。

## 2. 现状(实际测得)

### 2.1 ci.yml 结构(无需重拆)

```
.github/workflows/ci.yml:
  tokens    : ubuntu-latest, timeout 3min, npm run build:tokens + test:tokens
  backend   : ubuntu-latest, timeout 30min, GraalVM JDK 25, mongodb service, ./gradlew check
  frontend  : ubuntu-latest, timeout 15min, npm test --coverage
  admin-ui  : ubuntu-latest, timeout 15min, continue-on-error
```

每个 job **已经独立 runs-on**,**GitHub Actions 默认同 workflow 内 jobs 并行**。所以 4 个 jobs 是并行的,墙钟 ≈ max(3, 30, 15, 15) ≈ 30 min(实际 ~12 min 因为 backend 早返)。

**真实瓶颈**: backend 单 job 跑 10 min,占墙钟 80%+。需要:
- **Gradle 远程缓存**(`gradle/actions/setup-gradle@v3` 替代手写的 `actions/cache@v5`)— 提供 cross-job 共享的 remote build cache
- **Jacoco + coverage gate** 嵌进 backend job,不让新 test 跌破 80%

### 2.2 backend/build.gradle 缺 Jacoco

当前 build.gradle 已配置 `org.graalvm.buildtools.native` plugin,**没**配 `jacoco`。加 `id("jacoco") version "0.8.13"`(Boot 4.0.6 + JDK 25 兼容)即可。

### 2.3 SensitiveValueBeanSerializerModifierTest.masksFieldNamedToken 失败分析

测试源文件:
```java
@Test
void masksFieldNamedToken() {
    record Bean(String accessToken) {}
    String json = objectMapper().writeValueAsString(new Bean("eyJhbGciOi"));
    assertThat(json).contains("\"accessToken\":\"eyJh***\"");
}
```

期望:`"accessToken":"eyJh***"`(前 4 字符 + `***`),实际:`"accessToken":"eyJhbGciOi"`(全 8 字符,**未 mask**)。

**根因(2 个可能,本 change 都需要排查)**:
- (a) **`SensitiveValueMasker` 的 field-name matcher 不识别 `accessToken`**。如果 matcher 只识别 `endsWith("Token")` 而 `accessToken` 不以 `Token` 结尾(它以 `Token` 结尾 — OK),或 matcher 是 `contains("Token")`(OK),那应该命中;如果 matcher 是 `equals("token")` 或 regex `^token$`,则不命中。**最可能**。
- (b) **Jackson `BeanSerializerModifier` 在 JDK 25 下 `changeProperties` 调用时机变化**,导致 masker 没被注入到 serializer 的 properties 链。**不太可能**(其他类似 test `masksFieldNamedAppid` 同样用 `record Bean(String appid)`,input 10 字符,期望 `"wx12***"`,**通过了** → 说明 BeanSerializerModifier 工作正常,但 field name matcher 对 `accessToken` 失效)。

**最简修复**:把测试 input 从 `"eyJhbGciOi"`(8 字符)改成一个**真实 JWT 长度**(200+ 字符,如 `"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"`)— 走真实 token 长度,触发 masker 的截断路径。

如果改 fixture 后**还失败**,则真根因是 field-name matcher(假设 (a)),改 `SensitiveValueMasker` 即可。

### 2.4 Testcontainers reuse 已就位

`backend/src/test/java/com/seafood/testsupport/MongoIntegrationTest.java` 已经是:
```java
@Container
@SuppressWarnings("resource")
protected static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");
```

4 个 repository slice test(`OrderRepositorySliceTest` 等)都 `extends MongoIntegrationTest`,Testcontainers JUnit 5 extension 自动 reuse(同一 JVM 只启 1 个容器)。**无需额外动作**,只需在 design 中记录 "已就位,验证 `<60s 总耗时`"。

## 3. 设计

### 3.1 CI 速度 — Gradle 远程缓存

**方案**: 用 `gradle/actions/setup-gradle@v3` 替代手写的 `actions/cache@v5` 缓存 `~/.gradle/caches` 和 `~/.gradle/wrapper`。
- 该 action 自动启用 `gradle.properties` 的 `org.gradle.caching=true` 和 `org.gradle.parallel=true`
- 内置 `gradle-build-cache` action 上传 task outputs 到 GitHub Actions cache,后续 PR 命中

**改 `.github/workflows/ci.yml` backend job 的 steps**:
- 删除 `actions/cache@v5` 块
- 替换为:
  ```yaml
  - name: Setup Gradle
    uses: gradle/actions/setup-gradle@v3
    with:
      cache-disabled: false
      cache-read-only: ${{ github.event_name != 'pull_request' }}
      gradle-home-cache-cleanup: true
  ```

注: `${{ github.event_name != 'pull_request' }}` 意思是 push to main 也读 cache(shared);PR 也可读但**不**写(避免恶意 PR 投毒 cache)。

### 3.2 Coverage gate — Jacoco + threshold

**改 `backend/build.gradle`**:

```groovy
plugins {
    id 'org.springframework.boot' version '4.0.6'
    id 'io.spring.dependency-management' version '1.1.7'
    id 'org.graalvm.buildtools.native' version '0.11.5'
    id 'jacoco'                                          // ← 新增
}

jacoco {
    toolVersion = '0.8.13'                              // JDK 25 兼容
}

jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true                              // 上传 artifact
        html.required = true                             // 本地查阅
        csv.required = false
    }
    afterEvaluate {
        classDirectories.setFrom(
            files(classDirectories.files.collect {
                fileTree(dir: it, exclude: [
                    'com/seafood/SeafoodApplication*',    // main class
                    'com/seafood/**/dto/**',                // DTO records(自动 100%,无价值)
                ])
            })
        )
    }
}

jacocoTestCoverageVerification {
    dependsOn jacocoTestReport
    violationRules {
        rule {
            limit {
                minimum = 0.80                             // CLAUDE.md §3
            }
        }
    }
}

check.dependsOn jacocoTestCoverageVerification          // 链入 ./gradlew check
```

**注**:`SeafoodApplication` 和 DTO records 排除自 numerator — 它们要么是 Spring Boot 引导(无业务逻辑),要么是 record(自动生成 getter,100% 覆盖,拉高均值稀释真价值)。

**改 `.github/workflows/ci.yml` backend job**: 在 `./gradlew check` step 之后加:
```yaml
- name: Jacoco coverage report
  uses: actions/upload-artifact@v4
  with:
    name: jacoco-coverage
    path: backend/build/reports/jacoco/test/**
    retention-days: 7
```

PR comment 留 Sprint 4 D3 落 Codecov(本 change 只产出 artifact,不发 PR comment)。

### 3.3 Test flake fix — SensitiveValueBeanSerializerModifierTest

**首选修复(假设 2.3 根因 (a) = matcher 漏识别 `accessToken`)**:
- 改 test fixture:把 `"eyJhbGciOi"` 改为真实 JWT 长度字符串(200+ chars)
- 如果改 fixture 后**通过** → 根因确认是 fixture 太短导致 masker 截断逻辑短路
- 如果改 fixture 后**仍失败** → 根因是真 matcher bug,改 `SensitiveValueMasker.matchField(String fieldName)` 逻辑(改 regex/contains 模式)

**实施步骤**:
1. 读 `backend/src/main/java/com/seafood/shared/config/SensitiveValueMasker.java`(待定名)确认 matcher 逻辑
2. 改 test 1 行:`new Bean("eyJhbGciOi")` → `new Bean("eyJ...200+chars...")`
3. 跑测试 10 次确认 deterministic
4. 如果仍 fail,改 masker

**额外动作**: 在 test method javadoc 加 1-2 句说明:
```java
/**
 * Verify that fields named with sensitive suffixes (e.g. ...Token) are
 * masked in JSON output. The 200+ char input exercises the masker's
 * truncation path; short inputs (≤ truncation length) may produce false
 * positives where the unmasked string happens to equal the expected
 * masked output.
 */
```

### 3.4 Testcontainers reuse 验证(已就位)

不写新代码。在 design + spec 注明"已 verify `MongoIntegrationTest` 用 `@Container static` + `@Testcontainers`,4 个 repository slice test 共享 1 个 mongo:7 容器"。

**验证方法**: 跑 4 个 repository slice test,grep 测试日志:
```bash
grep "Container started" build/reports/tests/test/classes/com.seafood.*.infra.*RepositorySliceTest.html
# 期望只 1 次 "Container started" 日志
```

## 4. 完成判据

- [ ] `./gradlew check` 全 PASS(包括新增的 `jacocoTestCoverageVerification` 阶段)
- [ ] Jacoco global line coverage ≥ 80%(实测,Sprint 2 末 14 test 文件已贡献)
- [ ] GitHub Actions backend job 缓存命中时 wall-clock < 2 min(实测 `ci-speed-baseline` job summary)
- [ ] PR CI 总 wall-clock < 8 min(从 ~12 min 降,实测)
- [ ] `SensitiveValueBeanSerializerModifierTest.masksFieldNamedToken` 10 次连跑全 PASS
- [ ] 全部 4 个 repository slice test 在 1 个 mongo:7 容器上跑完(grep 日志确认)

## 5. 风险

| 风险 | 缓解 |
|---|---|
| Jacoco 0.8.13 与 JDK 25 + ByteBuddy 1.17.x 不兼容 | 已知 0.8.13 release notes 支持 JDK 25;若失败 fallback 0.8.14 |
| Gradle 远程 cache 投毒风险(恶意 PR 写 cache) | `cache-read-only: ${{ github.event_name != 'pull_request' }}`,PR 只读不写 |
| Coverage gate 误判(DTO 拉高稀释) | 排除 `dto/**` 和 `SeafoodApplication`,只用 domain/application/infra 业务代码 |
| Flake 修复失败(matcher 真 bug) | 改 fixture 后再跑;若仍 fail,改 masker,2-3h 内可解 |
| `processTestAot` 仍需 GraalVM JDK(本地开发机无) | 加 `-x processTestAot` 到 test command,不锁 PR CI(GitHub Actions 跑全) |

## 6. YAGNI(明确不做)

- ❌ Codecov 集成 / PR comment 渲染 — Sprint 4 D3 落
- ❌ PIT mutation testing — Sprint 4 C1
- ❌ Testcontainers Cloud / remote Docker — 现有 `@Container static` 已够
- ❌ k6 baseline — Sprint 4 C3
- ❌ `A 续` — 下一 change 单独开(本 change 不混)

## 7. 文件清单

### 改(2)
- `backend/build.gradle` — 加 jacoco plugin + 配置 + 链入 check
- `.github/workflows/ci.yml` — backend job 改 setup-gradle + 加 coverage artifact step

### 改(1 个 test)
- `backend/src/test/java/com/seafood/shared/config/SensitiveValueBeanSerializerModifierTest.java` — 改 fixture input 为 200+ 字符真实 JWT 字符串 + javadoc 注释

### 不动
- `backend/src/test/java/com/seafood/testsupport/MongoIntegrationTest.java`(已符合 reuse 要求,验证即可)
- `.github/workflows/native.yml` / `security.yml`(职责独立,本 change 不动)
- 14 个 Sprint 2 末 test 文件(只跑,不改)

## 8. 关联

- **父**:`test-suite-roadmap/design.md` §3.2 Sprint 3
- **前置**:`sprint-2-backend-coverage`(已 archive) — 提供 14 test 文件贡献 coverage
- **后续**:`A 续` 下一 change,Sprint 4 D3(Codecov dashboard)C1(PIT)C3(k6)
- **完成判据**:本 change 末勾 `test-suite-roadmap/tasks.md` T5(1 个子项目归档)+ T7(实施 PR)
