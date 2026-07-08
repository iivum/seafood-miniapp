## MODIFIED Requirements

### Requirement: Order list and detail (mp-08) customer action row
The order list (`pages-sub/order/order-list/order-list`) and order detail MUST render the `OrderActionRow` component below each order card. The action row's buttons MUST be selected according to the order's current `Order.status` per the `order-customer-state-machine` capability:
- `PENDING` → "取消订单" (calls `POST /api/orders/{id}/cancel`) + "立即付款" (calls `POST /api/orders/{id}/pay`)
- `PAID` → "提醒发货" (calls `POST /api/orders/{id}/remind-ship`) + "申请退款" (calls `POST /api/orders/{id}/refund`)
- `SHIPPED` → "查看物流" (navigates to tracking view) + "确认收货" (calls `POST /api/orders/{id}/confirm-receive`)
- `COMPLETED` → "评价" (placeholder toast "评价功能开发中") + "再次购买" (calls `POST /api/orders/{id}/rebuy`) + "申请售后" (calls `POST /api/orders/{id}/refund`)
- `REFUNDING` → "退款处理中" (no-op, disabled)
- `CANCELLED` → "删除" (local only, hides from list) + "再次购买" (calls `POST /api/orders/{id}/rebuy`)

Tapping any action button MUST show a loading state, call the corresponding endpoint, and on 200 refresh the affected order. On 409 (invalid state) the UI MUST show a toast with the error message and refresh the order to reflect the actual server state.

This requirement's action behavior MUST be identical regardless of which screen (list or detail) the action is triggered from — the two screens MAY have separate page-level code, but MUST NOT diverge in which endpoint an action calls or whether it's a real call versus a placeholder.

#### Scenario: User cancels a PENDING order from the action row
- **WHEN** the user taps "取消订单" on a PENDING order
- **THEN** the UI shows a confirm dialog
- **AND** on confirm, calls `POST /api/orders/{id}/cancel`
- **AND** on 200, the order's status badge updates to "已取消" and the action row changes to the CANCELLED set

#### Scenario: User attempts an invalid action
- **WHEN** the user taps "确认收货" on a PENDING order (already invalid because only SHIPPED orders can be confirmed)
- **THEN** the UI shows a toast "订单状态已变更" and refreshes the order
- **AND** the action row updates to reflect the actual server status

#### Scenario: REFUNDING order has no customer actions
- **WHEN** the order is in `REFUNDING` status
- **THEN** the action row renders only a disabled "退款处理中" pill
- **AND** no taps on the row trigger any network call

#### Scenario: User requests a refund from either the list or detail screen
- **WHEN** the user taps "申请退款" (PAID) or "申请售后" (COMPLETED) from the order list screen, and confirms
- **THEN** the app calls `POST /api/orders/{id}/refund` — the same real endpoint call the order detail screen's equivalent action makes, not a placeholder message
