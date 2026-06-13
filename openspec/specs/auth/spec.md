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
