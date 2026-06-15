# Spec: backend-api

## Purpose

[TBD — see change `refactor-rust-rebuild-frontend` for context. Defines the HTTP contract for the single-module Spring Boot backend, including product/cart/order CRUD, admin BFF aggregation, uniform error responses, and static admin asset hosting.]

## Requirements

### Requirement: Public product browsing
The system SHALL expose product browsing endpoints that allow any client (authenticated or anonymous) to list and inspect products.

#### Scenario: Anonymous client lists products
- **WHEN** a client calls `GET /api/products` without an authentication token
- **THEN** the system returns a paginated list of products with status 200

#### Scenario: Anonymous client views a single product
- **WHEN** a client calls `GET /api/products/{id}` with a valid product id
- **THEN** the system returns the product payload with status 200

#### Scenario: Anonymous client requests a missing product
- **WHEN** a client calls `GET /api/products/{id}` with an id that does not exist
- **THEN** the system returns HTTP 404 with an `ErrorResponse` body whose `code` is `NOT_FOUND`

### Requirement: Admin product management
The system SHALL restrict product create / update / delete operations to ADMIN role and persist changes to MongoDB.

#### Scenario: Admin creates a product
- **WHEN** an ADMIN user calls `POST /api/products` with a valid product body
- **THEN** the system persists the product and returns 201 with the created resource

#### Scenario: CUSTOMER attempts to create a product
- **WHEN** a CUSTOMER user calls `POST /api/products` with a valid product body
- **THEN** the system returns HTTP 403

#### Scenario: Anonymous attempts to create a product
- **WHEN** an unauthenticated client calls `POST /api/products`
- **THEN** the system returns HTTP 401

#### Scenario: Admin updates a product
- **WHEN** an ADMIN user calls `PUT /api/products/{id}` with a valid body
- **THEN** the system updates the product and returns 200 with the updated resource

#### Scenario: Admin deletes a product
- **WHEN** an ADMIN user calls `DELETE /api/products/{id}` for an existing product
- **THEN** the system removes the product and returns 204

### Requirement: Customer cart operations
The system SHALL provide per-user cart endpoints that are accessible only to the owning CUSTOMER (or any ADMIN).

#### Scenario: CUSTOMER adds an item to their cart
- **WHEN** a CUSTOMER calls `POST /api/cart/items` with a productId and quantity
- **THEN** the system upserts the cart document for that user and returns the updated cart with status 200

#### Scenario: CUSTOMER views their cart
- **WHEN** a CUSTOMER calls `GET /api/cart`
- **THEN** the system returns the cart scoped to the authenticated user's id with status 200

#### Scenario: CUSTOMER clears their cart
- **WHEN** a CUSTOMER calls `DELETE /api/cart`
- **THEN** the system removes all items from that user's cart and returns 204

#### Scenario: One CUSTOMER attempts to read another CUSTOMER's cart
- **WHEN** a CUSTOMER calls `GET /api/cart?userId=<otherUserId>`
- **THEN** the system ignores the query parameter and returns the caller's own cart with status 200

### Requirement: Order lifecycle
The system SHALL let CUSTOMERs place orders from their cart and read their own orders, while ADMINs SHALL be able to read all orders and advance order status.

#### Scenario: CUSTOMER places an order
- **WHEN** a CUSTOMER calls `POST /api/orders` with a non-empty cart
- **THEN** the system creates an Order with status `PENDING`, captures price/stock snapshots, decrements product stock, and returns 201 with the order payload

#### Scenario: CUSTOMER places an order with insufficient stock
- **WHEN** a CUSTOMER calls `POST /api/orders` and any cart item quantity exceeds available stock
- **THEN** the system returns HTTP 409 with `code=DOMAIN` and does not mutate any document

#### Scenario: CUSTOMER lists their own orders
- **WHEN** a CUSTOMER calls `GET /api/orders`
- **THEN** the system returns only orders whose `userId` matches the authenticated principal

#### Scenario: Admin ships a PAID order
- **WHEN** an ADMIN calls `POST /api/orders/{id}/ship` on a `PAID` order
- **THEN** the system transitions the order to `SHIPPED` and returns 200

#### Scenario: Admin ships a PENDING order
- **WHEN** an ADMIN calls `POST /api/orders/{id}/ship` on a `PENDING` order
- **THEN** the system returns HTTP 409 with `code=DOMAIN` describing the invalid transition

### Requirement: Admin BFF aggregation
The system SHALL expose three aggregated read endpoints under `/api/admin/**` for the admin UI; each endpoint SHALL assemble data by calling the in-process application services (no network calls) and SHALL be restricted to ADMIN role.

#### Scenario: Admin fetches order detail
- **WHEN** an ADMIN calls `GET /api/admin/orders/{id}/detail`
- **THEN** the system returns a single payload containing the order, the customer, and a populated `items[].product` for each line, with status 200

#### Scenario: Admin fetches product stats
- **WHEN** an ADMIN calls `GET /api/admin/products/stats`
- **THEN** the system returns `{ total, onSale, outOfStock, byCategory: { ... } }` with status 200

#### Scenario: Admin fetches the dashboard
- **WHEN** an ADMIN calls `GET /api/admin/dashboard`
- **THEN** the system returns `{ orderStats: { today, week, month }, productStats, topProducts[] }` with status 200

#### Scenario: Non-admin calls a BFF endpoint
- **WHEN** a CUSTOMER calls any `GET /api/admin/**` endpoint
- **THEN** the system returns HTTP 403

### Requirement: Uniform error responses
The system SHALL translate all unhandled and domain exceptions into a single `ErrorResponse` shape with a stable `code` and human-readable `message`.

#### Scenario: Validation failure on request body
- **WHEN** a controller receives a request whose body fails Bean Validation
- **THEN** the system returns HTTP 400 with `code=VALIDATION` and a `fieldErrors` map

#### Scenario: Domain rule violation
- **WHEN** any application service throws `DomainException`
- **THEN** the system returns HTTP 409 with `code=DOMAIN` and the exception message

#### Scenario: Resource not found
- **WHEN** any application service throws `NotFoundException`
- **THEN** the system returns HTTP 404 with `code=NOT_FOUND` and the exception message

### Requirement: Static admin asset hosting
The system SHALL serve the production admin-ui build artifacts from `classpath:/static/admin/**` at the URL prefix `/admin/**` and SHALL fall back to `index.html` for unknown client routes.

#### Scenario: Client requests the admin SPA root
- **WHEN** a browser requests `GET /admin/`
- **THEN** the system returns 200 with the SPA `index.html`

#### Scenario: Client requests an admin SPA sub-route
- **WHEN** a browser requests `GET /admin/orders/123` directly
- **THEN** the system returns 200 with the SPA `index.html` (history-API fallback)

#### Scenario: Client requests a missing static asset
- **WHEN** a browser requests `GET /admin/missing.js`
- **THEN** the system returns 404 and does not fall back to `index.html`


### Requirement: SCA 扫描隔离性

The OWASP Dependency-Check task (`dependencyCheckAnalyze`) SHALL NOT be wired as a dependency of `./gradlew check`, and the build configuration SHALL NOT execute it from the JVM check pipeline (`.github/workflows/ci.yml`). The task SHALL only be executed by the dedicated security pipeline (`.github/workflows/security.yml`).

The system MUST keep the NVD datafeed cache (`~/.gradle/dependency-check-data`) scoped to the security pipeline so that running the task from `check` would create a second cold-start download path that bypasses the weekly-bucket cache key and the `NVD_API_KEY` injection.

#### Scenario: gradle check 不跑 Dep-Check

- **WHEN** a developer runs `./gradlew check` locally or in `ci.yml`
- **THEN** the build does NOT execute `dependencyCheckAnalyze`, and the build's task graph SHALL NOT include a path from `check` to `dependencyCheckAnalyze`

#### Scenario: Dep-Check 仅在 security.yml 跑

- **WHEN** the CI pipeline triggers a build
- **THEN** only the `security.yml → dependency-check` job executes `dependencyCheckAnalyze`; `ci.yml` MUST NOT have a step or dependency that invokes it

#### Scenario: 回退检测 — check.dependsOn 重新指向 Dep-Check

- **WHEN** a PR modifies `backend/build.gradle` to add `tasks.named('check') { dependsOn 'dependencyCheckAnalyze' }` (or any equivalent wiring)
- **THEN** the PR review SHALL reject the change as a regression of this requirement, regardless of any justifying comment in the build script

### Requirement: Backend HTTP responses include baseline security headers
Every HTTP response served by the backend — JSON API, BFF, static admin assets, and `/actuator/**` — SHALL include the baseline security header set defined in capability `runtime-security`. Existing endpoint behavior (status codes, body shape, `code` values) SHALL remain unchanged.

#### Scenario: JSON API response carries security headers
- **WHEN** any existing endpoint under `/api/**` (e.g. `GET /api/products`) returns a response
- **THEN** the response carries `Strict-Transport-Security`, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, and `Content-Security-Policy` headers per `runtime-security`

#### Scenario: Static admin response carries security headers
- **WHEN** a browser fetches a static admin asset under `/admin/**`
- **THEN** the response carries the same security header set

#### Scenario: Error response carries security headers
- **WHEN** the global exception handler returns an `ErrorResponse` (4xx or 5xx)
- **THEN** the response carries the same security header set

### Requirement: Admin BFF endpoints enforce a request rate limit
Every endpoint under `/api/admin/**` (including BFF aggregation, admin product CRUD, and admin auth) SHALL enforce the 60 rpm **fixed-window** limit defined in capability `runtime-security`. Excess requests SHALL receive HTTP 429 with an `ErrorResponse` whose `code` is `RATE_LIMITED`. The non-admin endpoints under `/api/**` SHALL NOT be rate-limited by this rule. PR review #27: prior wording "token-bucket" was inaccurate.

#### Scenario: BFF dashboard rate-limited
- **WHEN** an ADMIN client issues a 61st request to `GET /api/admin/dashboard` within 60 seconds
- **THEN** the response is HTTP 429 with `code=RATE_LIMITED` and the `Retry-After` header set

#### Scenario: Admin product CRUD rate-limited
- **WHEN** an ADMIN client issues a 61st request to `POST /api/admin/products` (or any admin write path) within 60 seconds
- **THEN** the response is HTTP 429 with `code=RATE_LIMITED`

#### Scenario: Non-admin path not rate-limited
- **WHEN** a CUSTOMER client issues 100 requests to `GET /api/products` within 60 seconds
- **THEN** every response is 200 (no `RATE_LIMITED` body), because `/api/products` is outside `/api/admin/**`

### Requirement: Admin product batch endpoints
The system SHALL expose the following admin-side product batch endpoints, all restricted to `INTERNAL_OPERATOR` role:
- `POST /api/admin/products/{id}/duplicate` — duplicate a product
- `POST /api/admin/products/export` — export products matching a filter as CSV

Both endpoints are detailed in the `admin-batch-operations` capability. The duplicate endpoint MUST return HTTP 201 with the new product; the export endpoint MUST return a streamed CSV response with `Content-Type: text/csv; charset=utf-8`.

#### Scenario: Duplicate a product
- **WHEN** an `INTERNAL_OPERATOR` calls `POST /api/admin/products/{id}/duplicate` for an existing product
- **THEN** the system creates a new product with name "{sourceName} (副本)" and `status=OUT_OF_STOCK`
- **AND** returns HTTP 201 with the new product

#### Scenario: Export products with filter
- **WHEN** an `INTERNAL_OPERATOR` calls `POST /api/admin/products/export` with `{ category: "鱼类" }`
- **THEN** the response is a streamed CSV containing only products in category "鱼类"
- **AND** the response includes `Content-Disposition: attachment; filename="products-<timestamp>.csv"`

---

### Requirement: Admin image upload endpoint
The system SHALL expose `POST /api/admin/uploads` accepting multipart/form-data with one or more image files (MIME types `image/jpeg` / `image/png` / `image/webp`, max 5 MB each). The endpoint is restricted to `INTERNAL_OPERATOR` role. The response MUST be JSON of shape `{ urls: ["..."] }`. The system MUST be configurable to use either local disk (default) or object storage (OSS/S3) via `app.uploads.backend`.

#### Scenario: Upload one image
- **WHEN** an `INTERNAL_OPERATOR` uploads one 1 MB JPEG
- **THEN** the system returns HTTP 200 with `{ urls: ["https://cdn.example.com/products/<uuid>.jpg"] }`

#### Scenario: Oversize file rejected
- **WHEN** the operator uploads a 10 MB file
- **THEN** the system returns HTTP 413 with `code: "FILE_TOO_LARGE"`

---

### Requirement: Admin order batch endpoints
The system SHALL expose the following admin-side order batch endpoints, all restricted to `INTERNAL_OPERATOR` role:
- `POST /api/admin/orders/batch-ship` — bulk ship up to 100 PAID orders
- `POST /api/admin/orders/{id}/print-picklist` — return a PDF picklist for one PAID order
- `GET /api/admin/orders/export?status=&from=&to=&format=csv` — export orders as CSV

The bulk ship endpoint MUST attempt each order independently, returning `{ succeeded: List<String>, failed: List<{ orderId, reason }> }` rather than aborting on first failure. The print-picklist endpoint MUST return `Content-Type: application/pdf` with `Content-Disposition: inline; filename="picklist-<orderId>.pdf"`.

#### Scenario: Mixed batch outcome
- **WHEN** the operator submits `{ orderIds: ["ord-1", "ord-2", "ord-3"] }` where ord-1 and ord-3 are PAID, ord-2 is SHIPPED
- **THEN** the system ships ord-1 and ord-3, skips ord-2
- **AND** returns `{ succeeded: ["ord-1", "ord-3"], failed: [{ orderId: "ord-2", reason: "INVALID_STATE" }] }`

#### Scenario: Picklist generation
- **WHEN** the operator requests the picklist for a PAID order
- **THEN** the response is a PDF stream
- **AND** the response `Content-Disposition` is `inline; filename="picklist-<orderId>.pdf"`

#### Scenario: Order export with date range
- **WHEN** the operator requests `/api/admin/orders/export?from=2026-06-01&to=2026-06-13&format=csv`
- **THEN** the response is a streamed CSV containing only orders in the date range

---

### Requirement: Customer order action endpoints
The system SHALL expose the following customer-side order action endpoints:
- `POST /api/orders/{id}/cancel` — cancel a `PENDING` order
- `POST /api/orders/{id}/pay` — pay a `PENDING` order
- `POST /api/orders/{id}/remind-ship` — send a remind-ship notification on a `PAID` order (derived; no domain state change)
- `POST /api/orders/{id}/confirm-receive` — confirm receipt on a `SHIPPED` order (transitions to `COMPLETED`)
- `POST /api/orders/{id}/rebuy` — re-buy from any terminal-state order (returns cart items)
- `POST /api/orders/{id}/refund` — request refund on a `PAID` or `COMPLETED` order
- `GET /api/orders/{id}/tracking` — fetch the tracking value object (returns `null` if not yet shipped)

Each endpoint MUST return HTTP 409 with `code: "INVALID_STATE"` if the current `Order.status` is not in the allowed set for that action. The detailed transition matrix is defined in the `order-customer-state-machine` capability.

#### Scenario: Cancel a PENDING order
- **WHEN** the customer calls `POST /api/orders/{id}/cancel` on a PENDING order
- **THEN** the system transitions the order to `CANCELLED` and returns HTTP 200

#### Scenario: Cancel a PAID order is rejected
- **WHEN** the customer calls `POST /api/orders/{id}/cancel` on a PAID order
- **THEN** the system returns HTTP 409 with `code: "INVALID_STATE"`

#### Scenario: Re-buy from a terminal order
- **WHEN** the customer calls `POST /api/orders/{id}/rebuy` on a `COMPLETED`, `CANCELLED`, or `REFUNDED` order
- **THEN** the system returns a JSON array of `CartItem` objects (with `productId`, `skuId` if applicable, `quantity`)

#### Scenario: Refund request on PAID order
- **WHEN** the customer calls `POST /api/orders/{id}/refund` with a non-empty reason on a PAID order
- **THEN** the system creates a `Refund` aggregate with `status=REQUESTED`
- **AND** transitions the parent order to `REFUNDING`
- **AND** returns HTTP 201 with the refund id

#### Scenario: Tracking on PENDING order
- **WHEN** the customer calls `GET /api/orders/{id}/tracking` on a PENDING order
- **THEN** the response is HTTP 200 with `{ tracking: null }`

---

### Requirement: Order aggregate extension
The system SHALL extend the `Order` aggregate to include:
- `tracking: Tracking` (nullable value object with `carrier`, `trackingNumber`, `events: List<TrackingEvent>`)
- `refundId: UUID` (nullable reference to a `Refund` aggregate, set when a refund is requested)

The `OrderStatus` enum SHALL include the new value `REFUNDING`. The existing values `PENDING` / `PAID` / `SHIPPED` / `COMPLETED` / `CANCELLED` remain unchanged.

#### Scenario: OrderStatus includes REFUNDING
- **WHEN** a developer inspects `OrderStatus`
- **THEN** the enum contains at minimum: `PENDING`, `PAID`, `SHIPPED`, `COMPLETED`, `CANCELLED`, `REFUNDING`

#### Scenario: Tracking populated by ship
- **WHEN** `OrderService.ship()` is called on a PAID order
- **THEN** the system populates `Order.tracking` with a `SHIPPED` event at the current timestamp
- **AND** transitions the order to `SHIPPED`

---

### Requirement: Refund sub-aggregate
The system SHALL introduce a new `Refund` aggregate with fields: `id` (UUID), `orderId` (UUID, FK to Order), `userId` (UUID), `reason` (string, 1-200 chars), `amount` (BigDecimal, > 0), `status` (enum: `REQUESTED`, `APPROVED`, `REJECTED`), `requestedAt` (Instant), `processedAt` (Instant, nullable), `processedBy` (UUID, nullable — operator id), `rejectionReason` (string, nullable). The `Refund` aggregate MUST be persisted in its own MongoDB collection `refunds`.

#### Scenario: Refund created on request
- **WHEN** a customer submits a refund request
- **THEN** the system creates a `Refund` document with `status: "REQUESTED"`
- **AND** links the parent order via `Order.refundId`

#### Scenario: Refund approval
- **WHEN** an admin operator calls `POST /api/admin/orders/{id}/refund/approve`
- **THEN** the system updates the `Refund` to `status: "APPROVED"`, sets `processedAt` and `processedBy`
- **AND** transitions the parent order to `REFUNDED`

#### Scenario: Refund rejection
- **WHEN** an admin operator calls `POST /api/admin/orders/{id}/refund/reject` with a non-empty reason
- **THEN** the system updates the `Refund` to `status: "REJECTED"`, sets `processedAt`, `processedBy`, and `rejectionReason`
- **AND** transitions the parent order back to its prior status (e.g. `PAID` or `COMPLETED`)

---

### Requirement: Dashboard payload extension
The `GET /api/admin/dashboard` response payload MUST include the keys `kpis`, `trend7d`, `recentOrders`, and `lowStock`. The `trend7d` value MUST be a list of exactly 7 entries each shaped `{ date: "YYYY-MM-DD", orderCount: int, gmv: BigDecimal }` covering the prior 7 days. The `lowStock` value MUST be a list of at most 10 entries each shaped `{ productId, name, category, stock }` ordered by `stock` ascending.

#### Scenario: Dashboard payload shape
- **WHEN** an `INTERNAL_OPERATOR` calls `GET /api/admin/dashboard`
- **THEN** the response JSON contains `kpis` (object), `trend7d` (length 7), `recentOrders` (list), and `lowStock` (length ≤ 10)
- **AND** each `trend7d` entry has a `date` in `YYYY-MM-DD` form

#### Scenario: Low-stock list ordering
- **WHEN** 15 products have `stock < 10`
- **THEN** the dashboard's `lowStock` field returns the 10 with the lowest stock (ascending)

---

### Requirement: Product aggregate extension for SKUs
The system SHALL extend the `Product` aggregate to include a nullable `skus: List<SKU>` collection. Each `SKU` value object MUST have fields: `id` (UUID, server-generated), `spec` (string, 1-50 chars), `unitPrice` (BigDecimal, > 0), `stock` (int, ≥ 0), `position` (int, unique within product). When `skus` is null or empty, `Product.price` and `Product.stock` are the effective single-SKU representation. When `skus` is non-empty, `Product.price` and `Product.stock` MUST be ignored for display and inventory, and SKU values take precedence.

The system MUST reject product create/update payloads with duplicate `spec` values within `skus` with HTTP 409 `code: "DUPLICATE_SKU_SPEC"`. Inventory deduction during order create MUST use the SKU's `stock`, not `Product.stock`, when SKUs are present.

#### Scenario: Create product with SKUs
- **WHEN** an `INTERNAL_OPERATOR` calls `POST /api/products` with a payload containing `skus: [{ spec: "500g/份", unitPrice: 68, stock: 50, position: 1 }, { spec: "1kg/份", unitPrice: 128, stock: 20, position: 2 }]`
- **THEN** the system persists the product with the SKUs
- **AND** returns HTTP 201

#### Scenario: Duplicate SKU spec rejected
- **WHEN** the payload contains two SKUs with the same `spec` value
- **THEN** the system returns HTTP 409 with `code: "DUPLICATE_SKU_SPEC"`

#### Scenario: Order create deducts from SKU stock
- **WHEN** a customer submits an order for 2 units of a SKU with `stock: 50`
- **THEN** the system atomically decrements the SKU's `stock` by 2 to 48
- **AND** `Product.stock` is unchanged

#### Scenario: Insufficient SKU stock
- **WHEN** a customer submits an order for 5 units of a SKU with `stock: 3`
- **THEN** the system returns HTTP 409 with `code: "INSUFFICIENT_STOCK"` and `{ skuId, availableStock: 3 }`
- **AND** no inventory is deducted

---

### Requirement: Admin refund review endpoints
The system SHALL expose the following admin-side refund review endpoints, restricted to `INTERNAL_OPERATOR` role:
- `POST /api/admin/orders/{id}/refund/approve` — approve a pending refund
- `POST /api/admin/orders/{id}/refund/reject` — reject a pending refund (mandatory reason, 1-200 chars)

Both endpoints MUST return HTTP 409 `code: "INVALID_STATE"` if the order is not in `REFUNDING` status.

#### Scenario: Approve a pending refund
- **WHEN** an operator calls `POST /api/admin/orders/{id}/refund/approve` on a REFUNDING order
- **THEN** the system transitions the order to `REFUNDED`
- **AND** returns HTTP 200

#### Scenario: Reject with empty reason rejected
- **WHEN** the operator calls `POST /api/admin/orders/{id}/refund/reject` with an empty reason
- **THEN** the system returns HTTP 400 with `code: "VALIDATION"` and `fieldErrors.reason: "请填写拒绝原因"`

---

### Requirement: Customer order state-transition endpoints contract
The four customer-side state-transition endpoints MUST conform to the following contract. Each endpoint MUST return HTTP 200 on a successful transition, and HTTP 409 with `code: "INVALID_STATE"` if the current `Order.status` is not in the action's allowed-from set (as defined in the `order-customer-state-machine` capability).

| Endpoint | Allowed from | Target state | Side effect | Counter |
|---|---|---|---|---|
| `POST /api/orders/{id}/cancel` | `PENDING` | `CANCELLED` | Restock products (SKU stock +N), refund any discount | `orders.cancelled{reason="user"}` |
| `POST /api/orders/{id}/pay` | `PENDING` | `PAID` | Capture payment (stub — increment counter only) | `orders.paid{paymentMethod="wechat", amountBucket}` |
| `POST /api/orders/{id}/confirm-receive` | `SHIPPED` | `COMPLETED` | None | `orders.completed` (new) |
| `POST /api/orders/{id}/rebuy` | `COMPLETED` / `CANCELLED` / `REFUNDED` | (no state change) | Returns cart items; caller adds to cart | `orders.rebuy` (new) |
| `POST /api/orders/{id}/refund` | `PAID` / `COMPLETED` | `REFUNDING` | Creates Refund aggregate, sets `Order.refundId` | `orders.refunding{reason="customer"}` |
| `POST /api/orders/{id}/remind-ship` | `PAID` | (no state change) | Send notification (stub) | `orders.remind_ship` (new) |
| `GET /api/orders/{id}/tracking` | (any) | n/a | Returns `{ tracking: Tracking | null }` | (no counter) |

Each endpoint MUST be reachable only by the order's owning customer (or any ADMIN). The `OrderService` MUST route all transitions through a single `transition(orderId, action, principal)` method that looks up the action in a `Map<OrderAction, TransitionRule>` and emits the appropriate counter via `OrderMetrics`.

#### Scenario: Customer cancels a PENDING order
- **WHEN** the customer calls `POST /api/orders/{id}/cancel` on a PENDING order
- **THEN** the system transitions the order to `CANCELLED` and returns 200
- **AND** the SKU stock is incremented by the order's quantity
- **AND** `orders.cancelled{reason="user"}` is incremented by 1

#### Scenario: Customer pays a PENDING order
- **WHEN** the customer calls `POST /api/orders/{id}/pay` on a PENDING order with `totalAmount` in the "100to500" bucket
- **THEN** the system transitions the order to `PAID` and returns 200
- **AND** `orders.paid{paymentMethod="wechat", amountBucket="100to500"}` is incremented by 1

#### Scenario: Customer confirms a SHIPPED order
- **WHEN** the customer calls `POST /api/orders/{id}/confirm-receive` on a SHIPPED order
- **THEN** the system transitions the order to `COMPLETED` and returns 200
- **AND** `orders.completed` is incremented by 1

#### Scenario: Customer calls cancel on a PAID order
- **WHEN** the customer calls `POST /api/orders/{id}/cancel` on a PAID order
- **THEN** the system returns HTTP 409 with `code: "INVALID_STATE"` and a message "PAID orders cannot be cancelled, please request a refund"

#### Scenario: Customer calls confirm-receive on a PENDING order
- **WHEN** the customer calls `POST /api/orders/{id}/confirm-receive` on a PENDING order
- **THEN** the system returns HTTP 409 with `code: "INVALID_STATE"` and a message "Only SHIPPED orders can be confirmed received"

#### Scenario: Customer requests a refund on a PAID order
- **WHEN** the customer calls `POST /api/orders/{id}/refund` with `{ reason: "..." }` on a PAID order
- **THEN** the system creates a `Refund` aggregate with `status: REQUESTED`
- **AND** transitions the parent order to `REFUNDING`
- **AND** sets `Order.refundId` to the new Refund's id
- **AND** returns HTTP 201 with `{ refundId: "..." }`

#### Scenario: Customer rebusies a COMPLETED order
- **WHEN** the customer calls `POST /api/orders/{id}/rebuy` on a COMPLETED order
- **THEN** the system returns HTTP 200 with a JSON array of `CartItem` objects (`productId`, `quantity`, `skuId` if applicable)
- **AND** the order's status is unchanged
- **AND** `orders.rebuy` is incremented by 1

#### Scenario: Tracking query on PENDING order
- **WHEN** the customer calls `GET /api/orders/{id}/tracking` on a PENDING order
- **THEN** the response is HTTP 200 with `{ tracking: null }`

#### Scenario: Tracking query on SHIPPED order
- **WHEN** the customer calls `GET /api/orders/{id}/tracking` on a SHIPPED order
- **THEN** the response is HTTP 200 with `{ tracking: { carrier: "顺丰", trackingNumber: "SF123", events: [...] } }`

#### Scenario: One CUSTOMER calls cancel on another CUSTOMER's order
- **WHEN** CUSTOMER A calls `POST /api/orders/{orderB}/cancel`
- **THEN** the system returns HTTP 403 (or 404, matching the existing "own orders only" pattern)

---

### Requirement: Customer state-transition endpoint authorization
The 7 customer-side state-transition endpoints (`/cancel`, `/pay`, `/confirm-receive`, `/rebuy`, `/refund`, `/remind-ship`, `/tracking`) MUST be restricted to the order's owning customer (or any `INTERNAL_OPERATOR` role). Anonymous requests MUST return HTTP 401. The auth check MUST compare `Order.userId` to the JWT's `sub` claim for CUSTOMER tokens.

#### Scenario: Owner cancels own order
- **WHEN** CUSTOMER A (with `sub=userA`) calls `POST /api/orders/{orderA}/cancel` where `orderA.userId = userA`
- **THEN** the system proceeds with the transition and returns 200

#### Scenario: Non-owner attempts to cancel
- **WHEN** CUSTOMER B (with `sub=userB`) calls `POST /api/orders/{orderA}/cancel` where `orderA.userId = userA`
- **THEN** the system returns HTTP 403 with `code: "FORBIDDEN"`

#### Scenario: Anonymous attempts to pay
- **WHEN** an unauthenticated request hits `POST /api/orders/{id}/pay`
- **THEN** the system returns HTTP 401 with `code: "TOKEN_MISSING"` (or the standard `UNAUTHENTICATED` code)

---
