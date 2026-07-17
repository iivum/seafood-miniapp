## MODIFIED Requirements

### Requirement: Order lifecycle
The system SHALL let CUSTOMERs place orders from their cart and read their own orders, while ADMINs SHALL be able to read all orders and advance order status. Order pricing (shipping fee and discount) is computed authoritatively on the server; the client MAY submit a `shippingMethod` selection but MUST NOT submit or influence the final `totalAmount` directly.

**Rationale for this delta**: 2026-07-13 E2E found the created order's `totalAmount` equal to the raw sum of item subtotals only — shipping fee and discount, both of which the mp checkout screen displays and promises to the user, were silently dropped because `shippingMethod` was never part of the request and no discount logic existed server-side.

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

#### Scenario: Order total includes shipping fee and discount
- **WHEN** a CUSTOMER calls `POST /api/orders` with `shippingMethod` set to a paid option (顺丰速运 / 中通快递) and an item subtotal ≥ ¥100
- **THEN** the created order's `totalAmount` equals `subtotal + shippingFee - discount`, where `shippingFee` is looked up server-side from the shipping method and `discount` is computed server-side from the ≥¥100 threshold rule
- **AND** the `OrderResponse` payload exposes `subtotal`, `shippingFee`, and `discount` alongside `totalAmount`

#### Scenario: Order without an explicit shipping method defaults to free shipping
- **WHEN** a CUSTOMER calls `POST /api/orders` with no `shippingMethod` field
- **THEN** the system treats it as `FREE` (shippingFee = 0) for pricing purposes

#### Scenario: Client-submitted total amount is ignored
- **WHEN** a CUSTOMER's request body contains any field resembling a total/amount override
- **THEN** the system computes `totalAmount` itself from items, shipping method, and discount rule, ignoring any client-submitted amount
