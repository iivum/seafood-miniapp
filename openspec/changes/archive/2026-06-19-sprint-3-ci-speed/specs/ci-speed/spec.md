## ADDED Requirements

### Requirement: PR CI runs as parallel jobs per domain
The system MUST split PR CI into at least 4 parallel jobs, one per domain: `backend` (Gradle test + check), `mp` (Jest 4-layer visual verification), `admin-ui` (npm test), `native` (GraalVM nativeCompile smoke). Each job MUST report its own status independently — a failure in `mp` MUST NOT block `backend` from completing and reporting success. PR CI total wall-clock time MUST be below 8 minutes on the baseline runner; jobs MUST emit `timing` job-summary fields for trend tracking.

#### Scenario: 4 jobs run in parallel on a PR
- **WHEN** a developer opens a pull request
- **THEN** GitHub Actions triggers 4 jobs in parallel (backend, mp, admin-ui, native), each starts within 1 minute of PR open
- **THEN** the PR check status aggregates as 4 separate check runs, each with its own success/failure status

#### Scenario: One job failure does not block the others
- **WHEN** the `mp` job fails (e.g. WebSocket stall on a visual verification test)
- **THEN** the `backend`, `admin-ui`, and `native` jobs continue to completion
- **THEN** the PR shows 1 failed + 3 successful check runs
- **THEN** a developer can merge if only the `mp` failure is non-blocking (configurable via `required-checks` rules)

#### Scenario: PR CI total wall time stays under 8 minutes
- **WHEN** all 4 jobs complete
- **THEN** the wall-clock time from PR open to all jobs reported is below 8 minutes
- **THEN** the trend is recorded as a job-summary `ci-speed-baseline` artifact for Sprint 4 comparison

### Requirement: Gradle build cache reduces incremental check time
The system MUST enable Gradle's local + remote build cache for `./gradlew check` so that a no-op change (e.g. docs-only PR) completes the `check` task in under 2 minutes on the baseline runner. The cache MUST be keyed on inputs (source files, dependencies) and invalidated only when those change.

#### Scenario: Cache hit on docs-only PR
- **WHEN** a PR modifies only `docs/` or `openspec/` and no Java/TS/JSON source files
- **THEN** `./gradlew check` completes in under 2 minutes (cache hit on all task outputs)
- **THEN** the cache-hit rate is recorded as a job-summary `gradle-cache-hit` metric

#### Scenario: Cache invalidation on source change
- **WHEN** a PR modifies any `.java` file under `backend/src/`
- **THEN** Gradle invalidates the relevant cache entries and re-runs only the affected tasks
- **THEN** the build still completes in under 8 minutes wall-clock (degraded but bounded)

### Requirement: Testcontainers reuse across repository slice tests
The system MUST ensure that all `@Tag("docker")` repository slice tests share a single `mongo:7` Testcontainers container per test JVM, so container startup overhead is paid once. The existing `MongoIntegrationTest` base class MUST use `@Container static` + `@Testcontainers` so JUnit 5's Testcontainers extension reuses the container across all subclass test methods. The reuse MUST be verified by a log assertion or by a test-class counter — startup time MUST NOT scale linearly with the number of repository test classes.

#### Scenario: Container starts once for the JVM
- **WHEN** `./gradlew test --tests "com.seafood.*.infra.*RepositorySliceTest"` runs N test classes (N ≥ 2)
- **THEN** the mongo:7 container is started exactly once (logged by Testcontainers as "Container started")
- **THEN** the total test execution time for the 4 repository slice classes is under 60 seconds (vs. ~120 seconds if each class started its own container)

#### Scenario: Container is reused across test methods
- **WHEN** `OrderRepositorySliceTest` has 4 test methods and `ProductRepositorySliceTest` has 4 test methods
- **THEN** both classes connect to the same container (replica set URL identical)
- **THEN** the `mongo:7` container shows exactly 1 startup log entry in the test output, not 8

### Requirement: CI logs are surfaced for debugging
The system MUST upload test reports (`build/test-results/**/*.xml`, `build/reports/jacoco/**`) as GitHub Actions artifacts on every CI run, with a retention of at least 7 days. Job summaries MUST include a one-line status table: `domain | tests | failures | duration`.

#### Scenario: Test reports uploaded as artifacts
- **WHEN** any CI job completes
- **THEN** the corresponding test result XML files and HTML reports are uploaded as a downloadable artifact
- **THEN** a developer can download the artifact from the failed job's run page

#### Scenario: Job summary shows test counts
- **WHEN** a CI job completes
- **THEN** the job summary on GitHub shows a Markdown table with columns: domain, tests run, failures, duration (e.g. `backend | 422 | 1 | 1m32s`)
