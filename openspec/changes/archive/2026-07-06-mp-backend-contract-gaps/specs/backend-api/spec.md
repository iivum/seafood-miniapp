## MODIFIED Requirements

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

#### Scenario: CUSTOMER updates a cart item's quantity
- **WHEN** a CUSTOMER calls `PUT /api/cart/items/{productId}` with a positive `quantity`
- **THEN** the system replaces that line's quantity (does not add to it) and returns the updated cart with status 200

#### Scenario: CUSTOMER updates the quantity of a line that is not in their cart
- **WHEN** a CUSTOMER calls `PUT /api/cart/items/{productId}` for a productId not present in their cart
- **THEN** the system returns HTTP 404 with `code=NOT_FOUND` and does not mutate the cart

#### Scenario: CUSTOMER toggles a cart item's selected state
- **WHEN** a CUSTOMER calls `PATCH /api/cart/items/{productId}` with no body
- **THEN** the system flips that line's `selected` boolean (true→false or false→true) and returns the updated cart with status 200

#### Scenario: CUSTOMER toggles selection of a line that is not in their cart
- **WHEN** a CUSTOMER calls `PATCH /api/cart/items/{productId}` for a productId not present in their cart
- **THEN** the system returns HTTP 404 with `code=NOT_FOUND` and does not mutate the cart

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

#### Scenario: CUSTOMER places a direct-buy order with explicit items
- **WHEN** a CUSTOMER calls `POST /api/orders` with a non-empty `items` array (each `{productId, quantity}`)
- **THEN** the system creates an Order from exactly those items (captures price/stock snapshots, decrements product stock, returns 201), and does NOT read from or mutate the CUSTOMER's cart

#### Scenario: CUSTOMER places a direct-buy order with insufficient stock
- **WHEN** a CUSTOMER calls `POST /api/orders` with an explicit `items` array where any line's quantity exceeds available stock
- **THEN** the system returns HTTP 409 with `code=DOMAIN`, does not mutate any document, and does not touch the cart

#### Scenario: CUSTOMER places an order without an items body
- **WHEN** a CUSTOMER calls `POST /api/orders` with no request body (or an empty/absent `items` field)
- **THEN** the system falls back to the existing cart-checkout behavior unchanged
