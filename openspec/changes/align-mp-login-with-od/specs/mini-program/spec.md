## MODIFIED Requirements

### Requirement: Authentication and session
The mini-program SHALL authenticate via WeChat login: it SHALL call `wx.login` to obtain a `code`, exchange it for an `openId` at `POST /api/auth/wechat-login`, and store the returned access/refresh tokens in a way that survives page reloads but is cleared on logout. Before invoking WeChat login, the user SHALL explicitly consent to the User Agreement and Privacy Policy. After a successful WeChat login, the app SHALL offer an optional, skippable phone-number-binding step. A developer-only login entry SHALL remain available as a visually de-emphasized secondary affordance for local/E2E use, without changing its underlying dev-login behavior.

#### Scenario: First-launch login
- **WHEN** the app starts and no valid token is present
- **THEN** the app calls `wx.login`, exchanges the `code` via `POST /api/auth/wechat-login`, persists the tokens, and proceeds to the home page

#### Scenario: Token refresh
- **WHEN** any API call returns 401 and the response indicates the access token is expired
- **THEN** the app calls `POST /api/auth/refresh` once, retries the original request, and only re-runs the WeChat login flow if the refresh also fails

#### Scenario: Logout
- **WHEN** the user taps the logout entry in the profile page
- **THEN** the app clears stored tokens and returns to the home page in an anonymous state

#### Scenario: Login blocked without consent
- **WHEN** the user taps "微信一键登录" without checking the User Agreement / Privacy Policy consent box
- **THEN** the app does not call `wx.login`, shows a shake animation on the consent row, and displays a toast prompting the user to agree to the agreements first

#### Scenario: Phone-number-binding step after WeChat login
- **WHEN** WeChat login succeeds
- **THEN** the app shows the user's avatar/nickname with a "微信授权成功" confirmation and a button (`open-type="getPhoneNumber"`) to bind the user's phone number, calling the phone-binding endpoint with the resulting code on tap

#### Scenario: Skip phone-number binding
- **WHEN** the user taps "暂不绑定，进入首页" on the phone-binding step
- **THEN** the app proceeds to the home page without calling the phone-binding endpoint, and the user remains logged in with the WeChat-issued tokens

#### Scenario: Developer login remains available as a secondary entry
- **WHEN** a developer taps the visually de-emphasized "开发者登录" link on the login page
- **THEN** the app performs the existing dev-login flow (synthesizes a `dev-` prefixed code and calls `POST /api/auth/wechat-login`) unchanged, regardless of the new visual treatment
