## ADDED Requirements

### Requirement: SensitiveValueBeanSerializerModifierTest.masksFieldNamedToken is deterministic
The system MUST fix `com.seafood.shared.config.SensitiveValueBeanSerializerModifierTest.masksFieldNamedToken` so that the test passes deterministically on every run on the baseline CI runner (Ubuntu 24.04 + JDK 25 + Gradle 9.4). The test currently fails intermittently with the assertion: `expected: "accessToken":"eyJh***"` but actual is `"accessToken":"eyJhbGciOi"` — root cause is one of: Jackson `BeanSerializerModifier` reflection ordering in JDK 25, the `SensitiveValueMasker` truncation length, or test fixture input length.

#### Scenario: Test passes deterministically across 10 consecutive runs
- **WHEN** a developer runs `cd backend && ./gradlew test --tests "com.seafood.shared.config.SensitiveValueBeanSerializerModifierTest.masksFieldNamedToken" --rerun-tasks` 10 times in succession
- **THEN** all 10 runs pass with 0 failures and 0 errors
- **THEN** the build exits 0 each time

#### Scenario: Test passes under parallel execution
- **WHEN** `./gradlew test` runs with parallel test execution enabled
- **THEN** the `masksFieldNamedToken` test passes regardless of which worker thread executes it
- **THEN** there is no shared mutable state between test invocations

#### Scenario: Root cause is documented in test javadoc
- **WHEN** the fix is committed
- **THEN** the test method's javadoc includes a 1-2 sentence explanation of what was wrong (e.g. "Jackson 2.18+ dispatches BeanSerializerModifier differently in JDK 25; explicit `writeString` override avoids the silent fallback")
- **THEN** a future maintainer reading the test understands the fix without consulting the change history

### Requirement: Test fixture inputs match production truncation length
The system MUST ensure that `SensitiveValueBeanSerializerModifierTest` fixture input values (`"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."` style JWT-like strings) are long enough that the masker's truncation is exercised against the same input length as production JWTs (typically 200+ chars). Short fixtures (e.g. 8 chars) may produce a false negative where the truncated output happens to equal the un-masked string by coincidence.

#### Scenario: Fixture string is realistic JWT length
- **WHEN** the test fixture is constructed
- **THEN** the JWT-like string is at least 200 characters long (representative of real tokens)
- **THEN** the assertion compares masked output against the expected truncation of the long input (e.g. first 4 chars + `***` + length), not against a coincidentally short string

#### Scenario: Assertion is order-independent
- **WHEN** the test runs
- **THEN** the assertion does not depend on internal Jackson field ordering
- **THEN** re-running with `-Dnet.bytebuddy.experimental=true` or other JVM flags does not flip pass/fail

### Requirement: Test failures are localized to one test class
The system MUST ensure that fixing the `masksFieldNamedToken` test does not require changes to any other test in `com.seafood.shared.config` package (i.e. `SensitiveValueMaskerTest`, `JacksonSensitiveValueConfigTest`). All 3 test classes in that package MUST continue to pass after the fix.

#### Scenario: All 3 sensitive value tests pass
- **WHEN** `./gradlew test --tests "com.seafood.shared.config.*"` runs
- **THEN** all 3 test classes pass: `SensitiveValueBeanSerializerModifierTest`, `SensitiveValueMaskerTest`, `JacksonSensitiveValueConfigTest`
- **THEN** total test count for the package is unchanged (no tests deleted, no tests skipped)

#### Scenario: Fix does not require new dependencies
- **WHEN** the fix is applied
- **THEN** `backend/build.gradle` does not gain any new `testImplementation` dependency
- **THEN** the fix is achievable using Jackson's standard API or a small test-fixture adjustment
