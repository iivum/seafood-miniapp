## ADDED Requirements

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
