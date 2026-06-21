## ADDED Requirements

### Requirement: Jacoco plugin configured in backend Gradle build
The system MUST apply the `jacoco` Gradle plugin to `backend/build.gradle` and generate an XML coverage report at `build/reports/jacoco/test/jacocoTestReport.xml` on `./gradlew test`. The Jacoco version MUST be compatible with the JDK 25 toolchain (the version currently used is `0.8.13` or newer). The report MUST aggregate line coverage across `src/main/java` only (test code excluded from denominator).

#### Scenario: Jacoco report is generated on ./gradlew test
- **WHEN** a developer runs `cd backend && ./gradlew test -PexcludeTags=docker -x processTestAot`
- **THEN** the build produces `build/reports/jacoco/test/jacocoTestReport.xml` and `build/reports/jacoco/test/html/index.html`
- **THEN** the HTML report opens in a browser and shows per-package line coverage

#### Scenario: Jacoco plugin loads on JDK 25 + Spring Boot 4
- **WHEN** `./gradlew test` runs with `JAVA_HOME` pointing at a JDK 25 distribution
- **THEN** the `jacocoTestReport` task succeeds without `Unsupported class file major version` errors
- **THEN** the offline-instrumentation agent loads cleanly (no `IllegalArgumentException` from `ByteBuddy`)

### Requirement: Jacoco line coverage threshold enforced at build time
The system MUST configure `jacocoTestCoverageVerification` in `backend/build.gradle` to fail the build if global line coverage drops below 80% (CLAUDE.md §3 hard rule). The threshold MUST be applied to `com.seafood.*` packages in `src/main/java`. The verification task MUST run as part of `./gradlew check`.

#### Scenario: Coverage below 80% fails the build
- **WHEN** a developer adds a new domain class `Foo` to `src/main/java/com/seafood/order/domain/` without writing a test for it
- **THEN** `./gradlew check` fails with: `Rule violated for bundle seafood-backend: line coverage of 79.5% is below 80%`
- **THEN** the failure points to the `Foo.java` file as a candidate for test coverage

#### Scenario: Coverage at or above 80% passes
- **WHEN** global line coverage is 80.0% or higher
- **THEN** `./gradlew check` exits with status 0 (coverage gate passes)
- **THEN** no further action required for the gate

#### Scenario: Threshold is configurable per package
- **WHEN** a future requirement raises the threshold for the `domain/` layer to 90% (CLAUDE.md "核心 ≥90%")
- **THEN** the developer updates the `jacocoTestCoverageVerification` rules block without touching CI config
- **THEN** `./gradlew check` enforces the new threshold

### Requirement: PR comment shows Jacoco coverage diff
The system MUST post a PR comment (via `github-script` action or `dorny/test-reporter` style action) showing per-file Jacoco line coverage for the PR's changed files. The comment MUST include: per-file line coverage %, delta vs base branch, and a link to the full HTML report artifact. If coverage on the PR drops below 80%, the comment MUST call it out in red.

#### Scenario: PR comment with per-file diff
- **WHEN** a PR is opened or updated
- **THEN** within 2 minutes of CI completion, a PR comment is posted with a table:
  - File | Base coverage | PR coverage | Δ
  - e.g. `OrderService.java | 78% | 82% | +4%`
- **THEN** the comment includes a link to the full `jacoco-coverage` artifact (HTML report) for the PR's run

#### Scenario: Coverage drop flagged in red
- **WHEN** a PR's global line coverage drops below 80%
- **THEN** the PR comment includes a red `❌ Coverage gate failed` banner
- **THEN** the failing check is reported in the PR's "Files changed" tab

#### Scenario: Coverage diff survives force-pushes
- **WHEN** a developer force-pushes to the PR branch
- **THEN** the next CI run re-computes the diff against the new base commit
- **THEN** the PR comment is updated (single comment, not duplicates) with the new numbers

### Requirement: Coverage report is published as CI artifact
The system MUST upload `build/reports/jacoco/test/**` as a GitHub Actions artifact named `jacoco-coverage` on every CI run, with at least 7 days retention. The artifact MUST be downloadable from the job's run page without re-running CI.

#### Scenario: Jacoco HTML report downloadable
- **WHEN** a CI run completes
- **THEN** the `jacoco-coverage` artifact contains the full HTML report
- **THEN** a developer can download and open `index.html` locally to inspect per-file coverage

#### Scenario: Retention is at least 7 days
- **WHEN** the CI workflow defines `actions/upload-artifact@v4` with `retention-days: 7`
- **THEN** artifacts are available for 7 days before automatic deletion
- **THEN** older artifacts are not retained beyond 7 days (storage hygiene)
