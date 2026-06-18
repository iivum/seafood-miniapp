## ADDED Requirements

### Requirement: k6 script covers 5 core endpoints
The system MUST have a k6 script (`backend/scripts/k6-baseline.js`) that exercises the 5 core endpoints identified in `test-suite-roadmap/design.md` §2.1 子项目 ③ C3:

- `GET /api/products` — public product list (no auth)
- `GET /api/orders` — authenticated order list (CUSTOMER role)
- `POST /api/orders` — authenticated order creation (CUSTOMER role)
- `POST /api/admin/auth/login` — admin login
- `GET /api/admin/orders` — admin order list (ADMIN role)

The script MUST report P50, P95, and P99 latency for each endpoint. The script MUST use the k6 `http` module and define thresholds matching the CLAUDE.md performance budget (`http_req_duration: ['p(99)<500']`).

#### Scenario: k6 script runs against a live backend
- **WHEN** a developer runs `k6 run backend/scripts/k6-baseline.js` against a running backend (e.g. localhost:8080)
- **THEN** the script makes HTTP requests to all 5 endpoints
- **THEN** the script reports latency percentiles (P50, P95, P99) for each endpoint
- **THEN** the script exits with code 0 if all P99 < 500ms, non-zero otherwise

#### Scenario: k6 script authenticates correctly
- **WHEN** the script tests admin endpoints (`/api/admin/orders`)
- **THEN** it first calls `POST /api/admin/auth/login` to obtain an admin token
- **THEN** it includes the token in subsequent admin request headers
- **AND** similarly for CUSTOMER endpoints (uses test customer credentials)

### Requirement: k6 baseline results are recorded
The system MUST record the first k6 run results to a JSON file (`backend/scripts/k6-results.json`) for trend comparison. The JSON MUST include: timestamp, per-endpoint P50/P95/P99, total requests, error rate.

#### Scenario: k6 results JSON published
- **WHEN** `k6 run` completes
- **THEN** a CI step extracts the per-endpoint stats from k6 stdout
- **THEN** the step writes `backend/scripts/k6-results.json` with the schema:
  ```json
  {
    "timestamp": "2026-06-19T00:00:00Z",
    "endpoints": {
      "GET /api/products": { "p50_ms": 12, "p95_ms": 45, "p99_ms": 89 },
      ...
    },
    "total_requests": 1000,
    "error_rate": 0.001
  }
  ```
- **THEN** the JSON is committed to the repo as a baseline (subsequent runs compare)

#### Scenario: Baseline comparison detects regressions
- **WHEN** a developer runs `k6-baseline` script
- **THEN** the script compares current run to baseline `k6-results.json`
- **THEN** if any endpoint's P99 increases by > 50% from baseline, the script exits with non-zero
- **THEN** the output prints a diff table showing current vs baseline

### Requirement: k6 runs as nightly CI job (not PR gate)
The system MUST run k6 as a nightly scheduled GitHub Actions job (cron `0 2 * * *`) on the main branch. The job MUST NOT run on pull requests (P99 baseline is a trend metric, not a PR gate).

#### Scenario: Nightly k6 job runs at 2am UTC
- **WHEN** the cron schedule triggers (`0 2 * * *`)
- **THEN** GitHub Actions starts the `k6-baseline-nightly` workflow
- **THEN** the workflow checks out main, builds the backend (if needed), starts the backend service, runs k6, captures results

#### Scenario: k6 results PR opened on regression
- **WHEN** k6 nightly run detects P99 regression > 50% from baseline
- **THEN** the workflow opens a PR with the updated `k6-results.json` (or a comment on the existing baseline file)
- **THEN** the PR body describes which endpoint regressed and the magnitude
- **THEN** the PR is labeled `performance-regression`

#### Scenario: k6 does NOT block PRs
- **WHEN** a PR is opened
- **THEN** no k6 step runs in the PR CI
- **THEN** the PR can merge without waiting for k6 (nightly only)

### Requirement: k6 baseline written to `backend/scripts/k6-results.json`
The system MUST commit a `k6-results.json` file in the first run of this change, even if the numbers are placeholder. This file is the contract for future runs to compare against.

#### Scenario: Initial baseline committed
- **WHEN** this change lands
- **THEN** `backend/scripts/k6-results.json` exists in the repo
- **THEN** it contains the first-run numbers or placeholder structure (`endpoints: {}`)
- **THEN** the README documents how to run k6 locally and CI

#### Scenario: Subsequent runs update the baseline
- **WHEN** a developer intentionally raises the budget (e.g. changes in P99 expected)
- **THEN** they manually update `k6-results.json` and commit
- **THEN** subsequent runs use the new numbers as the comparison baseline
