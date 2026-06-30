# Spec: admin-ui

## Purpose

[TBD — see change `refactor-rust-rebuild-frontend` for context. Defines the admin web UI built on React 18 + Vite + shadcn/ui, consuming the backend BFF endpoints under `/api/admin/**`.]

## Requirements

### Requirement: Admin authentication
The admin UI SHALL authenticate users via `POST /api/admin/auth/login` using a separate JWT secret from the mini-program, store the resulting access token in an `httpOnly` cookie, and SHALL NOT keep tokens in `localStorage`.

#### Scenario: Successful admin login
- **WHEN** the user submits valid admin credentials on the login form
- **THEN** the UI redirects to `/dashboard` and the access token cookie is set by the response

#### Scenario: Failed admin login
- **WHEN** the user submits invalid credentials
- **THEN** the UI shows an inline error message and does not navigate away

#### Scenario: Authenticated API call
- **WHEN** the UI makes any request to `/api/admin/**` after login
- **THEN** the browser automatically attaches the `httpOnly` cookie

#### Scenario: Token refresh
- **WHEN** any `/api/admin/**` request returns 401 due to an expired access token
- **THEN** the UI calls `POST /api/admin/auth/refresh` once, retries the original request, and only navigates to `/login` if the refresh also fails

### Requirement: Product management screens
The admin UI SHALL provide list, create, edit, and delete views for products, each backed by `/api/admin/**` aggregation endpoints and `/api/products/**` mutations.

#### Scenario: Admin opens the product list
- **WHEN** an admin navigates to `/admin/products`
- **THEN** the UI displays a paginated table of products fetched from `GET /api/admin/products/stats` plus `GET /api/products`

#### Scenario: Admin creates a product
- **WHEN** an admin submits the product form with valid fields
- **THEN** the UI calls `POST /api/products` and shows a success toast; the table refreshes

#### Scenario: Admin edits a product
- **WHEN** an admin submits the edit form with changes
- **THEN** the UI calls `PUT /api/products/{id}` and shows a success toast; the table row reflects the new value

#### Scenario: Admin deletes a product
- **WHEN** an admin confirms the delete dialog
- **THEN** the UI calls `DELETE /api/products/{id}` and removes the row; a failure shows an error toast

### Requirement: Order management screens
The admin UI SHALL provide a list of orders and a detail view that includes customer information and expanded line items.

#### Scenario: Admin opens the order list
- **WHEN** an admin navigates to `/admin/orders`
- **THEN** the UI shows a paginated list of orders from `GET /api/orders`

#### Scenario: Admin opens order detail
- **WHEN** an admin clicks an order row
- **THEN** the UI calls `GET /api/admin/orders/{id}/detail` and renders the aggregated payload (customer, line items with product info)

#### Scenario: Admin ships an order
- **WHEN** an admin clicks "Ship" on a `PAID` order
- **THEN** the UI calls `POST /api/orders/{id}/ship` and reflects the new status

### Requirement: Dashboard
The admin UI SHALL provide a dashboard at `/admin/dashboard` powered by `GET /api/admin/dashboard`.

#### Scenario: Admin opens the dashboard
- **WHEN** an admin navigates to `/admin/dashboard`
- **THEN** the UI renders three cards: order stats (today / week / month), product stats, and a top-products table

#### Scenario: Dashboard loading state
- **WHEN** the dashboard query is in-flight
- **THEN** the UI shows a skeleton placeholder for each card

#### Scenario: Dashboard error state
- **WHEN** the dashboard request fails
- **THEN** the UI shows an error state with a retry button

### Requirement: Route protection
The admin UI SHALL redirect unauthenticated users to the login page and SHALL block any non-admin role from reaching admin routes.

#### Scenario: Unauthenticated user visits a protected route
- **WHEN** an unauthenticated browser navigates to `/admin/products`
- **THEN** the UI redirects to `/admin/login` and remembers the original destination

#### Scenario: Authenticated admin visits login
- **WHEN** an already-authenticated admin navigates to `/admin/login`
- **THEN** the UI redirects to `/admin/dashboard`

### Requirement: Visual baseline
The admin UI SHALL use the shadcn/ui component library and Tailwind CSS, with a design-token file shared with the mini-program to guarantee visual coherence across surfaces.

#### Scenario: Component library is shadcn/ui
- **WHEN** any new view is built
- **THEN** the developer composes it from primitives in `src/components/ui/*` (shadcn) and avoids hand-rolling equivalent markup

#### Scenario: Colors come from tokens
- **WHEN** any element receives a color, spacing, or typography style
- **THEN** the style SHALL reference a token from the shared `tokens.json` rather than a hard-coded value

### Requirement: Admin UI scope is single-seller internal operations
The admin UI SHALL be scoped exclusively to single-seller internal operations. The system MUST NOT implement any of the following external-merchant capabilities: merchant onboarding, multi-tenant seller separation, merchant self-service portal, merchant settlement or revenue splitting, or merchant-facing analytics. Only the role `INTERNAL_OPERATOR` (or `INTERNAL_CS`) may authenticate and use the admin UI. The system MUST reject any JWT carrying a `MERCHANT` role at the filter chain with HTTP 401 `code=AUTH_INVALID_ROLE`.

#### Scenario: No external merchant routes exist
- **WHEN** a developer inspects `admin-ui/src/features/` and `admin-ui/src/routes.tsx` (or equivalent)
- **THEN** there is no route for merchant onboarding, settlement, storefront management, or seller analytics
- **AND** the backend `AdminBffController` exposes no endpoint matching `/api/admin/merchants/**` or `/api/admin/sellers/**`

#### Scenario: MERCHANT role JWT rejected
- **WHEN** a JWT carrying `role: "MERCHANT"` is presented at any `/api/admin/**` endpoint
- **THEN** the request is rejected with HTTP 401 and `code: "AUTH_INVALID_ROLE"`

---

### Requirement: Admin UI consumes OKLch token system
The admin UI MUST consume the `tokens.tailwind.ts` file generated by the same build step as the mini-program's `tokens.wxss`, ensuring visual parity between the two surfaces. The admin UI MUST NOT introduce any new hardcoded hex color values in JSX or CSS outside of the `tokens.tailwind.ts` source.

#### Scenario: Tailwind theme extends tokens
- **WHEN** a developer inspects `admin-ui/tailwind.config.ts`
- **THEN** `theme.extend.colors` exposes `bg`, `surface`, `fg`, `muted`, `border`, `accent`, `accent-soft`, `accent-strong`, `accent-deep`, `success`, `warning`, `error`, `info` (and their `-soft` variants)
- **AND** `theme.extend.fontFamily` exposes `display`, `body`, `mono`
- **AND** all values reference the imported `tokens` object

#### Scenario: Component uses token-derived class
- **WHEN** a developer writes `<div className="bg-accent text-bg font-body">`
- **THEN** the compiled CSS uses `oklch(64% 0.16 38)` and `oklch(99% 0.006 60)` (the canonical token values)

---

### Requirement: Admin UI six operational screens
The admin UI SHALL provide exactly six operational screens: login (`/login`), dashboard (`/`), product list (`/products`), product form (`/products/new` and `/products/{id}/edit`), order list (`/orders`), and order detail (`/orders/{id}`). All six screens MUST consume `/api/admin/**` endpoints (or, for product write paths, `/api/products/**` mutations). The detailed functional behavior of each screen is defined in the `admin-ui-modules` capability.

#### Scenario: Six routes registered
- **WHEN** a developer inspects the admin UI router
- **THEN** exactly six route entries exist: `/login`, `/`, `/products`, `/products/new`, `/products/{id}/edit`, `/orders`, `/orders/{id}`

#### Scenario: Product form saves via the public product API
- **WHEN** an admin submits the product form
- **THEN** the UI calls `POST /api/products` (for new) or `PUT /api/products/{id}` (for edit)
- **AND** the resulting product is visible in the product list

---

### Requirement: Admin UI deployment as third Docker service
The admin UI SHALL be deployed as a third Docker service `admin-ui` in `docker-compose.yml`, separate from `backend` and `mongodb`. The `admin-ui` service MUST depend on `backend` being healthy before starting. The bundled nginx configuration MUST reverse-proxy all `/api/**` requests to `http://backend:8080` so the browser does not need to know the backend's internal address. The service MUST expose port 5173 on the host for direct browser access.

#### Scenario: docker-compose up brings all three services
- **WHEN** a developer runs `docker-compose up -d` from a clean state
- **THEN** three services start: `mongodb`, `backend`, `admin-ui`
- **AND** `http://localhost:5173/` returns HTTP 200 with the admin login HTML

#### Scenario: API calls proxy through nginx
- **WHEN** the admin UI's JavaScript makes a request to `/api/admin/auth/cookie-login`
- **THEN** nginx forwards the request to `http://backend:8080/api/admin/auth/cookie-login`
- **AND** the response's `Set-Cookie` header is honored by the browser

---

### Requirement: Admin UI access matrix
The admin UI routes SHALL be guarded as follows:
- `/login` — public
- `/` (dashboard) — `INTERNAL_OPERATOR` or `INTERNAL_CS`
- `/products/**` — `INTERNAL_OPERATOR` only (write paths), all internal roles for read
- `/orders/**` — `INTERNAL_OPERATOR` only (write paths), all internal roles for read

When the user lacks the required role for a route, the admin UI MUST redirect to `/login` (when unauthenticated) or display a 403 page (when authenticated but lacking the role).

#### Scenario: Unauthenticated user visits `/products`
- **WHEN** a browser with no auth cookie navigates to `/products`
- **THEN** the UI redirects to `/login` and remembers the original destination

#### Scenario: Authenticated INTERNAL_CS visits `/products/new`
- **WHEN** a user with role `INTERNAL_CS` navigates to `/products/new`
- **THEN** the UI displays a 403 page with the message "权限不足"
- **AND** does not render the product form

---

### Requirement: Visual baseline refresh
The admin UI SHALL use the OKLch-based token system for all colors, typography, radii, and shadows. The admin UI MUST NOT use any Tailwind default colors (e.g. `bg-blue-500`, `text-red-600`) — all color usage MUST go through the token-derived utilities (`bg-accent`, `text-fg`, etc.). The design posture documented in the `visual-design-system` capability applies to the admin UI equally.

#### Scenario: No default Tailwind colors in components
- **WHEN** a developer searches `admin-ui/src/` for Tailwind color classes
- **THEN** the search finds no matches for `bg-(red|blue|green|yellow|purple|pink|gray|slate|zinc|neutral|stone|amber|orange|lime|emerald|teal|cyan|sky|indigo|violet|fuchsia|rose)-[0-9]`
- **AND** all color usage is via token-derived classes

#### Scenario: Three-font typography system in use
- **WHEN** a developer inspects any rendered admin UI page
- **THEN** headings and prices use the `font-display` (Fraunces) family
- **AND** body text uses the `font-body` (Inter Tight) family
- **AND** order IDs and numeric KPIs use the `font-mono` (Geist Mono) family

---

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

---

### Requirement: Dashboard LowStockList 三段着色
仪表盘库存预警列表 `LowStockList` SHALL 按库存数量分三段着色：
- `stock === 0`：显示「已售罄」红色 badge（`text-destructive` / `bg-destructive/10` border），不显示数字
- `1 ≤ stock < 5`：数字显示为橙色（`text-orange-600 font-semibold`）
- `5 ≤ stock < 10`：数字显示为黄色（`text-yellow-600 font-medium`）

#### Scenario: stock=0 显示已售罄 badge
- **WHEN** `lowStock` 列表中某商品 `stock === 0`
- **THEN** 该行库存列渲染「已售罄」badge，class 含 `text-destructive`
- **AND** 不渲染数字 `0`

#### Scenario: stock 在 [1,5) 显示橙色
- **WHEN** `lowStock` 列表中某商品 `1 ≤ stock < 5`
- **THEN** 该行库存数字的 class 含 `text-orange-600`

#### Scenario: stock 在 [5,10) 显示黄色
- **WHEN** `lowStock` 列表中某商品 `5 ≤ stock < 10`
- **THEN** 该行库存数字的 class 含 `text-yellow-600`

---

### Requirement: Dashboard LowStockList 缩略图列
`LowStockList` 表格 SHALL 在「商品」列渲染 32×32 px 缩略图（`h-8 w-8 rounded object-cover`）：
- 若 `imageUrl` 有值：渲染 `<img src={imageUrl} loading="lazy">`，`onError` 降级到灰色占位
- 若 `imageUrl` 为 `null` 或空字符串：渲染灰色占位 div（`bg-muted`），不渲染 `<img>`

#### Scenario: 商品有 imageUrl 渲染缩略图
- **WHEN** `lowStock[i].imageUrl` 为非空字符串
- **THEN** 该行渲染 `<img>` 元素，`src` 等于 `imageUrl`，class 含 `rounded`

#### Scenario: 商品无 imageUrl 渲染灰色占位
- **WHEN** `lowStock[i].imageUrl` 为 `null` 或 `""`
- **THEN** 该行渲染灰色占位 div，class 含 `bg-muted`
- **AND** 不渲染 `<img>` 元素

---

### Requirement: Dashboard LowStockList 空态
`LowStockList` 在 `items.length === 0` 时 SHALL 渲染绿色空态，包含：
- 绿色圆形图标（`CheckCircle2`，`text-green-600`）
- 文字「库存健康」

#### Scenario: lowStock 为空数组
- **WHEN** `GET /api/admin/dashboard` 返回 `lowStock: []`
- **THEN** 仪表盘库存预警区域渲染「库存健康」文字
- **AND** 不渲染表格行

---

### Requirement: Dashboard LowStockList 补货链接
`LowStockList` 每行 SHALL 渲染「去补货」链接，目标为 `/admin/products?highlight=${id}`，
使商家可在商品列表中定位并处理低库存商品。

#### Scenario: 点击「去补货」导航到商品列表
- **WHEN** 用户点击某商品行的「去补货」链接
- **THEN** 浏览器导航到 `/admin/products?highlight=${该商品id}`

---
