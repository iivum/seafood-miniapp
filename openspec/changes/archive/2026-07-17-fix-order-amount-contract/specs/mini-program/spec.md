## MODIFIED Requirements

### Requirement: Order confirmation page (mp-06) OD-aligned layout
The order confirmation page (`pages-sub/order/order-confirm/order-confirm`) MUST render, in vertical order, matching the OD golden `frontend/e2e/od-golden/mp-06-order-confirm.png` to within a 5% perceptual-diff threshold (`npm run test:visual mp-06-order-confirm`) and passing the geometry gate (`npm run test:geometry mp-06-order-confirm`):
- **Address card**: selected address (name/phone/full address) or an empty placeholder "📍 请选择收货地址"; tapping navigates to `pages-sub/user/address/address-list?selectMode=true`.
- **Items card**: one row per line item (image, name, price, quantity).
- **Delivery method card**: 3 selectable options (免运费 / 顺丰速运 / 中通快递), selected option shows a check mark and `is-selected` styling.
- **Remark card**: a `<textarea>` capped at 50 characters with a live counter.
- **Summary card**: 商品总额 / 运费 / 优惠 / 实付 rows, 实付 emphasized in `var(--accent)` `font-display`. These figures are a **local estimate** computed from the same shipping-fee table and discount threshold the backend uses (kept in sync via tests, per `backend-api`'s Order creation pricing requirement) — they are not the authoritative amount.
- **Bottom bar**: sticky, shows the running total and a submit button that is disabled while `cartItems.length === 0` or a submission is in flight.

The selected `shippingMethod` MUST be included in the `POST /api/orders` request body on submit. After order creation, this page and every downstream screen that displays order amounts (order-success, order-list, order-detail) MUST render `subtotal`/`shippingFee`/`discount`/`totalAmount` from the backend `OrderResponse`, never from the local pre-submit estimate.

This page MUST NOT render with an empty `cartItems` list when reached via the normal checkout flow (cart → confirm) or the direct-buy flow (product detail → confirm, per the `Direct buy from product detail` requirement); an empty items card on entry indicates a state-passing bug, not a valid empty state.

**Rationale for this delta**: 2026-07-13 E2E found the pre-submit summary card's 优惠 (and, on root-cause analysis, 运费 too) computed entirely client-side and never transmitted to or recomputed by the backend — the created order's `totalAmount` silently ignored both, producing a user-visible mismatch between what checkout promised and what the order record shows.

#### Scenario: Direct-buy entry pre-loads the single product
- **WHEN** the user arrives at mp-06 via "立即购买" with `{ source: "direct_buy", items: [{ productId, quantity: 2 }] }`
- **THEN** the items card shows exactly that product at quantity 2, not an empty list

#### Scenario: Selecting a delivery method updates the summary
- **WHEN** the user taps "顺丰速运" (¥12)
- **THEN** the delivery card shows it as selected
- **AND** the summary card's 运费 row and 实付 total update to include the ¥12 fee (local estimate)

#### Scenario: Submit includes shipping method and amounts match after creation
- **WHEN** the user submits the order with a selected delivery method and (if subtotal ≥ ¥100) the discount threshold met
- **THEN** the `POST /api/orders` request body includes `shippingMethod`
- **AND** the resulting order's `totalAmount` (as shown on the order-success/list/detail screens) equals the pre-submit estimate shown on this page

#### Scenario: Submit is disabled with no items
- **WHEN** `cartItems.length === 0`
- **THEN** the bottom bar's submit button carries the `is-disabled` class and taps are a no-op
