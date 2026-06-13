## ADDED Requirements

### Requirement: Admin cookie-based authentication
The system SHALL provide cookie-based authentication for the admin UI surface via the following endpoints, all signed with `JWT_ADMIN_SECRET` (distinct from the mini-program's `JWT_SECRET`):
- `POST /api/admin/auth/cookie-login` — accept `{ phone, password }`, validate, and on success set an `httpOnly`, `Secure`, `SameSite=Lax` cookie containing a signed JWT (15-minute TTL)
- `POST /api/admin/auth/logout` — clear the cookie and revoke the token's `jti` in `revoked_tokens`
- `GET /api/admin/auth/csrf` — return a CSRF token bound to the current session cookie (for use in non-GET requests)

The cookie MUST be set with `Path=/`, `Domain` matching the admin host, and `Max-Age=900` (15 minutes). The system MUST NOT accept `Authorization: Bearer ...` for admin endpoints — only the cookie.

#### Scenario: Successful cookie login
- **WHEN** an operator submits valid credentials to `POST /api/admin/auth/cookie-login`
- **THEN** the response sets the `httpOnly` admin cookie
- **AND** the response body is empty (or contains only `{ csrfToken: "..." }`)
- **AND** the response status is 204 No Content

#### Scenario: Logout clears the cookie
- **WHEN** an authenticated operator calls `POST /api/admin/auth/logout`
- **THEN** the response sets the admin cookie with `Max-Age=0` and a past `Expires`
- **AND** the JWT's `jti` is added to `revoked_tokens`
- **AND** the response status is 204

#### Scenario: Subsequent admin request uses the cookie
- **WHEN** the browser makes a request to `GET /api/admin/dashboard`
- **THEN** the browser includes the admin cookie automatically
- **AND** the JWT filter populates the `SecurityContext` with role `INTERNAL_OPERATOR`

#### Scenario: CSRF token required for non-GET
- **WHEN** the admin UI issues a `POST` to `/api/admin/orders/batch-ship`
- **THEN** the request MUST include an `X-CSRF-Token` header whose value matches the CSRF token returned by `/api/admin/auth/csrf`
- **AND** the server rejects the request with HTTP 403 `code: "CSRF_TOKEN_MISMATCH"` if the header is missing or mismatched

---

### Requirement: Admin login lockout (3 failures / 15 minutes / IP-scoped)
The `POST /api/admin/auth/cookie-login` endpoint SHALL enforce IP-scoped rate limiting and account lockout. After 3 consecutive failed attempts from the same IP within 15 minutes, the system MUST lock that IP for 15 minutes, returning HTTP 429 with `code: "AUTH_LOCKED"` and `Retry-After` header. The system MUST increment the `users.login.attempts{result=locked}` Micrometer counter on lockout. After 3 consecutive failed attempts against a specific account, the system MUST also lock that account for 15 minutes.

#### Scenario: 3 failures lock the IP
- **WHEN** 3 consecutive failed login attempts originate from IP `1.2.3.4` within 15 minutes
- **AND** a 4th attempt originates from the same IP (regardless of credentials)
- **THEN** the system returns HTTP 429 with `code: "AUTH_LOCKED"` and `Retry-After: 900`
- **AND** increments `users.login.attempts{result=locked}` by 1

#### Scenario: Successful login clears the counter
- **WHEN** the IP `1.2.3.4` has 2 failed attempts
- **AND** then submits valid credentials
- **THEN** the system issues the admin cookie
- **AND** the failure counter is reset to 0
- **AND** the next failure counts as failure #1

#### Scenario: Account lockout (3 failures on same account)
- **WHEN** 3 consecutive failed login attempts target the same `phone` (regardless of IP) within 15 minutes
- **THEN** the account is locked for 15 minutes
- **AND** even correct credentials return HTTP 423 with `code: "ACCOUNT_LOCKED"`

---

### Requirement: Admin role and access matrix
The admin endpoints SHALL enforce the following access matrix:

| Endpoint | Role | Notes |
|---|---|---|
| `POST /api/admin/auth/cookie-login` | public | independent `JWT_ADMIN_SECRET` |
| `POST /api/admin/auth/logout` | any authenticated | clears the cookie |
| `GET /api/admin/auth/csrf` | any authenticated | returns CSRF token |
| `GET /api/admin/dashboard` | INTERNAL_OPERATOR or INTERNAL_CS | |
| `GET /api/admin/orders/{id}/detail` | INTERNAL_OPERATOR or INTERNAL_CS | |
| `POST /api/admin/orders/batch-ship` | INTERNAL_OPERATOR | write path |
| `POST /api/admin/orders/{id}/print-picklist` | INTERNAL_OPERATOR | write path |
| `GET /api/admin/orders/export` | INTERNAL_OPERATOR | write path |
| `POST /api/admin/orders/{id}/refund/approve` | INTERNAL_OPERATOR | write path |
| `POST /api/admin/orders/{id}/refund/reject` | INTERNAL_OPERATOR | write path |
| `GET /api/admin/products/stats` | INTERNAL_OPERATOR or INTERNAL_CS | read path |
| `POST /api/admin/products/{id}/duplicate` | INTERNAL_OPERATOR | write path |
| `POST /api/admin/products/export` | INTERNAL_OPERATOR | write path |
| `POST /api/admin/uploads` | INTERNAL_OPERATOR | write path |

The system MUST reject any request presenting a JWT with a `MERCHANT` role at any `/api/admin/**` endpoint with HTTP 401 `code: "AUTH_INVALID_ROLE"`.

#### Scenario: INTERNAL_CS reads dashboard
- **WHEN** a JWT with `role: "INTERNAL_CS"` is presented at `GET /api/admin/dashboard`
- **THEN** the system returns HTTP 200

#### Scenario: INTERNAL_CS attempts write
- **WHEN** a JWT with `role: "INTERNAL_CS"` is presented at `POST /api/admin/orders/batch-ship`
- **THEN** the system returns HTTP 403 with `code: "FORBIDDEN"`

#### Scenario: MERCHANT role rejected
- **WHEN** a JWT with `role: "MERCHANT"` is presented at any `/api/admin/**` endpoint
- **THEN** the system returns HTTP 401 with `code: "AUTH_INVALID_ROLE"`
- **AND** increments `users.login.attempts{result=invalid_role}` by 1
