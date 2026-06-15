## ADDED Requirements

### Requirement: Product duplicate endpoint
The system SHALL expose `POST /api/admin/products/{id}/duplicate` accepting the source product id and creating a new product that copies all fields from the source (name with " (副本)" suffix, description, category, status forced to `OUT_OF_STOCK` to prevent accidental sale, price, stock, images, SKUs, shipping info). The new product's id MUST be a fresh UUID and `createdAt` MUST be the current timestamp. The endpoint MUST return HTTP 201 with the new product object.

#### Scenario: Successful duplication
- **WHEN** an admin operator POSTs to `/api/admin/products/{id}/duplicate`
- **THEN** the system creates a new product with name "{sourceName} (副本)" and `status=OUT_OF_STOCK`
- **AND** copies all other fields including the SKUs (with new SKU ids)
- **AND** returns HTTP 201 with the new product

#### Scenario: Source product not found
- **WHEN** the source product id does not exist
- **THEN** the system returns HTTP 404 with `code: "NOT_FOUND"`

---

### Requirement: Product list CSV export
The system SHALL expose `POST /api/admin/products/export` accepting a filter body (category, status, keyword — same fields as the list endpoint) and returning a CSV stream. The CSV MUST include columns: id, name, category, price, stock, status, createdAt, updatedAt. The response Content-Type MUST be `text/csv; charset=utf-8` and the Content-Disposition MUST be `attachment; filename="products-<timestamp>.csv"`. The system MUST support exporting at least 10,000 rows in a single stream without OOM (use streaming response writer).

#### Scenario: Export with no filter
- **WHEN** an admin operator POSTs to `/api/admin/products/export` with an empty body
- **THEN** the response is a CSV file containing all products
- **AND** the response is streamed (not buffered entirely in memory)

#### Scenario: Export with category filter
- **WHEN** the body specifies `category: "鱼类"`
- **THEN** the CSV contains only products in category "鱼类"

---

### Requirement: Image upload endpoint
The system SHALL expose `POST /api/admin/uploads` accepting multipart/form-data with one or more image files (allowed MIME types: `image/jpeg`, `image/png`, `image/webp`; max file size 5 MB per file). The endpoint MUST return JSON with the uploaded URLs. The system MUST generate URLs that are publicly accessible (e.g. `https://cdn.example.com/products/<uuid>.jpg`). The implementation MUST be configurable to use either local disk (default in dev) or object storage (OSS/S3) via the `app.uploads.backend` configuration property.

#### Scenario: Successful upload of one image
- **WHEN** the admin operator uploads a 1 MB JPEG
- **THEN** the system returns HTTP 200 with `{ urls: ["https://cdn.example.com/products/<uuid>.jpg"] }`

#### Scenario: File too large
- **WHEN** the admin operator uploads a 10 MB file
- **THEN** the system returns HTTP 413 with `code: "FILE_TOO_LARGE"`
- **AND** `fieldErrors` contains the file name and "exceeds 5 MB limit"

#### Scenario: Invalid MIME type
- **WHEN** the admin operator uploads a `.pdf` file
- **THEN** the system returns HTTP 415 with `code: "UNSUPPORTED_MEDIA_TYPE"`

---

### Requirement: Bulk ship orders endpoint
The system SHALL expose `POST /api/admin/orders/batch-ship` accepting a JSON body `{ orderIds: List<String> }` (max 100 ids per request). The system MUST attempt to ship each order independently: if an order is not in `PAID` status, the system MUST skip it and record the failure in the response (does not abort the batch). The response MUST include `{ succeeded: List<String>, failed: List<{ orderId, reason }> }`. The system MUST increment `orders.paid{paymentMethod=wechat,batch=true}` counter for each successfully shipped order.

#### Scenario: Mixed status batch
- **WHEN** the operator submits `{ orderIds: ["ord-1", "ord-2", "ord-3"] }` where ord-1 is PAID, ord-2 is SHIPPED, ord-3 is PAID
- **THEN** the system ships ord-1 and ord-3, skips ord-2
- **AND** returns `{ succeeded: ["ord-1", "ord-3"], failed: [{ orderId: "ord-2", reason: "INVALID_STATE" }] }`

#### Scenario: Empty list rejected
- **WHEN** the operator submits `{ orderIds: [] }`
- **THEN** the system returns HTTP 400 with `code: "VALIDATION"`

#### Scenario: Batch size exceeded
- **WHEN** the operator submits more than 100 ids
- **THEN** the system returns HTTP 400 with `code: "BATCH_TOO_LARGE"`
- **AND** the response message includes "max 100 per batch"

---

### Requirement: Picklist PDF generation
The system SHALL expose `POST /api/admin/orders/{id}/print-picklist` returning a PDF stream of the picklist for the given order. The picklist MUST include: order ID (mono font, e.g. `ORD-20260607-0184`), order placement time, customer name and phone, full shipping address, and a table of items with: SKU/spec, product name, quantity, and a checkbox column for picker initials. The response Content-Type MUST be `application/pdf` and the Content-Disposition MUST be `inline; filename="picklist-<orderId>.pdf"`.

#### Scenario: Successful picklist generation
- **WHEN** an admin operator requests the picklist for a PAID order
- **THEN** the response is a PDF stream of the picklist
- **AND** the response headers indicate inline disposition
- **AND** the PDF body is well-formed (opens in a PDF viewer)

#### Scenario: Order not in PAID status
- **WHEN** the operator requests a picklist for an order not in PAID status
- **THEN** the system returns HTTP 409 with `code: "INVALID_STATE"`

---

### Requirement: Order list CSV export
The system SHALL expose `GET /api/admin/orders/export?status=&from=&to=&format=csv` returning a CSV stream of orders matching the filter. The CSV MUST include columns: orderId, userId, totalAmount, status, itemCount, createdAt, updatedAt. The response Content-Type MUST be `text/csv; charset=utf-8` and the Content-Disposition MUST be `attachment; filename="orders-<timestamp>.csv"`. The system MUST support streaming for at least 10,000 rows.

#### Scenario: Export with date range
- **WHEN** the operator requests `/api/admin/orders/export?from=2026-06-01&to=2026-06-13&format=csv`
- **THEN** the CSV contains only orders with `createdAt` in the specified range

#### Scenario: Status filter
- **WHEN** the operator requests `/api/admin/orders/export?status=PAID`
- **THEN** the CSV contains only PAID orders

---

### Requirement: Dashboard field extension
The system SHALL extend the `GET /api/admin/dashboard` response payload to include:
- `trend7d: List<{ date: String (YYYY-MM-DD), orderCount: int, gmv: BigDecimal }>` — exactly 7 entries covering the prior 7 days
- `lowStock: List<{ productId, name, category, stock }>` — top 10 products with `stock < 10`, ordered by `stock` ascending
- The existing `kpis` and `recentOrders` fields are preserved

#### Scenario: Dashboard payload includes trend and low-stock
- **WHEN** an admin operator loads the dashboard
- **THEN** the response JSON contains `kpis`, `trend7d` (length 7), `recentOrders`, and `lowStock` (length ≤ 10)

#### Scenario: Low-stock list ordering
- **WHEN** there are 15 products with `stock < 10`
- **THEN** the dashboard's `lowStock` field returns the 10 with the lowest stock (ascending)

---

### Requirement: CSV streaming for large exports
The system SHALL use chunked transfer encoding (response streaming) for both product and order CSV exports to avoid loading the full result set into memory. The implementation MUST use a streaming response writer (e.g. `StreamingResponseBody` in Spring MVC) and MUST flush rows in batches of at most 500.

#### Scenario: Export 10,000 products without OOM
- **WHEN** the operator exports 10,000 products
- **THEN** the backend JVM heap usage does not increase by more than 50 MB during the export
- **AND** the response is delivered in chunks
- **AND** the export completes within 30 seconds on the seed dataset
