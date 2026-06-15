## ADDED Requirements

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
