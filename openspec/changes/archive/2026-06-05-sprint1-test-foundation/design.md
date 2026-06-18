## Context

The repository completed a single-module Spring Boot 4.0.6 refactor (5 commits pending PR on `feature/refactor`). Current state of the test suite:

- 77 test cases passing at ~80% line coverage.
- All tests run on the JVM (`./gradlew test`); no `nativeTest` task wired.
- The build enforces a `@RefreshScope` static check (`scripts/check-no-refresh-scope.sh`, bound to `gradle check`).
- DDD layering (api / application / domain / infra) is a documented rule but has no automated guard.
- No Testcontainers usage; MongoDB-backed tests are not exercising a real database today.
- Spring Boot 4.0 removed `@MockBean` / `@SpyBean` and the `MockitoTestExecutionListener`; `@SpringBootTest` no longer auto-configures `MockMvc` / `WebClient` / `TestRestTemplate`. These removals affect any test that imported the old annotations.

> **Note on project context**: the project context file describes `@WebMvcTest + @MockBean` as "unavailable in Spring Boot 4, use plain JUnit + Mockito." This is partially inaccurate: `@WebMvcTest` is one of 20+ official slice annotations in Boot 4.0.7-SNAPSHOT and is fully supported. `@MockBean` is **removed** (replaced by `@MockitoBean` from Spring Framework 7), but the slice itself is not. This design uses the official Boot 4 stack (`@WebMvcTest` + `@MockitoBean` + `MockitoExtension`) and does not regress to plain JUnit + Mockito for slice tests. The context's rule should be updated in a follow-up.

## Goals / Non-Goals

**Goals:**

- One test-source tree that compiles on Spring Boot 4.0.6 with zero deprecation warnings related to the testing stack.
- DDD layering violations fail the build.
- MongoDB integration tests run against a real MongoDB 7 instance via Testcontainers.
- All new test classes follow the same style as the existing 77 tests (JUnit 5, AssertJ, `@DisplayName` if already used).
- `./gradlew check` remains the single CI entry point; no new top-level Gradle task is required.
- Net coverage does not regress; new tests contribute toward the 80% backend target.

**Non-Goals:**

- Visual regression, E2E (Playwright / miniprogram-automator), OpenAPI Diff, PIT mutation testing, OWASP Dep-Check, Trivy, GraalVM native-test parity, `@ConfigurationPropertiesTest`. These belong to later changes (Sprint 2 / 3 in the research synthesis).
- Refactoring production code beyond the minimum needed to make the new tests compile.
- Introducing a new code-coverage tool or changing the coverage report format.
- Changing the existing `scripts/check-no-refresh-scope.sh` rule surface for production sources (already correct).

## Decisions

### 1. Annotation migration policy: file-by-file mechanical rename, no behavior change

- Audit `backend/src/test/java/**` for `@MockBean`, `@SpyBean`, and any `MockitoTestExecutionListener` reference.
- Rename to `@MockitoBean` / `@MockitoSpyBean` (Spring Framework 7) and add `MockitoExtension` where missing.
- Where `@SpringBootTest` is used, add explicit `MockMvc` / `WebClient` / `TestRestTemplate` configuration per the Boot 4 migration guide.
- **Rationale**: the research verified 208/267 claims including the official removal of these annotations; the migration is mechanical and reversible.
- **Alternative considered**: revert to plain JUnit + Mockito for all tests. Rejected — loses `@WebMvcTest` request-mapping / serialization assertions that the project would otherwise have to hand-roll.

### 2. Testcontainers scope: MongoDB only in Sprint 1

- Add `org.testcontainers:postgresql` ... no — add `org.testcontainers:mongodb` only.
- One shared static `MongoDBContainer("mongo:7")` in a base class `MongoIntegrationTest` reused by `@DataMongoTest` IT classes.
- Reuse the existing `docker-compose.yml`'s `mongodb` service for local dev (no second container).
- **Rationale**: MongoDB is the only stateful dependency in the single-module backend. Testcontainers gives an integration test against the same image the production compose uses, removing the "tests pass locally, fail in CI" class of bug.
- **Alternative considered**: embedded Flapdoodle / de.flapdoodle.embed.mongo. Rejected — Boot 4 + GraalVM Native deprecates embedded Mongo drivers; Testcontainers is the official replacement.

### 3. ArchUnit rule set: 4 rules covering the 4 worst historical violations

Rules:

1. `api` must not depend on `infra`. Rationale: preserves re-split option (Design §1.3).
2. `bff` must not depend on `infra` (and not on other modules' `infra`). Rationale: BFF composes ApplicationServices only.
3. `domain` must not depend on `org.springframework.*` (except `org.springframework.data.annotation.Id` and `org.springframework.data.mongodb.core.mapping.Document` — explicit allow-list). Rationale: domain stays framework-agnostic and JVM-pure.
4. `controller` (in `api` packages) classes must not declare fields / constructors that take a `*Repository` type. Rationale: enforces the controller→service→repository flow.

- One `ArchitectureTest` class under `backend/src/test/java/com/seafood/architecture/`.
- Rules run as part of `./gradlew test`. On violation, Gradle fails with file + line.
- **Rationale**: the four rules above map to the most common accidental boundary crossings observed during the multi→single-module refactor.
- **Alternative considered**: full ArchUnit 50-rule library. Rejected — over-restrictive, will require a `freezeRules` allow-list day one and create PR noise.

### 4. Static check for `@RefreshScope` in test sources: extend the existing script

- The existing `scripts/check-no-refresh-scope.sh` scans `src/main/java`. Mirror it to scan `src/test/java` as well, or extend the loop.
- Fail with the same exit code and message format.
- **Rationale**: any new test that imports `@RefreshScope` would silently break Native compilation. The test source tree is also subject to the rule.

### 5. `@ConfigurationPropertiesTest`: 1 happy path per known binding (Sprint 1 minimum)

- Add a single `ConfigurationPropertiesBindingTest` that loads `application.yml` and asserts the three known property prefixes bind: `spring.data.mongodb.uri`, `jwt.*` (the application-specific root, not the Spring prefix), and (if present) `wechat.*`.
- **Rationale**: catches `kebab-case ↔ camelCase` mapping regressions and missing `prefix=` on `@ConfigurationProperties` classes. Three assertions cover 80% of the value.
- **Alternative considered**: full per-class binding tests. Deferred to Sprint 2.

### 6. Gradle dependency graph

- Add to `dependencies { testImplementation(...) }` in `backend/build.gradle`:
  - `org.testcontainers:junit-jupiter` (testcontainers-bom manages versions)
  - `org.testcontainers:mongodb`
  - `com.tngtech.archunit:archunit-junit5`
  - The Boot 4 BOM already manages JUnit 5, Mockito, AssertJ, and Spring Test — do **not** pin versions explicitly.
- **Rationale**: stays inside the BOM-managed versions, no upgrade debt.

## Risks / Trade-offs

- **Testcontainers requires Docker on the developer machine and CI runner.** → Mitigation: document the prerequisite in CLAUDE.md `## 运行测试`; add a `task named 'testContainersFast'` that filters `@Tag("docker")` tests so contributors without Docker can still run the JVM-only tests.
- **ArchUnit false positives on reflection-based dependencies** (e.g. Lombok-generated methods, Spring Data proxy methods). → Mitigation: rule set is narrow (4 rules), with explicit allow-list for the two `org.springframework.data.*` annotations in the domain allow-list. If a future rule needs an allow-list, add it as a named `freezingRule` with a comment.
- **Annotation migration touches 77 existing tests** → Mitigation: do the audit + rename in a single PR, run `./gradlew check` once to surface compile errors, fix in a follow-up if any test depends on removed auto-configuration.
- **Testcontainers startup adds ~10s to the first integration test in a Gradle run** → Mitigation: share the static container across the test JVM (Testcontainers reuses the container if declared `static`); keep the slice tests (which don't need the container) on the default `gradle test` path and isolate IT classes via a JUnit 5 `@Tag("docker")`.
- **Boot 4 `MockitoTestExecutionListener` removal means existing tests using `@MockBean` and relying on listener-injected behavior will silently misbehave** (not just fail to compile). → Mitigation: the rename to `@MockitoBean` is compile-time enforced; the audit pass also greps for `MockitoTestExecutionListener` and `MockMvcAutoConfiguration` references in test sources.
