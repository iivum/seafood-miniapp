# Spec: auth

## Purpose

[TBD — see change `refactor-rust-rebuild-frontend` for context. Defines the JWT-based authentication, RBAC access matrix, refresh-token rotation, and GraalVM Native Image safety constraints for both the mini-program and admin-ui surfaces.]

## Requirements

### Requirement: JWT issuance and structure
The system SHALL issue HS256-signed access tokens (15-minute TTL) and refresh tokens (7-day TTL). Access tokens SHALL carry `sub` (userId), `role`, `iat`, `exp`, and `jti`. Refresh tokens SHALL carry `sub`, `type=refresh`, `iat`, `exp`, and `jti`. The signing secret SHALL be read from the `JWT_SECRET` environment variable, and the system SHALL fail-fast at startup if the secret is missing.

#### Scenario: Login issues a token pair
- **WHEN** a user successfully authenticates at `POST /api/auth/login` (or `POST /api/admin/auth/login` for admin)
- **THEN** the response body contains both `accessToken` and `refreshToken`; the access-token claims include `role` and `sub`

#### Scenario: Startup with missing JWT secret
- **WHEN** the application starts without `JWT_SECRET` set
- **THEN** the process exits with a clear error before accepting any traffic

#### Scenario: Access token expiry
- **WHEN** 15 minutes have elapsed since a token was issued
- **THEN** any API call authenticated by that token returns HTTP 401 with `code=TOKEN_EXPIRED`

### Requirement: Filter chain ordering
The system SHALL run authentication before authorization. The JWT filter SHALL parse the `Authorization: Bearer <token>` header, skip the configured public endpoints, and populate the `SecurityContext`. The authorization layer SHALL then evaluate `@PreAuthorize` expressions on the handler method.

#### Scenario: Public endpoint bypasses JWT
- **WHEN** a request hits a public endpoint (e.g. `GET /api/products`)
- **THEN** the JWT filter does not require a token and does not populate the SecurityContext

#### Scenario: Protected endpoint without token
- **WHEN** a request hits a protected endpoint without an `Authorization` header
- **THEN** the system returns HTTP 401 before the handler is invoked

#### Scenario: Protected endpoint with valid token
- **WHEN** a request hits a protected endpoint with a valid bearer token
- **THEN** the SecurityContext contains a `UserPrincipal` whose `id` and `role` match the token claims

### Requirement: Endpoint access matrix
The system SHALL enforce the following access matrix at the authorization layer:

| Prefix | Role | Notes |
|---|---|---|
| `POST /api/auth/login`, `POST /api/auth/refresh` | public | |
| `POST /api/admin/auth/login` | public | independent secret |
| `GET  /api/products/**` | public | |
| `POST/PUT/DELETE /api/products/**` | ADMIN | |
| `GET/POST/PUT/DELETE /api/cart/**` | CUSTOMER (own) | ADMIN may act on any cart |
| `GET/POST /api/orders/**` | CUSTOMER (own) / ADMIN (all) | |
| `GET /api/admin/**` | ADMIN | |

#### Scenario: CUSTOMER reads any user's orders
- **WHEN** a CUSTOMER calls `GET /api/orders?userId=<otherUserId>` or similar
- **THEN** the system returns only the caller's own orders

#### Scenario: ADMIN reads all orders
- **WHEN** an ADMIN calls `GET /api/orders`
- **THEN** the system returns orders for all users

#### Scenario: Anonymous reads public product list
- **WHEN** an unauthenticated client calls `GET /api/products`
- **THEN** the system returns the public product list with status 200

### Requirement: Token refresh
The system SHALL accept a valid refresh token at `POST /api/auth/refresh` (or `POST /api/admin/auth/refresh` for admin) and SHALL issue a new access token. Refresh tokens SHALL be single-use: after a successful refresh the prior refresh `jti` SHALL be rejected on subsequent uses.

#### Scenario: Successful refresh
- **WHEN** a client calls `POST /api/auth/refresh` with a valid refresh token
- **THEN** the system returns a new access token and a new refresh token with status 200

#### Scenario: Reuse of consumed refresh token
- **WHEN** a client calls `POST /api/auth/refresh` with a refresh token whose `jti` has already been exchanged
- **THEN** the system returns HTTP 401 with `code=TOKEN_REUSED` and revokes the entire token family for that user

#### Scenario: Expired refresh token
- **WHEN** a client calls `POST /api/auth/refresh` with a refresh token past its 7-day expiry
- **THEN** the system returns HTTP 401 with `code=TOKEN_EXPIRED`

### Requirement: Native Image safety
The system SHALL compile successfully into a GraalVM Native binary, and the codebase SHALL NOT contain any usage of `@RefreshScope` (incompatible with Native Image).

#### Scenario: Build pipeline scans for `@RefreshScope`
- **WHEN** the CI pipeline runs the code-review static check
- **THEN** any Java file that imports or annotates `@RefreshScope` fails the check with a clear error message

#### Scenario: Native binary boots
- **WHEN** the GraalVM Native binary is started
- **THEN** it boots in under 2 seconds and serves a 200 response on `GET /actuator/health` (or equivalent readiness probe)

### Requirement: Access tokens can be revoked server-side
The system SHALL maintain a server-side revocation list keyed by access token `jti`. A revoked `jti` SHALL be rejected by the JWT authentication filter with HTTP 401 and `code=TOKEN_REVOKED` even when the signature and `exp` are still valid. The revocation store SHALL be a MongoDB collection `revoked_tokens` with a TTL index on `expiresAt` so entries self-delete after the original token expiry.

#### Scenario: Logout revokes the current token
- **WHEN** an authenticated client calls `POST /api/auth/logout` with a valid access token
- **THEN** the system writes `{ jti, userId, expiresAt }` to `revoked_tokens` and returns HTTP 204; any subsequent request reusing that access token returns HTTP 401 with `code=TOKEN_REVOKED`

#### Scenario: Admin force-logout user
- **WHEN** an ADMIN calls `POST /api/admin/users/{id}/revoke-tokens`
- **THEN** the system writes a revocation marker for every outstanding `jti` belonging to that user; the user's next API call returns HTTP 401 with `code=TOKEN_REVOKED`

#### Scenario: TTL index removes expired entries
- **WHEN** a revoked `jti` has passed its original `expiresAt`
- **THEN** MongoDB's TTL monitor removes the document within 60 seconds, and the collection size stays bounded

### Requirement: Repeated login failures lock the account
The login endpoints (`POST /api/auth/login` and `POST /api/admin/auth/login`) SHALL track failed attempts per account. After 5 consecutive failures within 15 minutes the account SHALL be locked for 15 minutes; further login attempts on a locked account SHALL return HTTP 423 with `code=ACCOUNT_LOCKED`, even if the supplied credentials would otherwise succeed. A successful login SHALL reset the counter.

#### Scenario: Five wrong passwords lock the account
- **WHEN** a client submits 5 wrong passwords for the same account within 15 minutes
- **THEN** the 6th attempt returns HTTP 423 with `code=ACCOUNT_LOCKED` and a `retryAfterSeconds` field, regardless of whether the credentials are correct

#### Scenario: Successful login clears the counter
- **WHEN** a client submits a wrong password 3 times then logs in successfully
- **THEN** the failure counter is reset to 0 and a subsequent wrong-password attempt counts as failure #1, not #4

#### Scenario: Lock expires after window
- **WHEN** a locked account waits 15 minutes
- **THEN** the next login attempt with correct credentials succeeds and returns the normal token pair

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

### Requirement: Admin login lockout — exact window and HTTP shape
The `POST /api/admin/auth/cookie-login` endpoint MUST enforce a strict lockout policy:
- **IP lockout**: 3 consecutive failed attempts from the same `X-Forwarded-For` IP (falling back to `request.getRemoteAddr()` when the header is absent) within a 15-minute rolling window locks that IP for exactly 15 minutes. During the lockout window, ALL login attempts from that IP return HTTP 429 with:
  - Body: `{ code: "AUTH_LOCKED", message: "...", retryAfterSeconds: 900 }`
  - Header: `Retry-After: 900`
  - Increment: `users.login.attempts{result=locked}` by 1
- **Account lockout**: 3 consecutive failed attempts against the same `phone` (regardless of IP) within 15 minutes locks that account for 15 minutes. During the lockout window, login attempts on the locked account (even with correct credentials) return HTTP 423 with:
  - Body: `{ code: "ACCOUNT_LOCKED", message: "...", retryAfterSeconds: 900 }`
  - Increment: `users.login.attempts{result=locked}` by 1
- **Counter reset**: A successful login from a previously-locked-but-now-unlocked IP/account MUST reset the consecutive-failure counter to 0.
- **Storage**: Failure attempts are persisted in a `login_attempts` MongoDB collection with `{ ip, account, success, ts }` and a TTL index on `ts` of 900 seconds. The IP and account lockout windows are computed by querying the collection for the latest N records for the same IP or account.
- **Unlock endpoint (stub)**: The system SHALL expose `GET /api/auth/login-lock?phone={phone}&ip={ip}` returning `{ locked: boolean, until: ISO8601|null, scope: "IP"|"ACCOUNT"|"NONE" }`. The endpoint is read-only in Sprint 1 closure (no manual unlock); manual unlock is a Sprint 4 follow-up.

#### Scenario: IP lockout after 3 failures
- **WHEN** IP `1.2.3.4` makes 3 consecutive failed login attempts (any accounts) within 15 minutes
- **AND** a 4th attempt originates from the same IP
- **THEN** the response is HTTP 429 with `code: "AUTH_LOCKED"`, `Retry-After: 900`, and the body includes `retryAfterSeconds: 900`
- **AND** `users.login.attempts{result=locked}` is incremented by 1
- **AND** the failure counter for that IP is NOT reset (the lockout is in effect)

#### Scenario: Account lockout takes precedence over correct credentials
- **WHEN** account `13800138000` has 3 failed attempts within 15 minutes
- **AND** a 4th attempt submits the correct password
- **THEN** the response is HTTP 423 with `code: "ACCOUNT_LOCKED"`
- **AND** the correct credentials do NOT bypass the lockout

#### Scenario: Lockout expires exactly after 15 minutes
- **WHEN** an IP is locked at time T
- **THEN** the lockout expires at T + 15 minutes (within 1 minute precision)
- **AND** a login attempt after T + 15 minutes with correct credentials succeeds

#### Scenario: Successful login clears the counter
- **WHEN** IP `1.2.3.4` has 2 failed attempts and then submits valid credentials
- **THEN** the response is 204 (cookie set)
- **AND** the IP's failure counter is reset to 0
- **AND** the next failure counts as failure #1

#### Scenario: Lock status query
- **WHEN** a client calls `GET /api/auth/login-lock?phone=13800138000&ip=1.2.3.4`
- **THEN** the response is HTTP 200 with `{ locked: false, until: null, scope: "NONE" }` (when neither is locked)
- **OR** `{ locked: true, until: "2026-06-15T16:30:00Z", scope: "IP" }` (when IP is locked)
- **OR** `{ locked: true, until: "2026-06-15T16:30:00Z", scope: "ACCOUNT" }` (when account is locked)

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
