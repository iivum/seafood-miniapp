## ADDED Requirements

### Requirement: Login page (ad-01) form validation and lockout UX
The login page at `/login` MUST be implemented with React 18 + `react-hook-form` + `zod` resolver. The form MUST validate:
- `phone` — required, must match `^1[3-9]\d{9}$` (Chinese mobile pattern)
- `password` — required, minimum 6 characters

On submit the page MUST call `POST /api/admin/auth/cookie-login`. On 200 the page MUST navigate to the originally-requested URL (or `/` if none was stored). On 401 the page MUST show an inline error "手机号或密码错误". On 423 (`ACCOUNT_LOCKED`) the page MUST show a message with `retryAfterSeconds` formatted as "请 X 分 Y 秒后再试". On 429 (`AUTH_LOCKED`) the page MUST show "登录尝试次数过多,请 X 分钟后再试" and disable the submit button until the lockout window expires (via countdown timer).

The form MUST persist the lockout state to `sessionStorage` so a page refresh during the lockout window keeps the timer running.

#### Scenario: User submits valid credentials
- **WHEN** the user enters a valid phone and password and clicks "登录"
- **THEN** the form calls `POST /api/admin/auth/cookie-login`
- **AND** on 200, navigates to the original destination (or `/`)
- **AND** the httpOnly cookie is set by the response

#### Scenario: User submits invalid credentials (1st attempt)
- **WHEN** the user enters wrong credentials
- **THEN** the form shows an inline error "手机号或密码错误"
- **AND** the password field is cleared
- **AND** the submit button remains enabled

#### Scenario: User triggers 3rd failed attempt → IP lockout
- **WHEN** the user fails the 3rd login attempt
- **THEN** the response is HTTP 429 with `code: "AUTH_LOCKED"` and `Retry-After: 900`
- **AND** the form shows "登录尝试次数过多,请 15 分钟后再试"
- **AND** the submit button is disabled
- **AND** a 15:00 countdown timer appears below the button
- **AND** `users.login.attempts{result=locked}` is incremented on the server

#### Scenario: Form validation rejects malformed phone
- **WHEN** the user types a non-mobile phone number
- **THEN** the form shows an inline error "请输入正确的手机号"
- **AND** the submit button is disabled until the input is valid

---

### Requirement: Login page lockout persistence across refresh
The login page MUST persist lockout state (whether the user is currently locked out, the remaining seconds) to `sessionStorage` under the key `admin-login-lockout`. On page mount, if a lockout entry exists with `until > Date.now()`, the form MUST restore the disabled state and the countdown timer.

#### Scenario: User refreshes during IP lockout
- **WHEN** the user refreshes the page while the IP is locked out
- **THEN** the form is still disabled
- **AND** the countdown continues from the persisted `until - now` value
- **AND** the submit button stays disabled

#### Scenario: User opens login in a new tab during lockout
- **WHEN** the user opens a new browser tab and navigates to `/login`
- **THEN** `sessionStorage` is shared between tabs of the same origin
- **AND** the new tab also shows the lockout state

---

### Requirement: Dashboard (ad-02) four KPI cards
The dashboard at `/` MUST render four KPI cards in a 2×2 grid (or 4×1 on wide screens), each backed by `GET /api/admin/dashboard`'s `kpis` payload:
- **今日订单** (today's order count) — large number in `font-display` (Fraunces), subtitle "昨日 +N" or "昨日 -N" in `text-success` / `text-error` color
- **今日营收** (today's GMV in ¥) — large number in `font-display`, formatted with `formatYuan`
- **待发货** (orders in PAID status) — large number, clickable, navigates to `/orders?status=PAID`
- **库存预警** (products with stock < 10) — large number, clickable, navigates to `/products?filter=low-stock`

Each card MUST be wrapped in a shadcn `Card` with `CardHeader` (label) + `CardContent` (number + delta). Loading state MUST be a skeleton placeholder (pulsing rounded rect).

#### Scenario: User opens dashboard with fresh data
- **WHEN** the dashboard mounts and `GET /api/admin/dashboard` returns `{ kpis: { todayOrders: 12, todayGmv: 1280.50, toShip: 5, lowStock: 3 } }`
- **THEN** the four KPI cards render the corresponding values
- **AND** the "待发货" card links to `/orders?status=PAID`
- **AND** the "库存预警" card links to `/products?filter=low-stock`

#### Scenario: Dashboard query fails
- **WHEN** `GET /api/admin/dashboard` returns HTTP 500
- **THEN** the dashboard shows an error state with a "重新加载" retry button
- **AND** the KPI cards render their skeleton placeholders
