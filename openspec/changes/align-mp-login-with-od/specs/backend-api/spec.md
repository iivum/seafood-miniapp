## ADDED Requirements

### Requirement: Customer phone number binding
The system SHALL expose an authenticated endpoint that lets a logged-in CUSTOMER bind or update their phone number by exchanging a WeChat `getPhoneNumber` authorization code, following the same dev/prod dual-mode convention as `POST /api/auth/wechat-login` (`wechat.enabled=false` accepts a `dev-`-prefixed code for local/E2E use; `wechat.enabled=true` exchanges the code against WeChat's official `phonenumber.getPhoneNumber` API).

#### Scenario: Authenticated customer binds phone number in dev mode
- **WHEN** an authenticated CUSTOMER calls the phone-binding endpoint with a `dev-`-prefixed code while `wechat.enabled=false`
- **THEN** the system binds a deterministic test phone number derived from the code to the user's account and returns 200 with the updated user profile

#### Scenario: Authenticated customer binds phone number in production mode
- **WHEN** an authenticated CUSTOMER calls the phone-binding endpoint with a valid WeChat authorization code while `wechat.enabled=true`
- **THEN** the system exchanges the code for the real phone number via WeChat's `phonenumber.getPhoneNumber` API, binds it to the user's account, and returns 200 with the updated user profile

#### Scenario: Unauthenticated client attempts to bind a phone number
- **WHEN** a client without a valid access token calls the phone-binding endpoint
- **THEN** the system returns HTTP 401

#### Scenario: Non-dev code rejected while WeChat integration is disabled
- **WHEN** an authenticated CUSTOMER calls the phone-binding endpoint with a code that does not start with `dev-` while `wechat.enabled=false`
- **THEN** the system returns an error response with `code=DOMAIN` and does not modify the user's phone number

#### Scenario: WeChat code exchange fails in production mode
- **WHEN** an authenticated CUSTOMER calls the phone-binding endpoint with an invalid or expired code while `wechat.enabled=true`
- **THEN** the system returns an error response with `code=DOMAIN` and does not modify the user's phone number
