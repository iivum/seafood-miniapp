## ADDED Requirements

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
