## ADDED Requirements

### Requirement: Order aggregate supports REFUNDING status and Refund sub-aggregate
The system SHALL extend the `Order` aggregate to include a new status value `REFUNDING` in the `OrderStatus` enum, alongside the existing values `PENDING` / `PAID` / `SHIPPED` / `COMPLETED` / `CANCELLED`. The system SHALL introduce a new `Refund` sub-aggregate with fields: `id`, `orderId`, `userId`, `reason`, `amount`, `status` (REQUESTED / APPROVED / REJECTED), `requestedAt`, `processedAt`, `processedBy`. The `Order.refundId` field SHALL be a nullable reference to a `Refund` aggregate, set when a refund is requested.

#### Scenario: Order status enum includes REFUNDING
- **WHEN** a developer inspects `OrderStatus` enum in the order domain
- **THEN** the enum contains exactly 7 values: `PENDING`, `PAID`, `SHIPPED`, `COMPLETED`, `CANCELLED`, `REFUNDING`, `REFUNDED`
- **AND** `REFUNDED` is documented as a terminal state

#### Scenario: Refund aggregate is creatable
- **WHEN** a user submits a refund request via `POST /api/orders/{id}/refund`
- **THEN** the system creates a new `Refund` aggregate with `status=REQUESTED`
- **AND** updates the parent `Order` to status `REFUNDING` and sets `Order.refundId`
- **AND** emits a `RefundRequested` domain event

#### Scenario: Refund approval transitions order
- **WHEN** an admin operator approves a refund via `POST /api/admin/orders/{id}/refund/approve`
- **THEN** the system updates the `Refund.status` to `APPROVED` and sets `processedAt`, `processedBy`
- **AND** updates the parent `Order` to status `REFUNDED`
- **AND** increments the `orders.refunded` counter by 1
- **AND** emits a `RefundApproved` domain event

---

### Requirement: Order state machine — customer actions
The system SHALL expose exactly the customer-action endpoints described below, each gated by a precondition check on the current `Order.status`:

| OrderStatus | Allowed customer actions |
|---|---|
| `PENDING` | `POST /api/orders/{id}/cancel`, `POST /api/orders/{id}/pay` |
| `PAID` | `POST /api/orders/{id}/remind-ship`, `POST /api/orders/{id}/refund` |
| `SHIPPED` | `GET /api/orders/{id}/tracking`, `POST /api/orders/{id}/confirm-receive` |
| `COMPLETED` | `POST /api/orders/{id}/rebuy`, `POST /api/orders/{id}/refund` |
| `CANCELLED` | `POST /api/orders/{id}/rebuy` |
| `REFUNDING` | none (awaiting admin review) |
| `REFUNDED` | `POST /api/orders/{id}/rebuy` |

Each endpoint MUST return HTTP 409 with `code: "INVALID_STATE"` if the current `Order.status` is not in the allowed set for that action.

#### Scenario: Cancel a PENDING order
- **WHEN** the order is in `PENDING` status
- **AND** the customer calls `POST /api/orders/{id}/cancel`
- **THEN** the system updates the order to `CANCELLED` and sets `cancelReason` from the request body
- **AND** increments `orders.cancelled{reason=user}` by 1
- **AND** returns HTTP 200

#### Scenario: Cancel a PAID order
- **WHEN** the order is in `PAID` status
- **AND** the customer calls `POST /api/orders/{id}/cancel`
- **THEN** the system returns HTTP 409 with `code: "INVALID_STATE"`
- **AND** the response message includes "paid orders cannot be cancelled, request a refund instead"

#### Scenario: Re-buy from a CANCELLED order
- **WHEN** the order is in `CANCELLED` status
- **AND** the customer calls `POST /api/orders/{id}/rebuy`
- **THEN** the system returns a list of `CartItem` objects equivalent to the order's items
- **AND** the customer frontend adds them to the cart and navigates to the cart page

#### Scenario: Re-buy from a SHIPPED order is rejected
- **WHEN** the order is in `SHIPPED` status
- **AND** the customer calls `POST /api/orders/{id}/rebuy`
- **THEN** the system returns HTTP 409 with `code: "INVALID_STATE"`

#### Scenario: Pay a PENDING order
- **WHEN** the order is in `PENDING` status
- **AND** the customer calls `POST /api/orders/{id}/pay`
- **THEN** the system transitions the order to `PAID`
- **AND** increments `orders.paid{paymentMethod=wechat,amountBucket=...}` by 1
- **AND** the `amountBucket` tag is computed via `OrderMetrics.bucketize(BigDecimal)` into one of `lt100`, `100to500`, `500to2000`, `gte2000`

---

### Requirement: Order tracking field
The system SHALL extend the `Order` aggregate to include a nullable `tracking: Tracking` value object with fields: `carrier` (string, e.g. "顺丰"), `trackingNumber` (string), `events: List<TrackingEvent>` where each `TrackingEvent` has `at: Instant`, `location: String`, `description: String`, and `status` (one of `SHIPPED`, `IN_TRANSIT`, `DELIVERED`, `EXCEPTION`). The system SHALL provide `GET /api/orders/{id}/tracking` returning the full `Tracking` value object. The `tracking` field MUST be populated by `OrderService.ship()` with at least one `SHIPPED` event and may be extended as the order progresses.

#### Scenario: Tracking absent on PENDING order
- **WHEN** a customer queries `GET /api/orders/{id}/tracking` for a PENDING order
- **THEN** the response returns HTTP 200 with body `{ "tracking": null }`

#### Scenario: Tracking populated after ship
- **WHEN** an admin operator marks an order as shipped
- **THEN** the system populates `Order.tracking` with a `SHIPPED` event at the current timestamp
- **AND** `GET /api/orders/{id}/tracking` returns the populated tracking

#### Scenario: Tracking timeline on order detail (ad-06)
- **WHEN** an admin operator opens an order with `tracking.events` containing 3 events
- **THEN** the order detail UI renders a 3-node timeline showing each event in chronological order
- **AND** each node displays `at`, `location`, `description`

---

### Requirement: Refund flow on mp-08
The system SHALL render a "申请退款" button on order list cards (mp-08) when the order status is `PAID` or `COMPLETED`. Tapping the button MUST open a bottom-sheet form asking for refund reason (free text, 1-200 chars) and confirming the refund amount (pre-filled to `order.totalAmount`). Submitting MUST call `POST /api/orders/{id}/refund` and refresh the order list. On success the card transitions to `REFUNDING` status and the action buttons update to "退款处理中" (no further customer actions available).

#### Scenario: Open refund sheet on a PAID order
- **WHEN** the customer opens the order list with a PAID order
- **THEN** the order card shows a "申请退款" button in the action row
- **AND** tapping it opens a bottom-sheet form with reason textarea and amount field

#### Scenario: Submit a refund request
- **WHEN** the customer enters a refund reason and taps "提交"
- **THEN** the system POSTs to `/api/orders/{id}/refund` with `{ reason, amount }`
- **AND** on success the order card transitions to `REFUNDING` status
- **AND** the action row shows "退款处理中" with no further buttons

#### Scenario: Refund request on a SHIPPED order is not offered
- **WHEN** the customer opens the order list with a SHIPPED order
- **THEN** the order card action row shows only "查看物流" and "确认收货"
- **AND** "申请退款" is not present (must wait until `COMPLETED`)

---

### Requirement: Refund review flow on ad-06
The system SHALL render a refund review UI on the admin order detail screen (ad-06) when `Order.status = REFUNDING`. The UI MUST display the refund reason, requested amount, request timestamp, and present two action buttons: "同意退款" and "拒绝退款" (with mandatory rejection reason textarea, 1-200 chars). On approval, the system MUST call `POST /api/admin/orders/{id}/refund/approve`; on rejection, `POST /api/admin/orders/{id}/refund/reject` with the rejection reason.

#### Scenario: Refund review UI is present
- **WHEN** an admin operator opens an order in `REFUNDING` status
- **THEN** the order detail page shows a refund review section
- **AND** displays the refund reason and amount
- **AND** offers "同意退款" and "拒绝退款" buttons

#### Scenario: Approve a refund
- **WHEN** the admin operator clicks "同意退款"
- **THEN** the system POSTs to `/api/admin/orders/{id}/refund/approve`
- **AND** the order transitions to `REFUNDED`
- **AND** `orders.refunded` counter increments by 1

#### Scenario: Reject a refund without reason is blocked
- **WHEN** the admin operator clicks "拒绝退款" with an empty reason textarea
- **THEN** the system shows a validation error "请填写拒绝原因"
- **AND** does not submit the request

---

### Requirement: Counter instrumentation for order state transitions
The system SHALL increment the following Micrometer counters at the specified transition points, all carrying the `application=seafood-backend` common tag:

| Counter | Tags | Increment site |
|---|---|---|
| `orders.created` | `paymentMethod=wechat` | `OrderService.create()` success path |
| `orders.cancelled` | `reason` ∈ {`user`,`timeout`,`admin`,`other`} | `OrderService.cancel()` success path |
| `orders.paid` | `paymentMethod`, `amountBucket` ∈ {`lt100`,`100to500`,`500to2000`,`gte2000`}` | `OrderService.markPaid()` success path |
| `orders.refunded` | none | `OrderService.approveRefund()` success path |

The `amountBucket` tag MUST be computed via `OrderMetrics.bucketize(BigDecimal)` with the geometric thresholds: < 100 RMB, 100-500 RMB, 500-2000 RMB, >= 2000 RMB.

#### Scenario: Order creation increments orders.created
- **WHEN** `OrderService.create()` completes successfully
- **THEN** the `orders.created` counter is incremented by 1 with `paymentMethod=wechat`

#### Scenario: Refund approval increments orders.refunded
- **WHEN** `OrderService.approveRefund()` completes successfully
- **THEN** the `orders.refunded` counter is incremented by 1

#### Scenario: Counter tag values are bounded
- **WHEN** the system emits a counter increment for any of the above
- **THEN** all tag keys and values belong to a pre-declared whitelist (ArchUnit-enforced)
- **AND** no high-cardinality identifiers (userId, orderId, productId, email) appear as tag keys
