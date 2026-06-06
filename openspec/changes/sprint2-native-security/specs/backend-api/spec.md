## ADDED Requirements

### Requirement: Backend HTTP responses include baseline security headers

Every HTTP response served by the backend — JSON API, BFF, static admin assets, and `/actuator/**` — SHALL include the baseline security header set defined in capability `runtime-security`. Existing endpoint behavior (status codes, body shape, `code` values) SHALL remain unchanged.

#### Scenario: JSON API response carries security headers

- **WHEN** any existing endpoint under `/api/**` (e.g. `GET /api/products`) returns a response
- **THEN** the response carries `Strict-Transport-Security`, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, and `Content-Security-Policy` headers per `runtime-security`

#### Scenario: Static admin response carries security headers

- **WHEN** a browser fetches a static admin asset under `/admin/**`
- **THEN** the response carries the same security header set

#### Scenario: Error response carries security headers

- **WHEN** the global exception handler returns an `ErrorResponse` (4xx or 5xx)
- **THEN** the response carries the same security header set

### Requirement: Admin BFF endpoints enforce a request rate limit

Every endpoint under `/api/admin/**` (including BFF aggregation, admin product CRUD, and admin auth) SHALL enforce the 60 rpm **fixed-window** limit defined in capability `runtime-security`. Excess requests SHALL receive HTTP 429 with an `ErrorResponse` whose `code` is `RATE_LIMITED`. The non-admin endpoints under `/api/**` SHALL NOT be rate-limited by this rule. PR review #27: prior wording "token-bucket" was inaccurate.

#### Scenario: BFF dashboard rate-limited

- **WHEN** an ADMIN client issues a 61st request to `GET /api/admin/dashboard` within 60 seconds
- **THEN** the response is HTTP 429 with `code=RATE_LIMITED` and the `Retry-After` header set

#### Scenario: Admin product CRUD rate-limited

- **WHEN** an ADMIN client issues a 61st request to `POST /api/admin/products` (or any admin write path) within 60 seconds
- **THEN** the response is HTTP 429 with `code=RATE_LIMITED`

#### Scenario: Non-admin path not rate-limited

- **WHEN** a CUSTOMER client issues 100 requests to `GET /api/products` within 60 seconds
- **THEN** every response is 200 (no `RATE_LIMITED` body), because `/api/products` is outside `/api/admin/**`
