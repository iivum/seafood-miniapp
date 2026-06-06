## ADDED Requirements

### Requirement: HTTP responses carry baseline security headers

Every HTTP response served by the backend (including static admin assets and JSON API) SHALL carry the following headers with the listed minimum values. Values MAY be tightened per route but MUST NOT be weaker:

| Header | Minimum value |
|---|---|
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` |
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `Permissions-Policy` | `geolocation=(), microphone=(), camera=()` |
| `Content-Security-Policy` | `default-src 'self'; img-src 'self' data: https:; style-src 'self' 'unsafe-inline'; script-src 'self'` |

Headers SHALL be injected by a single `SecurityHeadersFilter` registered in the filter chain, NOT scattered across controllers.

#### Scenario: JSON API response carries all headers

- **WHEN** a client calls `GET /api/products`
- **THEN** the response carries every header in the table with at least the listed value

#### Scenario: Static admin asset carries all headers

- **WHEN** a browser fetches `GET /admin/index.html`
- **THEN** the response carries every header in the table with at least the listed value

#### Scenario: Architecture test enforces single filter

- **WHEN** the ArchUnit suite runs
- **THEN** any new class that writes one of the listed headers from outside `com.seafood.shared.security.SecurityHeadersFilter` fails the test

### Requirement: Admin endpoints enforce a rate limit

The system SHALL apply a **fixed-window** rate limit of 60 requests per minute per `(client IP, account)` tuple to every endpoint under `/api/admin/**`. Excess requests SHALL receive HTTP 429 with `Retry-After` and a `code=RATE_LIMITED` `ErrorResponse` body. The limiter SHALL keep counters in a Caffeine in-memory cache (no external store). PR review #27: prior wording "token-bucket" was inaccurate — the implementation is a 60-second sliding window with per-tuple counter, not a token bucket.

#### Scenario: Within budget

- **WHEN** an admin client issues 30 requests in 60 seconds against `/api/admin/dashboard`
- **THEN** every response is 200

#### Scenario: Exceeds budget

- **WHEN** an admin client issues a 61st request within 60 seconds against any `/api/admin/**` endpoint
- **THEN** the response is HTTP 429 with `Retry-After` set to the seconds until the bucket refills and `code=RATE_LIMITED` in the body

#### Scenario: Separate accounts have separate buckets

- **WHEN** two distinct admin accounts each issue 60 requests in 60 seconds
- **THEN** all 120 requests succeed because the buckets are keyed by account
