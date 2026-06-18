## ADDED Requirements

### Requirement: PIT mutation testing runs on domain and application layers
The system MUST add the PIT (Program Integrated Testing) mutation testing framework to the backend Gradle build, configured to run on the `com.seafood.*.domain.*` and `com.seafood.*.application.*` packages. The plugin MUST be `org.pitest:pitest-gradle-plugin` version 1.15.0 or newer (compatible with JDK 25).

#### Scenario: PIT runs against the configured packages
- **WHEN** a developer runs `cd backend && ./gradlew pitest -x processTestAot`
- **THEN** PIT runs against `com.seafood.order.domain`, `com.seafood.order.application`, `com.seafood.product.domain`, `com.seafood.product.application`, `com.seafood.user.domain`, `com.seafood.user.application`, `com.seafood.bff.admin` packages
- **THEN** PIT skips `controller / repository / config` packages (per CLAUDE.md coverage rules — only domain + application count)
- **THEN** the build produces `build/reports/pitest/index.html` with a mutation score report

#### Scenario: PIT completes within reasonable time
- **WHEN** `pitest` task runs on a single developer machine
- **THEN** it completes within 10 minutes (full domain + application mutation set)
- **THEN** a CI run with the same scope completes within 15 minutes

#### Scenario: PIT baseline report is published
- **WHEN** `pitest` task runs
- **THEN** the HTML report at `build/reports/pitest/index.html` is uploaded as a CI artifact (retention 30 days — longer than Jacoco's 7 days since PIT runs less frequently)
- **THEN** the report shows mutation score per package
- **THEN** the report shows surviving mutants (tests that did NOT kill the mutation)

### Requirement: Mutation score threshold enforced at build time
The system MUST configure PIT to fail the build if the mutation score on the configured packages drops below 70% (initial baseline threshold; future iterations may raise it).

#### Scenario: Mutation score below 70% fails the build
- **WHEN** a developer adds a new method to `OrderService` without writing a test for it
- **AND** PIT's mutator generates a surviving mutant for the new method
- **THEN** `./gradlew pitest` reports mutation score < 70%
- **THEN** the build fails with: `Survival rate above threshold (30.0%)`
- **THEN** the failing report points to the surviving mutants and their locations

#### Scenario: Mutation score at or above 70% passes
- **WHEN** PIT reports a mutation score of 70% or higher
- **THEN** `./gradlew pitest` exits with status 0
- **THEN** no further action required for the gate

#### Scenario: PIT score is tracked over time
- **WHEN** the developer reads the PIT history (e.g. by viewing past CI artifacts)
- **THEN** the mutation score is recorded per build
- **THEN** trends are visible (improving, stable, or regressing)

### Requirement: PIT output is published as CI artifact
The system MUST upload the PIT HTML report as a GitHub Actions artifact on every CI run, with at least 30 days retention. The artifact MUST be downloadable from the job's run page.

#### Scenario: PIT HTML report downloadable
- **WHEN** a CI run completes
- **THEN** the `pitest-report` artifact contains the full HTML report
- **THEN** a developer can download and open `index.html` locally to inspect surviving mutants

#### Scenario: Retention is at least 30 days
- **WHEN** the CI workflow defines `actions/upload-artifact@v4` with `retention-days: 30`
- **THEN** PIT artifacts are available for 30 days before automatic deletion
- **THEN** older artifacts are not retained beyond 30 days (storage hygiene)
