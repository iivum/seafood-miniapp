## ADDED Requirements

### Requirement: Admin UI scope is single-seller internal operations only
The system SHALL scope the admin UI (`admin-ui/`) to internal operations for a single seller. The system MUST NOT implement any of the following external-merchant capabilities: merchant registration, merchant onboarding, multi-tenant seller separation, merchant self-service portal, merchant settlement or revenue splitting, or merchant-facing analytics dashboards. Only the internal operations role (operator or customer service) may authenticate and use the admin UI.

#### Scenario: No external merchant endpoints exposed
- **WHEN** a developer reviews `admin-ui/src/features/`
- **THEN** the directory contains exactly four feature folders: `auth`, `dashboard`, `product`, `order`
- **AND** no feature folder exists for merchant onboarding, settlement, or storefront management
- **AND** the backend `AdminBffController` exposes no endpoint matching `/api/admin/merchants/**` or `/api/admin/sellers/**`

#### Scenario: Admin login authenticates internal operator only
- **WHEN** an internal operator authenticates via `POST /api/admin/auth/cookie-login`
- **THEN** the response sets an `httpOnly`, `Secure`, `SameSite=Lax` cookie signed with `JWT_ADMIN_SECRET`
- **AND** the issued JWT contains a `role` claim equal to `INTERNAL_OPERATOR` (or `INTERNAL_CS`)
- **AND** no role equal to `MERCHANT` is recognized by the system

---

### Requirement: Admin login screen (ad-01)
The system SHALL provide an admin login screen at the path `/login` accepting phone-number-or-username + password. After three consecutive failed attempts from the same IP within a 15-minute window, the system MUST lock further login attempts from that IP for 15 minutes and increment the `users.login.attempts{result=locked}` counter. On successful authentication the system MUST issue an httpOnly cookie (see auth spec).

#### Scenario: Successful login
- **WHEN** an operator submits valid phone and password to the login form
- **THEN** the system sets an httpOnly cookie
- **AND** redirects the browser to `/` (dashboard)
- **AND** increments `users.login.attempts{result=success}` by 1

#### Scenario: Failed login attempt
- **WHEN** an operator submits an invalid password
- **THEN** the system returns HTTP 401 with `code: "AUTH_INVALID"`
- **AND** increments `users.login.attempts{result=failed}` by 1
- **AND** the form displays "账号或密码错误" message

#### Scenario: Account lockout after 3 failures
- **WHEN** an operator has submitted 3 failed login attempts within 15 minutes
- **AND** submits a 4th attempt
- **THEN** the system returns HTTP 429 with `code: "AUTH_LOCKED"`
- **AND** increments `users.login.attempts{result=locked}` by 1
- **AND** further attempts from the same IP within 15 minutes return HTTP 429 without re-checking credentials

---

### Requirement: Admin dashboard screen (ad-02)
The system SHALL provide a dashboard screen at the path `/` displaying: (a) 4 KPI cards — orders today, GMV today, active products, active users (24h); (b) a 7-day trend chart (orders + GMV); (c) a recent-orders list (latest 10, clickable to ad-06); (d) a low-stock list (top 10 products with stock < 10). The dashboard MUST consume `GET /api/admin/dashboard` whose payload SHALL include `kpis`, `trend7d`, `recentOrders`, and `lowStock` keys.

#### Scenario: Dashboard renders all four sections
- **WHEN** an authenticated operator navigates to `/`
- **THEN** the page renders the 4 KPI cards, the trend chart, the recent-orders list, and the low-stock list
- **AND** the trend chart shows 7 data points (one per day) covering the prior 7 days
- **AND** clicking a recent order row navigates to `/orders/{id}`

#### Scenario: Dashboard low-stock list
- **WHEN** the dashboard loads
- **THEN** the low-stock list shows at most 10 products with stock < 10
- **AND** each row shows product name, current stock, and category
- **AND** clicking a row navigates to `/products/{id}/edit`

---

### Requirement: Admin product list screen (ad-03)
The system SHALL provide a product list screen at `/products` rendering a data table with columns: thumbnail, name, category, price, stock, status, updatedAt, actions. The screen MUST support: top filter (category / status / keyword), multi-select with bulk publish/unpublish/delete, per-row edit / toggle status / duplicate / delete, CSV export, and pagination at 20 rows per page.

#### Scenario: Product list renders with filters
- **WHEN** an operator navigates to `/products`
- **THEN** the table renders the first 20 products
- **AND** the top filter exposes category, status, and keyword inputs
- **AND** selecting a filter triggers a new `GET /api/products?page=0&size=20&category=...&status=...&q=...`

#### Scenario: Bulk publish
- **WHEN** an operator selects 3 products and clicks "批量上架"
- **THEN** the system sends one `PATCH /api/products/{id}` per product with `status=ACTIVE`
- **AND** the table reflects the new statuses without a full reload

#### Scenario: Duplicate a product
- **WHEN** an operator clicks "复制" on a single product row
- **THEN** the system calls `POST /api/admin/products/{id}/duplicate`
- **AND** on success the new product appears in the table as a copy with name suffix "(副本)"

#### Scenario: Export CSV
- **WHEN** an operator clicks "导出 CSV"
- **THEN** the system POSTs to `/api/admin/products/export` with the current filter set
- **AND** the response is a CSV file stream
- **AND** the browser initiates a download named `products-<timestamp>.csv`

---

### Requirement: Admin product form screen (ad-04)
The system SHALL provide a product form screen at `/products/new` and `/products/{id}/edit` accepting: name, description (rich text), category (dropdown), status (radio), price (¥), stock, images (multi-upload with primary marking and drag-reorder), SKUs (inline-editable rows: spec name + unit price + stock, with add/remove), shipping info (weight, origin, storage method). The form MUST support three submit actions: save (draft), save and publish, preview.

#### Scenario: Create a new product with images and SKUs
- **WHEN** an operator fills the form with name, price, stock, 3 uploaded images, and 2 SKU rows
- **AND** clicks "保存并发布"
- **THEN** the system POSTs to `POST /api/products` (or `PUT /api/products/{id}` for edit) with the full payload
- **AND** the 3 images were uploaded via `POST /api/admin/uploads` returning URLs included in the product payload
- **AND** the 2 SKUs are persisted on `Product.skus` field
- **AND** the new product is listed at `/products` with status `ACTIVE`

#### Scenario: Image upload
- **WHEN** an operator selects 3 image files in the image picker
- **THEN** the system uploads them to `POST /api/admin/uploads` (one request per file or batched)
- **AND** the response returns URLs that the form binds to the product payload
- **AND** the operator can mark one image as the primary image and reorder via drag

---

### Requirement: Admin order list screen (ad-05)
The system SHALL provide an order list screen at `/orders` rendering a data table with columns: order ID (mono font), user, item count, paid amount, status, placed time, actions. The screen MUST support: top filter (order ID, status, time range), 6 status tabs (全部 / 待付款 / 待发货 / 待收货 / 待评价 / 售后), per-row ship / cancel / close / print-picklist / view-detail, bulk ship / bulk export, and pagination at 20 rows per page.

#### Scenario: Order list filters by status tab
- **WHEN** an operator clicks the "待发货" tab
- **THEN** the table refreshes with `GET /api/orders?status=PAID&page=0&size=20`
- **AND** the active tab is visually marked with the `accent` color

#### Scenario: Bulk ship
- **WHEN** an operator selects 5 orders in status PAID and clicks "批量发货"
- **THEN** the system POSTs to `/api/admin/orders/batch-ship` with the 5 order IDs
- **AND** the response returns the count of successfully shipped orders
- **AND** the table reflects the new SHIPPED status for those rows

#### Scenario: Print picklist
- **WHEN** an operator clicks "拣货单打印" on a single PAID order
- **THEN** the system POSTs to `/api/admin/orders/{id}/print-picklist`
- **AND** the response is a PDF stream with filename `picklist-<orderId>.pdf`
- **AND** the browser opens a PDF preview

---

### Requirement: Admin order detail screen (ad-06)
The system SHALL provide an order detail screen at `/orders/{id}` rendering a 3-column layout: left (2/3 width) shows ordered items table, shipping tracking timeline (when SHIPPED or later), and operation history; right (1/3 width) shows user information (name, phone, WeChat), shipping address, and amount breakdown. Bottom action bar MUST be status-driven: PAID → ship, PENDING → cancel, COMPLETED → refund review, REFUNDING → approve/reject refund.

#### Scenario: Order detail renders all sections
- **WHEN** an operator navigates to `/orders/{id}`
- **THEN** the page renders ordered items, user info, address, and amount breakdown
- **AND** if `Order.tracking` is present, the shipping timeline shows 3 nodes (shipped / in-transit / delivered)
- **AND** the bottom action bar shows only the actions valid for the current status

#### Scenario: Approve a refund request
- **WHEN** an order is in status REFUNDING
- **AND** the operator clicks "同意退款"
- **THEN** the system updates the order to REFUNDED
- **AND** increments `orders.refunded` counter by 1
- **AND** the timeline shows the refund approval entry

---

### Requirement: Admin UI deployment as third Docker service
The system SHALL deploy the admin UI as a third Docker service `admin-ui` in `docker-compose.yml`, distinct from `backend` and `mongodb`. The `admin-ui` service MUST be built from `admin-ui/Dockerfile` (multi-stage: Node 20 build → nginx:1.27-alpine serve), depend on `backend` being healthy, and expose port 5173 to the host. The nginx config MUST reverse-proxy `/api/` requests to `http://backend:8080`.

#### Scenario: docker-compose up brings all three services
- **WHEN** a developer runs `docker-compose up -d`
- **THEN** three services start: `mongodb`, `backend`, `admin-ui`
- **AND** the `admin-ui` container logs report "nginx entered ready state" within 5 seconds
- **AND** `http://localhost:5173/` returns HTTP 200 with the admin login HTML

#### Scenario: API calls proxy through nginx
- **WHEN** the admin UI's JS makes a request to `/api/admin/auth/cookie-login`
- **THEN** nginx forwards the request to `http://backend:8080/api/admin/auth/cookie-login`
- **AND** the response sets the `httpOnly` cookie that the browser stores and includes in subsequent admin requests

---

### Requirement: Admin UI token consumption
The system SHALL configure `admin-ui/tailwind.config.ts` to consume the generated `tokens.tailwind.ts` such that Tailwind utilities like `bg-accent`, `text-fg`, `border-border`, `rounded-pill`, `shadow-md`, `font-display`, `font-body`, `font-mono` resolve to the canonical OKLch values from `docs/redesign/tokens.json`.

#### Scenario: Token-derived Tailwind classes resolve
- **WHEN** a developer writes `<button className="bg-accent text-bg font-body rounded-pill">`
- **THEN** the compiled CSS for this button contains `background-color: oklch(64% 0.16 38);` (the `accent` value)
- **AND** `color: oklch(99% 0.006 60);` (the `bg` value)
- **AND** `font-family: 'Inter Tight', ...;`
- **AND** `border-radius: 9999px;`
