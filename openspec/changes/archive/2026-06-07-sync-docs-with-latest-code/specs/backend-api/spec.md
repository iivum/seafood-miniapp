## ADDED Requirements

### Requirement: SCA 扫描隔离性

The OWASP Dependency-Check task (`dependencyCheckAnalyze`) SHALL NOT be wired as a dependency of `./gradlew check`, and the build configuration SHALL NOT execute it from the JVM check pipeline (`.github/workflows/ci.yml`). The task SHALL only be executed by the dedicated security pipeline (`.github/workflows/security.yml`).

The system MUST keep the NVD datafeed cache (`~/.gradle/dependency-check-data`) scoped to the security pipeline so that running the task from `check` would create a second cold-start download path that bypasses the weekly-bucket cache key and the `NVD_API_KEY` injection.

#### Scenario: gradle check 不跑 Dep-Check

- **WHEN** a developer runs `./gradlew check` locally or in `ci.yml`
- **THEN** the build does NOT execute `dependencyCheckAnalyze`, and the build's task graph SHALL NOT include a path from `check` to `dependencyCheckAnalyze`

#### Scenario: Dep-Check 仅在 security.yml 跑

- **WHEN** the CI pipeline triggers a build
- **THEN** only the `security.yml → dependency-check` job executes `dependencyCheckAnalyze`; `ci.yml` MUST NOT have a step or dependency that invokes it

#### Scenario: 回退检测 — check.dependsOn 重新指向 Dep-Check

- **WHEN** a PR modifies `backend/build.gradle` to add `tasks.named('check') { dependsOn 'dependencyCheckAnalyze' }` (or any equivalent wiring)
- **THEN** the PR review SHALL reject the change as a regression of this requirement, regardless of any justifying comment in the build script
