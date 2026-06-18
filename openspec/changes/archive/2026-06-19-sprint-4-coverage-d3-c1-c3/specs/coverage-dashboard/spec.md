## ADDED Requirements

### Requirement: PR comment shows Jacoco coverage diff
The system MUST post a PR comment (via Codecov GitHub Action or `github-script`) showing per-file Jacoco line coverage for the PR's changed files. The comment MUST include: per-file line coverage %, delta vs base branch, and a link to the full HTML report artifact. If coverage on the PR drops below 80%, the comment MUST call it out in red.

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

### Requirement: Codecov integration OR self-hosted alternative
The system MUST have a coverage reporting integration that works without external services in dev. The implementation MAY use Codecov (preferred) or a self-hosted GitHub Pages dashboard reading `build/reports/jacoco/test/html/`.

#### Scenario: Codecov token configured
- **WHEN** the repository admin adds `CODECOV_TOKEN` as a GitHub Actions secret
- **THEN** the Codecov GitHub Action uploads coverage on every CI run
- **THEN** Codecov posts PR comments automatically
- **THEN** the Codecov badge in `README.md` displays the latest coverage %

#### Scenario: Self-hosted fallback works without external service
- **WHEN** `CODECOV_TOKEN` is not configured
- **THEN** a self-hosted `gh-pages` workflow publishes `build/reports/jacoco/test/html/` as a static site
- **THEN** the README badge links to the GitHub Pages URL
- **THEN** PR comments still include the per-file diff (via `github-script` step reading Jacoco XML directly)

### Requirement: README.md shows coverage badge
The system MUST have a coverage badge in `README.md` linking to the coverage report (Codecov URL or GitHub Pages URL). The badge MUST show the latest global line coverage percentage.

#### Scenario: Badge displays current coverage
- **WHEN** a developer opens `README.md`
- **THEN** a coverage badge is visible near the project title
- **THEN** the badge shows a percentage (e.g. `Coverage 80%`)
- **THEN** clicking the badge navigates to the detailed coverage report
