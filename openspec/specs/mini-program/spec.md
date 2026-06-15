# Spec: mini-program

## Purpose

[TBD — see change `refactor-rust-rebuild-frontend` for context. Defines the WeChat mini-program frontend behavior: feature-based layout, product browsing, WeChat login, cart/checkout, order history, and shared design tokens with the admin UI.]

## Requirements

### Requirement: Feature-based directory layout
The mini-program source tree SHALL be organized by feature, with each feature exposing its own `api.ts`, `types.ts`, and `components/` directory under `src/features/<feature>/`. Shared cross-feature code SHALL live under `src/shared/`.

#### Scenario: A developer adds a new feature
- **WHEN** a developer creates the directory `src/features/<new>/`
- **THEN** the build, lint, and Jest configuration pick it up without further wiring (no edits to `tsconfig.json` paths or Jest `moduleDirectories`)

#### Scenario: Cross-feature reuse
- **WHEN** two features need the same UI primitive (e.g. an `Empty` placeholder)
- **THEN** they SHALL import it from `src/shared/components/Empty` rather than duplicating it inside the feature folder

### Requirement: Product browsing flow
The mini-program SHALL let any user browse the product list and view a product detail page by calling the public `/api/products/**` endpoints.

#### Scenario: User opens the product list page
- **WHEN** the user navigates to the product list page
- **THEN** the page calls `GET /api/products`, renders a `ProductCard` for each item, and supports pull-to-refresh

#### Scenario: User opens product detail
- **WHEN** the user taps a product card
- **THEN** the detail page calls `GET /api/products/{id}` and renders the product information, price, and stock

#### Scenario: Network failure on the product list
- **WHEN** the product list request fails
- **THEN** the page shows an `Empty` component with a retry button

### Requirement: Authentication and session
The mini-program SHALL authenticate via WeChat login: it SHALL call `wx.login` to obtain a `code`, exchange it for an `openId` at `POST /api/auth/wechat-login`, and store the returned access/refresh tokens in a way that survives page reloads but is cleared on logout.

#### Scenario: First-launch login
- **WHEN** the app starts and no valid token is present
- **THEN** the app calls `wx.login`, exchanges the `code` via `POST /api/auth/wechat-login`, persists the tokens, and proceeds to the home page

#### Scenario: Token refresh
- **WHEN** any API call returns 401 and the response indicates the access token is expired
- **THEN** the app calls `POST /api/auth/refresh` once, retries the original request, and only re-runs the WeChat login flow if the refresh also fails

#### Scenario: Logout
- **WHEN** the user taps the logout entry in the profile page
- **THEN** the app clears stored tokens and returns to the home page in an anonymous state

### Requirement: Cart and checkout
The mini-program SHALL let authenticated users add items to their cart, review the cart, and place an order, persisting cart state across page navigations.

#### Scenario: User adds a product to the cart
- **WHEN** an authenticated user taps "Add to cart" on a product detail page
- **THEN** the app calls `POST /api/cart/items` and shows a success toast; the cart badge increments

#### Scenario: User reviews the cart
- **WHEN** the user opens the cart page
- **THEN** the app calls `GET /api/cart` and lists the items with per-line quantity controls

#### Scenario: User places an order
- **WHEN** the user taps "Checkout" on a non-empty cart
- **THEN** the app calls `POST /api/orders`, clears the cart on success, and navigates to the order detail page

#### Scenario: Checkout with out-of-stock item
- **WHEN** the user taps "Checkout" and any line item has insufficient stock
- **THEN** the app shows an inline error and does not call `POST /api/orders`

### Requirement: Order history
The mini-program SHALL let authenticated users view their own order history and order detail.

#### Scenario: User opens the order list
- **WHEN** the user opens the order list page
- **THEN** the app calls `GET /api/orders` and lists the user's orders, most recent first

#### Scenario: User opens order detail
- **WHEN** the user taps an order row
- **THEN** the app calls `GET /api/orders/{id}` and shows the order status, line items, and totals

### Requirement: Design-token parity with admin-ui
The mini-program SHALL consume the same `tokens.json` design tokens as the admin UI for color, spacing, and typography values, ensuring visual coherence between the customer-facing app and the admin surface.

#### Scenario: Tokens resolve at build time
- **WHEN** a WXSS rule references a token (e.g. `var(--color-primary)`)
- **THEN** the build pipeline substitutes the value from the shared `tokens.json`

#### Scenario: Token update propagates to both surfaces
- **WHEN** the shared `tokens.json` is updated
- **THEN** both the mini-program and the admin UI reflect the new value after their next build, with no further code changes

### Requirement: Direct buy from product detail
The mini-program SHALL support a "立即购买" action on the product detail page (mp-03) that bypasses the cart and proceeds directly to the order confirmation screen (mp-06) with the current product and selected quantity (or SKU) pre-loaded. The order confirmation screen MUST treat the direct-buy entry as equivalent to a cart checkout in all respects except the source of the line items.

#### Scenario: Direct buy with a product without SKUs
- **WHEN** the user taps "立即购买" on a product with no SKUs and quantity 2
- **THEN** the app navigates to mp-06 with `{ source: "direct_buy", items: [{ productId, quantity: 2 }] }`
- **AND** mp-06 does NOT touch the cart store

#### Scenario: Direct buy with a SKU selected
- **WHEN** the user selects a SKU and taps "立即购买" with quantity 2
- **THEN** the app navigates to mp-06 with `{ source: "direct_buy", items: [{ productId, skuId, quantity: 2 }] }`

#### Scenario: Direct buy with quantity exceeding stock
- **WHEN** the quantity stepper's max is the SKU's stock
- **AND** the user attempts to set quantity above the max
- **THEN** the stepper caps the quantity at the max
- **AND** the "立即购买" button is disabled when stock = 0

---

### Requirement: Address management entry points
The mini-program SHALL expose address management via: (a) the "我的" page (mp-05) tool list, which navigates to the address list in list mode; (b) the order checkout page (mp-06) "更换" button, which navigates to the address list in selection mode. The address list in selection mode MUST return the selected address id to mp-06 via the page navigation API.

#### Scenario: Open address list from profile
- **WHEN** the user taps "地址管理" in the mp-05 tool list
- **THEN** the app navigates to the address list in list mode (no selection semantics)

#### Scenario: Open address list from checkout
- **WHEN** the user taps "更换" on the address card in mp-06
- **THEN** the app navigates to the address list in selection mode
- **AND** on selection, mp-06 receives the selected address id via the previous-page data channel

---

### Requirement: Order state machine — customer actions visible in UI
The mini-program SHALL render the action button row on each order list card (mp-08) and order detail (mp-08 detail view) according to the order's current status, exactly as specified in the `order-customer-state-machine` capability. The action row MUST only display buttons valid for the current `Order.status`; tapping a button MUST trigger the corresponding endpoint and refresh the order list.

#### Scenario: Action row for PENDING order
- **WHEN** the order is in `PENDING` status
- **THEN** the action row shows "取消订单" and "立即付款"

#### Scenario: Action row for PAID order
- **WHEN** the order is in `PAID` status
- **THEN** the action row shows "提醒发货" and "申请退款"

#### Scenario: Action row for SHIPPED order
- **WHEN** the order is in `SHIPPED` status
- **THEN** the action row shows "查看物流" and "确认收货"

#### Scenario: Action row for COMPLETED order
- **WHEN** the order is in `COMPLETED` status
- **THEN** the action row shows "评价", "再次购买", "申请售后"
- **AND** "评价" is a placeholder that displays a toast "评价功能开发中"

#### Scenario: Action row for REFUNDING order
- **WHEN** the order is in `REFUNDING` status
- **THEN** the action row shows only "退款处理中" (no further customer actions)

#### Scenario: Action row for CANCELLED order
- **WHEN** the order is in `CANCELLED` status
- **THEN** the action row shows "删除" and "再次购买"

---

### Requirement: Real design-token parity (no fallbacks to hex)
The mini-program MUST consume the design tokens generated by `scripts/build-tokens.js` from `docs/redesign/tokens.json` as its sole source of color, typography, radius, and shadow values. The mini-program SHALL NOT introduce any new hardcoded hex color values in `*.wxss` or `*.wxml` files outside of the single hex-fallback in `app.json` (required because WeChat native nav bar does not support CSS variables). Any new component introduced in this change MUST use `var(--token-name)` references rather than literal color or typography values.

#### Scenario: New wxml rule uses token
- **WHEN** a developer adds a new wxml rule that needs accent color
- **THEN** the rule uses `var(--accent)` (or token-derived Tailwind class equivalent)
- **AND** the rule does not contain a literal hex value

#### Scenario: tokens.wxss is the only color source for app-level imports
- **WHEN** a developer inspects `frontend/app.wxss`
- **THEN** the first non-comment line is `@import '/shared/tokens/tokens.wxss';`
- **AND** no `@import` of any other color source exists
