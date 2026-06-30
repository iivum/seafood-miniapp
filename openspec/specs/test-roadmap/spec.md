# Capability: Test Roadmap

## Purpose

Define the test infrastructure, coverage targets, CI speed targets, and sustainability gates for the seafood-miniapp project. Covers backend builders, coverage gap closure, CI performance, mutation testing, load testing, and long-term test debt prevention.

## Requirements

### Requirement: Test data builders for backend domain entities
The system MUST provide reusable test data builder classes (one per aggregate root) that allow tests to construct fully-populated domain entities in a single expression with sensible defaults and the ability to override any field. The builders MUST cover at minimum: `OrderBuilder`, `ProductBuilder`, `UserBuilder`, `CartBuilder`, `RefundBuilder`. Each builder MUST be located under `backend/src/test/java/com/seafood/<module>/domain/` (or equivalent fixture package) and MUST NOT introduce runtime dependencies into main source.

#### Scenario: Default-constructed order via builder
- **WHEN** a test calls `OrderBuilder.anOrder().build()` with no overrides
- **THEN** the returned `Order` is non-null, has a non-blank id, a default `PENDING` status, a non-empty items list, and round-trips through `OrderMapper.toDocument` then `toDomain` without field loss

#### Scenario: Builder overrides propagate
- **WHEN** a test calls `OrderBuilder.anOrder().withStatus(OrderStatus.SHIPPED).withUserId("u-99").build()`
- **THEN** the returned `Order` has status `SHIPPED` and userId `"u-99"`, with all other fields at their defaults

### Requirement: Coverage gap closure for backend
The system MUST close the coverage gap for backend by adding controller-slice tests (each `@RestController` has at least one passing test), repository-slice tests (`@DataMongoTest` or equivalent for each `MongoRepository`), and BFF integration tests (each endpoint under `/api/admin/**` has at least one happy-path + one auth-rejection test). After gap closure, Jacoco line coverage MUST be ≥ 80% globally and ≥ 90% on domain/application layers.

#### Scenario: New controller introduced without slice test blocks merge
- **WHEN** a developer adds a new `@RestController` to `backend/src/main/java/com/seafood/**/api/`
- **THEN** a CI check fails with a clear message stating the missing controller slice test, blocking merge until the test is added

#### Scenario: Coverage drops below threshold fails CI
- **WHEN** Jacoco reports global line coverage below 80% on a PR build
- **THEN** the CI job exits non-zero and posts the coverage diff in the PR comment

### Requirement: CI speed and stability targets
The system MUST achieve PR CI total time under 8 minutes (down from a baseline of approximately 12 minutes), Gradle `check` incremental-cache-hit time under 2 minutes, and mini-program e2e flaky rate under 5% (measured as `flaky_runs / total_runs` over the last 100 CI runs).

#### Scenario: Testcontainers reuse cuts setup time
- **WHEN** the test suite starts
- **THEN** a single `MongoDBContainer` is reused across all integration tests in the run via `@Container static` + `@Testcontainers(disabledWithoutDocker = true)`, eliminating per-class container startup overhead

#### Scenario: CI jobs split by domain
- **WHEN** a PR is opened
- **THEN** the `ci.yml` workflow runs backend / mini-program / admin-ui / native as 4 parallel jobs, each reporting its own status, so a failure in one domain does not block the others

#### Scenario: Mini-program e2e retries on transient failure
- **WHEN** a `mp-3layer.test.ts` run fails with a WebSocket-stall or DevTools-launch timeout
- **THEN** the test runner retries up to 2 times with exponential backoff before marking the run as failed, keeping flaky rate under 5%

### Requirement: Test infrastructure (builders, fixtures, dashboard)
The system MUST ship a test-data builder library (Requirement 1), a shared test fixture base class `MongoIntegrationTest` for backend integration tests, and a coverage dashboard (Jacoco + Codecov or self-hosted GitHub Pages) that shows per-file coverage and PR diff. The dashboard MUST be linked from the project README and from every PR comment via a Codecov bot.

#### Scenario: Builder usage in new tests
- **WHEN** a developer writes a new backend test
- **THEN** they use the relevant builder (e.g. `OrderBuilder.anOrder().withStatus(...)`) and the test class imports only from the `builders/` fixture package, not from inline `new Order(...)` constructors

#### Scenario: Coverage dashboard shows per-file breakdown
- **WHEN** a developer opens the coverage dashboard
- **THEN** they can drill down from global percentage to per-package to per-file coverage, and the PR comment shows a diff highlighting newly-added or newly-uncovered lines

### Requirement: New testing capabilities (PIT mutation, k6 load, deferred items)
The system MUST integrate PIT mutation testing (mutation score ≥ 70% on domain/application layers), and k6 load testing (5 core endpoints: `GET /api/products`, `GET /api/orders`, `POST /api/orders`, `POST /api/admin/auth/login`, `GET /api/admin/orders` — all with P99 < 500ms under documented load). Spring Cloud Contract (C2), jqwik property-based testing (C4), and visual diff (C5) are explicitly out of scope and MAY be addressed in future Sprints.

#### Scenario: PIT mutation score gates PR merge
- **WHEN** PIT runs on a backend PR build
- **THEN** if mutation score on domain or application layers drops below 70% compared to the trunk baseline, the CI job exits non-zero and the PR cannot merge

#### Scenario: k6 baseline recorded for each core endpoint
- **WHEN** the k6 nightly job runs
- **THEN** a JSON report per endpoint is uploaded to the project's reports artifact, capturing P50 / P95 / P99 latency and requests-per-second under the documented test load; any endpoint with P99 ≥ 500ms is flagged in the report

#### Scenario: Deferred capabilities not implemented in this change
- **WHEN** the team reviews the test-suite-roadmap after this change ships
- **THEN** Spring Cloud Contract (C2), jqwik property-based testing (C4), and visual diff (C5) remain unimplemented; the change explicitly states these are deferred to a future Sprint and references their original proposal sections

### Requirement: Sustainability and observability
The system MUST enforce that every new feature PR includes a TDD-driven test set (PR template check), and MUST publish coverage trend + mutation score trend in the PR comment. This requirement is the long-term operational gate that prevents test debt from re-accumulating after the initial gap closure Sprints.

#### Scenario: PR template requires test plan
- **WHEN** a developer opens a new PR
- **THEN** the PR template includes a "Test plan" section that must be filled in, and a CI lint job fails the PR if the section is empty

#### Scenario: Coverage trend visible on every PR
- **WHEN** a PR is opened or updated
- **THEN** a Codecov (or equivalent) bot comment appears showing the coverage diff and the trend over the last 10 commits, so reviewers can spot regressions without leaving GitHub
