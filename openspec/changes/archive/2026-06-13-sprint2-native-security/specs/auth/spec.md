## ADDED Requirements

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
