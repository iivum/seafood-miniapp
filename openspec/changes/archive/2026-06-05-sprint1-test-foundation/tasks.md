## 1. Audit Existing Test Annotations (RESOLVED: no migration needed)

- [x] 1.1 `grep -rE "@MockBean|@SpyBean|MockitoTestExecutionListener" backend/src/test/java` returned **zero hits**. The 9 existing test files are pure JUnit 5 + hand-written `mock(...)` calls (e.g. `ProductServiceTest.java:37`). They compile and run on Spring Boot 4.0.6 without any annotation migration.
- [x] 1.2 No `@SpringBootTest`, `@WebMvcTest`, `@DataMongoTest`, `@JsonTest`, or `@MockitoBean` annotations exist in the test tree. Slice tests will be **added from scratch** in task group 3 rather than migrated.

## 2. Add Testcontainers MongoDB

- [ ] 2.1 Add to `backend/build.gradle` `dependencies { testImplementation(...) }`: `org.testcontainers:junit-jupiter` and `org.testcontainers:mongodb` (versions managed by `testcontainers-bom`)
- [ ] 2.2 Import `testcontainers-bom` in the same `build.gradle` (`enforcedPlatform` not required; the BOM is sufficient for test scope)
- [ ] 2.3 Create `backend/src/test/java/com/seafood/testsupport/MongoIntegrationTest.java` with one `static MongoDBContainer mongo = new MongoDBContainer("mongo:7")` field, `@Container` annotation, and an `@DynamicPropertySource` that wires `spring.data.mongodb.uri` to the container's replica-set URL
- [ ] 2.4 Add `@Tag("docker")` to `MongoIntegrationTest` so Docker-less contributors can skip the suite
- [ ] 2.5 Add a single smoke test in `MongoIntegrationTest` that writes and reads a `Product` document end-to-end

## 3. Convert One Existing Test Per Slice to Its Official Boot 4 Slice

- [ ] 3.1 Pick one controller test in `backend/src/test/java/com/seafood/product/api/` and convert it to `@WebMvcTest(ProductController.class)` with `@MockitoBean` for the service
- [ ] 3.2 Pick one repository-touching test and convert it to `@DataMongoTest` extending `MongoIntegrationTest`
- [ ] 3.3 Pick one DTO test and convert it to `@JsonTest` validating the record's round-trip serialization
- [ ] 3.4 Run `./gradlew :test` and confirm all three converted tests pass and run noticeably faster than the `@SpringBootTest` baseline

## 4. Add ArchUnit DDD Layer Guardrails

- [ ] 4.1 Add `com.tngtech.archunit:archunit-junit5` to `backend/build.gradle` test dependencies (Boot 4 BOM does not manage archunit; pin a 1.x version)
- [ ] 4.2 Create `backend/src/test/java/com/seafood/architecture/ArchitectureTest.java` annotated with `@AnalyzeClasses(packages = "com.seafood")`
- [ ] 4.3 Implement rule 1: `api → infra` forbidden
- [ ] 4.4 Implement rule 2: `bff → infra` forbidden
- [ ] 4.5 Implement rule 3: `domain → org.springframework.*` forbidden except for the `data.annotation.Id` + `mongodb.core.mapping.Document` allow-list
- [ ] 4.6 Implement rule 4: `@RestController` / `@Controller` classes must not have a field or constructor parameter of type `*Repository`
- [ ] 4.7 Run `./gradlew :test --tests "*ArchitectureTest"` standalone; confirm rules pass on the current codebase
- [ ] 4.8 Introduce a deliberate violation in a throwaway branch, confirm the test fails, revert the throwaway code

## 5. `@RefreshScope` Static Check Covers Test Sources (RESOLVED: already covered)

- [x] 5.1 `scripts/check-no-refresh-scope.sh` sets `SRC="$ROOT/src"` and `grep -REn ... "$SRC"`. The recursive scan covers BOTH `src/main/java/**` and `src/test/java/**` in a single pass.
- [x] 5.2 Smoke test: re-ran the script after adding new test sources — exit 0, message confirms scan scope includes tests. No new pass needed.
- [x] 5.3 `gradle check` already depends on `checkNoRefreshScope` in `build.gradle`. No Gradle change required.

## 6. Add `@ConfigurationPropertiesTest` Smoke Coverage

- [ ] 6.1 Identify the three `@ConfigurationProperties` classes for MongoDB, JWT, and (if present) WeChat by reading the `backend/src/main/java/com/seafood/shared/config/` tree
- [ ] 6.2 Add `spring-boot-configuration-processor` to `compileOnly` in `backend/build.gradle` (no-op if already present from the Boot 4 starter)
- [ ] 6.3 Create `backend/src/test/java/com/seafood/shared/config/ConfigurationPropertiesBindingTest.java` using `@ConfigurationPropertiesTest` (or the Boot 4 equivalent)
- [ ] 6.4 Assert the three prefixes bind successfully when `application.yml` is loaded
- [ ] 6.5 Run `./gradlew :test --tests "*ConfigurationPropertiesBindingTest"` standalone

## 7. Wire Up CI Fast / Slow Split

- [ ] 7.1 In `backend/build.gradle`, add a `tasks.named("test") { useJUnitPlatform { excludeTags("docker") } }` profile or a system-property switch so a Docker-less run can skip ITs via `./gradlew test -PexcludeTags=docker`
- [ ] 7.2 Document the new flag in `CLAUDE.md` `## 运行测试` section
- [ ] 7.3 Update the `docker-compose.yml` note in `CLAUDE.md` to clarify that Testcontainers reuses the `mongodb` service port when `TESTCONTAINERS_HOST_OVERRIDE` is set

## 8. Final Verification

- [x] 8.1 `./gradlew check -PexcludeTags=docker` → BUILD SUCCESSFUL. ArchUnit 4 rules pass, `checkNoRefreshScope` passes, 78 JVM tests pass.
- [x] 8.2 `./gradlew test -PexcludeTags=docker` → BUILD SUCCESSFUL. Docker-tagged `MongoIntegrationTest` + `ProductDocumentRepositoryIT` skipped.
- [x] 8.3 ArchitectureTest reports `tests="4" failures="0"` — the 4 `@ArchTest` rules all evaluate against the current codebase and pass.
- [ ] 8.4 Commit with `feat(test): Sprint 1 test foundation — Testcontainers, ArchUnit, JsonTest, config binding`
- [ ] 8.5 Push the branch and open a PR against `feature/refactor` (or `main` if `feature/refactor` has already merged)

## Implementation Notes (deviation from original plan)

- **Annotation migration (group 1)**: dropped. Existing tests are pure JUnit 5 + Mockito. No `@MockBean` / `@SpyBean` in the tree.
- **`@WebMvcTest` slice (group 3)**: dropped. Spring Boot 4.0.6's `spring-boot-starter-test` does **not** bundle the `@WebMvcTest` slice annotation (it lives in a separate `spring-boot-test-autoconfigure-webmvc` module that the meta starter does not pull in). The pre-existing plain JUnit + Mockito tests already cover controller behavior via direct service calls. The original group 3 spec is preserved for a future change that adds the missing starter dependency.
- **`@DataMongoTest` slice**: dropped for the same reason. `MongoIntegrationTest` is now a raw `MongoClient` driver base class; `ProductDocumentRepositoryIT` writes BSON documents directly. This still validates the integration test path through Testcontainers, just without the Spring Data auto-configuration shortcut.
- **`@JsonTest` slice (group 3)**: kept. `@JsonTest` IS in the base `spring-boot-test-autoconfigure` jar, so this works as designed.
- **`@ConfigurationPropertiesTest` (group 6)**: scope reduced from 3 prefixes to 1 (`security.jwt.*`). MongoDB URI is auto-configured by Spring Boot, not via a custom binding; WeChat is read from environment variables, not a properties class. Only `JwtProperties` is a real `@ConfigurationProperties` candidate.
- **`@RefreshScope` script (group 5)**: no change needed. The existing `check-no-refresh-scope.sh` scans `$ROOT/src` recursively and already covers both `src/main/java` and `src/test/java` in one pass.
