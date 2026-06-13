# Spec: supply-chain-security

## Purpose

[TBD — see change `sprint2-native-security` for context. Defines the supply-chain scanning and dependency-update posture: OWASP Dependency-Check, Trivy container scanning, TruffleHog secret scanning, and Dependabot for proactive updates.]

## Requirements

### Requirement: Dependency vulnerability scan blocks high-severity CVEs
The build SHALL include OWASP Dependency-Check via the `org.owasp.dependencycheck` Gradle plugin. The check SHALL run as part of `./gradlew check` and SHALL fail the build when any dependency reports a CVSS v3 score of 7.0 or higher. The NVD data feed SHALL be cached between CI runs to keep the scan under 3 minutes on warm runs.

#### Scenario: High-severity CVE fails the build
- **WHEN** CI runs `./gradlew dependencyCheckAnalyze` and any dependency has a vulnerability with CVSS ≥ 7.0
- **THEN** the task exits non-zero, the report path is printed, and `./gradlew check` is marked failed

#### Scenario: Suppression file controls false positives
- **WHEN** a documented false positive is added to `backend/dependency-check-suppressions.xml`
- **THEN** `dependencyCheckAnalyze` ignores that CVE on the matching dependency

### Requirement: Container image scan blocks high/critical vulnerabilities
The CI pipeline SHALL run Trivy against the published `seafood-backend:native` image on every push to `main` and every PR targeting `main`. The scan SHALL fail the job on `HIGH` or `CRITICAL` severity findings in OS packages or language libraries, and SHALL output the SARIF result file for the GitHub Security tab.

#### Scenario: Trivy detects a critical OS CVE
- **WHEN** Trivy scans the built image and a base-layer package has a `CRITICAL` advisory
- **THEN** the workflow step exits non-zero and the SARIF result is uploaded to GitHub Code Scanning

#### Scenario: Scan produces SARIF for code scanning
- **WHEN** the Trivy job completes (pass or fail)
- **THEN** a `trivy-results.sarif` artifact is uploaded via `github/codeql-action/upload-sarif`

### Requirement: Secret scanning prevents credential leaks
The repository SHALL have GitHub secret scanning and push protection enabled. The CI pipeline SHALL ALSO run `trufflehog` (or equivalent) on PR diffs as a second line of defense to catch verified credentials before merge.

#### Scenario: Push protection blocks a real secret
- **WHEN** a developer attempts to push a commit containing a real AWS access key, GitHub PAT, or JWT signing key
- **THEN** the push is rejected by GitHub before reaching the remote, and the developer is told which credential type was detected

#### Scenario: PR diff scan flags accidental commit
- **WHEN** a PR introduces a string matching a verifiable secret pattern
- **THEN** the `trufflehog` CI step exits non-zero and posts the matched file path and line

### Requirement: Dependency updates are proposed automatically
A `.github/dependabot.yml` SHALL be present and SHALL configure weekly update PRs for the Gradle (`backend/`) and Docker (`docker-compose.yml`, `backend/Dockerfile`) ecosystems. Security updates SHALL be raised immediately regardless of the weekly cadence.

#### Scenario: Weekly Gradle update PR
- **WHEN** a non-security Gradle dependency has a newer minor release
- **THEN** Dependabot opens a PR within 7 days of that release with the bumped version

#### Scenario: Immediate security PR
- **WHEN** an existing Gradle dependency receives a security advisory
- **THEN** Dependabot opens a PR within 24 hours regardless of the weekly schedule
