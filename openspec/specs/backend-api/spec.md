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
