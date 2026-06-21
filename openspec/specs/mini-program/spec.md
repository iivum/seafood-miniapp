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

---

### Requirement: Home page (mp-01) OD-aligned layout
The home page (`pages/index/index`) MUST render the following layout, in vertical scroll order, with each region visually matching `docs/redesign/mp-screenshots/design-ref/mp-01-home.png` to within a 5% visual-diff threshold (verified by miniprogram-automator screenshot + haiku image comparison):
- **Banner**: top-of-screen image carousel (`<swiper>`) showing 3 banner tiles, each with an emoji, title, and subtitle. Indicator dots use `var(--accent, #db633c)` for the active dot.
- **Category chips**: horizontal scroll (`<scroll-view scroll-x>`) of 5 chip pills (鱼类/虾蟹/贝类/软体/海藻), each with an emoji + label. Active chip uses `var(--accent)` background; inactive uses `var(--surface)` with a 1px border.
- **Section header**: "今日推荐" in `font-display` (Fraunces), with a subtitle in `font-body` (Inter Tight) reading "每日 10 款 · 限时优惠".
- **Product grid**: 2-column responsive grid (`display: grid; grid-template-columns: 1fr 1fr; gap: 16rpx`) of `ProductCard` components. Each card shows: image, name, price, and a circular "+" add-to-cart button.
- **States**: loading (skeleton grid), error (`Empty` component with retry), empty (after filter — `Empty` showing "该分类暂无商品" + "查看全部" button), load-more, no-more.

The page SHALL consume v2 tokens (`var(--accent)`, `var(--fg)`, `var(--bg)`, `var(--surface)`, `var(--muted)`, `var(--border)`, `var(--radius-pill)`, `var(--shadow-sm)`, `font-display`, `font-body`) and SHALL NOT use any v1 class names (`color-primary`, `color-text`, etc.).

#### Scenario: User opens home page with cached data
- **WHEN** the user navigates to mp-01 with valid cached products
- **THEN** the page renders 10 `ProductCard` items in a 2-column grid within 500ms of the page `onShow` event

#### Scenario: User opens home page with empty product list
- **WHEN** the API returns an empty `products` array
- **THEN** the page renders the empty state (`<shared-empty>` with "该分类暂无商品" message and "查看全部" button)

#### Scenario: Active category chip switches to accent
- **WHEN** the user taps a category chip
- **THEN** that chip's background becomes `var(--accent)` and the previously-active chip returns to `var(--surface)` with border

---

### Requirement: Category page (mp-02) OD-aligned layout
The category page (`pages/category/category`) MUST render:
- **Left rail**: vertical list of 5 category labels (one per category), each row ~80rpx tall, with the active label highlighted via a 4px left border in `var(--accent)` and `font-weight: 600`.
- **Top chips**: horizontal scroll of 2-3 sub-filter chips (e.g. "全部 / 当季 / 鲜活"), aligned with the right pane header.
- **Right pane**: 2-column product waterfall (`display: grid; grid-template-columns: 1fr 1fr; gap: 16rpx`) showing the products in the active category, reusing the `ProductCard` component.
- **Top bar**: page title in `font-display`, plus a search icon button on the right.

#### Scenario: User taps a different category label
- **WHEN** the user taps a category label in the left rail
- **THEN** the active highlight moves to the new label
- **AND** the right pane refetches `GET /api/products?category={id}` and re-renders the grid

#### Scenario: User opens category page fresh
- **WHEN** the user navigates to mp-02 from the home page
- **THEN** the first category is active by default
- **AND** the right pane shows its 10 products

---

### Requirement: Product detail page (mp-03) OD-aligned layout
The product detail page (`pages-sub/product/product-detail/product-detail`) MUST render, in vertical order:
- **Image carousel**: top 1/3 of the screen, `<swiper>` with 3-5 product images, indicator dots in `var(--accent)`.
- **Price block**: large price in `var(--accent)` (Fraunces display), strikethrough original price in `var(--muted)`, stock count in `var(--muted)`.
- **Title and description**: product name in `font-display`, description in `font-body`.
- **SKU placeholder** (Sprint 2 范围): a single row "规格 500g/份" with chevron, no SKU selection logic in Sprint 1.
- **Quantity stepper**: `-` and `+` buttons with the current quantity in between, capped at `Product.stock`.
- **3-button bottom bar**: fixed bottom action bar with "加入购物车" (secondary), "立即购买" (secondary), and a 收藏/心形 icon (tertiary). All buttons use `var(--radius-pill)` and tokens.

#### Scenario: User increases quantity to stock max
- **WHEN** the user taps `+` repeatedly
- **THEN** the stepper caps at `Product.stock` and the `+` button becomes disabled

#### Scenario: User taps "立即购买" with quantity 2
- **WHEN** the user taps the "立即购买" button
- **THEN** the app navigates to mp-06 with `{ source: "direct_buy", items: [{ productId, quantity: 2 }] }` (delegated to the existing `Direct buy from product detail` requirement)

#### Scenario: Stock = 0 disables 立即购买
- **WHEN** `Product.stock` is 0
- **THEN** both "加入购物车" and "立即购买" buttons are disabled
- **AND** a "已售罄" badge appears next to the price

---

### Requirement: Cart page (mp-04) OD-aligned layout
The cart page (`pages/cart/cart`) MUST render:
- **Header**: "购物车 (N)" title in `font-display`, with an "管理" button on the right (switches to delete mode).
- **Cart list**: each row is a `CartItemRow` with: a circular checkbox (filled in `var(--accent)` when selected), product image (96rpx), name + spec, unit price in `var(--accent)`, stepper (`-` `+`).
- **Bottom bar**: fixed bottom, "全选" checkbox on the left, "合计 ¥ {sum}" on the right (sum in `var(--accent)` `font-display`), "结算 ({N})" button with `var(--radius-pill)` in `var(--accent)` background.
- **Empty state**: when cart is empty, show `<shared-empty>` with "购物车空空如也" and a "去逛逛" button navigating to mp-01.

#### Scenario: User toggles a single item
- **WHEN** the user taps a cart item's checkbox
- **THEN** that item's checkbox state flips
- **AND** the "全选" master checkbox reflects the aggregate state (all selected / partial / none)
- **AND** the bottom "合计" recalculates to include only selected items
- **AND** the "结算 (N)" count updates

#### Scenario: User taps 结算 with empty selection
- **WHEN** the user taps the 结算 button with 0 items selected
- **THEN** the button is disabled (no-op on tap)

#### Scenario: User increases quantity
- **WHEN** the user taps the `+` on a cart row
- **THEN** the row's quantity increments
- **AND** the bottom 合计 and 结算 count update

---

### Requirement: Order list and detail (mp-08) customer action row
The order list (`pages-sub/order/order-list/order-list`) and order detail MUST render the `OrderActionRow` component below each order card. The action row's buttons MUST be selected according to the order's current `Order.status` per the `order-customer-state-machine` capability:
- `PENDING` → "取消订单" (calls `POST /api/orders/{id}/cancel`) + "立即付款" (calls `POST /api/orders/{id}/pay`)
- `PAID` → "提醒发货" (calls `POST /api/orders/{id}/remind-ship`) + "申请退款" (calls `POST /api/orders/{id}/refund`)
- `SHIPPED` → "查看物流" (navigates to tracking view) + "确认收货" (calls `POST /api/orders/{id}/confirm-receive`)
- `COMPLETED` → "评价" (placeholder toast "评价功能开发中") + "再次购买" (calls `POST /api/orders/{id}/rebuy`) + "申请售后" (calls `POST /api/orders/{id}/refund`)
- `REFUNDING` → "退款处理中" (no-op, disabled)
- `CANCELLED` → "删除" (local only, hides from list) + "再次购买" (calls `POST /api/orders/{id}/rebuy`)

Tapping any action button MUST show a loading state, call the corresponding endpoint, and on 200 refresh the affected order. On 409 (invalid state) the UI MUST show a toast with the error message and refresh the order to reflect the actual server state.

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

---

### Requirement: Real design-token parity for v2-rewritten pages
Any page rewritten in this change (`pages/index`, `pages/category`, `pages/cart`, `pages-sub/product/product-detail`, `pages-sub/order/order-list`, `pages-sub/order/order-confirm`) MUST use only v2 token references (`var(--accent)`, `var(--fg)`, `var(--bg)`, `var(--surface)`, `var(--muted)`, `var(--border)`, `var(--radius-pill)`, `var(--shadow-sm)`, `font-display`, `font-body`) in their `*.wxss` files. The page MUST NOT contain any literal hex color (other than the single fallback in `var(--accent, #db633c)` style fallbacks) or v1 class names (`color-primary`, `color-text`, `bg-primary`, `card`, `btn`, `section-title`).

#### Scenario: Static check finds no v1 class names in rewritten pages
- **WHEN** the CI scan runs `grep -E '(\.color-primary|\.color-text|\.bg-primary|\.card|\.btn|\.section-title)' frontend/pages/index/index.wxss frontend/pages/category/category.wxss frontend/pages/cart/cart.wxss`
- **THEN** the command exits 1 (no matches)

#### Scenario: All color values in rewritten pages are v2 token references
- **WHEN** a developer greps `frontend/pages/index/index.wxss` for hex literals
- **THEN** the only matches are inside `var(--token, #fallback)` patterns (max 1 fallback per token)
- **AND** no bare hex like `#FF6B6B` (the v1 primary) appears

---
