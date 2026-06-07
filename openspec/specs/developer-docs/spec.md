# Spec: developer-docs

## Purpose

Defines the rules that govern the writing of developer-facing documentation in this repository (`CLAUDE.md`, `docs/`, `README.md`, OpenSpec `proposal.md` / `design.md` / `tasks.md` / `specs/`). Establishes single-source-of-truth (SOT) discipline so that facts about the system are stated in one place and referenced — not copied — elsewhere, preventing the doc-drift class of bugs that the `sync-docs-with-latest-code` change was created to remediate.

This spec is a meta-capability: its requirements govern the *process* by which other documentation is written and reviewed, not runtime system behavior.

## Requirements

### Requirement: Single source of truth

Every factual claim about the system (workflows, dependencies, file paths, environment variables, command names, port numbers, tool versions, sprint status) SHALL be stated in exactly one authoritative location, and any other location that mentions the same fact SHALL reference the source (by file path and line number, by yaml comment, by spec path, or by stable URL) rather than restate it.

Authoritative locations, in priority order:
1. Executable code, configuration, or `openspec/specs/<capability>/spec.md` (system behavior facts)
2. `.github/workflows/*.yml` and the comments inside them (CI behavior facts)
3. `backend/build.gradle`, `application.yml`, `Dockerfile`, `docker-compose.yml` (build/deploy facts)
4. `openspec/changes/<name>/proposal.md` for a specific in-flight change (transient change facts)
5. `openspec/changes/archive/<name>/*` (historical change facts — read-only)
6. `CLAUDE.md` and `docs/` (only for project-level conventions and entry points; never for system facts)

Locations 1–5 are non-negotiable SOT. Location 6 may *introduce* a project-level fact (e.g., a coding convention, a workflow rule) but once introduced, the fact must be referenced from any other doc that needs it.

#### Scenario: Doc 复述 workflow 数量

- **WHEN** a doc (any file outside `.github/workflows/`) states the number of GitHub Actions workflows in this repository
- **THEN** the doc MUST use the phrasing "see `.github/workflows/`" or list each file by path; the doc MUST NOT restate a workflow count, list, or trigger condition as its own claim

#### Scenario: Doc 复述技术栈版本

- **WHEN** a doc states a dependency version, a tool version, or a runtime version (Java, Spring Boot, MongoDB, Gradle, etc.)
- **THEN** the doc MUST point to the SOT file (e.g., `backend/build.gradle`, `backend/gradle.properties`, `Dockerfile`) rather than write the version inline; an inline version is only allowed if the doc itself is introducing or proposing a change to that version

#### Scenario: doc 类 change 的 proposal 自检

- **WHEN** a developer creates a new OpenSpec change under `openspec/changes/<name>/` whose `proposal.md` `What Changes` section includes edits to `CLAUDE.md`, `docs/`, or `README.md`
- **THEN** the `Impact` section of that proposal MUST contain a sub-section titled "SOT conflict check" that enumerates any new factual claims introduced by the change and states the authoritative source for each; if no new claims are introduced, the sub-section SHALL state "no new factual claims"
- **THEN** `openspec validate --change <name> --strict` MUST flag the proposal as invalid when the "SOT conflict check" sub-section is missing

#### Scenario: 规则本身不自我违反

- **WHEN** any doc references this spec (`developer-docs`)
- **THEN** the doc MUST reference `openspec/specs/developer-docs/spec.md` by path; it MUST NOT quote the requirement text inline beyond the requirement's name

#### Scenario: 内部 doc 间不复制

- **WHEN** two or more files within `CLAUDE.md`, `docs/`, or `README.md` contain the same factual sentence or claim
- **THEN** the developer reviewing the PR SHALL request that one of the duplicates be replaced with a pointer to the other, and the change SHALL NOT be merged with the duplication intact
