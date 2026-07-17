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
The home page (`pages/index/index`) MUST render the following layout, in vertical scroll order, with each region visually matching the OD golden `frontend/e2e/od-golden/mp-01-home.png` to within a 5% perceptual-diff threshold (verified by `npm run test:visual mp-01-home`'s odiff comparison), and passing the geometry gate (`npm run test:geometry mp-01-home`) for structural invariants (region presence/count/columns):
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

### Requirement: Profile page (mp-05) OD-aligned layout
The profile page (`pages/profile/profile`) MUST render, in vertical order, matching the OD golden `frontend/e2e/od-golden/mp-05-profile.png` to within a 5% perceptual-diff threshold (`npm run test:visual mp-05-profile`) and passing the geometry gate (`npm run test:geometry mp-05-profile`):
- **User card**: avatar (`user-avatar`, falls back to `/images/default-avatar.png` when unauthenticated), nickname (or "点击登录" when unauthenticated), and a role badge shown only when `userInfo.role === 'MERCHANT'`.
- **Order status grid**: a 4-cell grid (待付款 / 待发货 / 待收货 / 已完成), each cell an emoji icon + label + optional count badge, navigating to `pages-sub/order/order-list/order-list?status={PENDING|PAID|SHIPPED|COMPLETED}`. A "查看全部" link above the grid navigates to the unfiltered order list.
- **Tools list**: rows for 收货地址 (→ `pages-sub/user/address/address-list`), 联系客服, 关于我们 — each an icon + label + trailing chevron (`›`).

The page SHALL consume v2 tokens only and SHALL NOT contain literal hex colors or v1 class names.

#### Scenario: Unauthenticated user sees login prompt
- **WHEN** the user is not authenticated
- **THEN** the user card shows the default avatar and "点击登录" in place of a nickname

#### Scenario: Status grid badge reflects pending count
- **WHEN** the user has 2 orders in `PENDING` status
- **THEN** the 待付款 cell shows a badge with "2"

#### Scenario: Tapping a status cell navigates to the filtered order list
- **WHEN** the user taps the 待发货 cell
- **THEN** the app navigates to `pages-sub/order/order-list/order-list?status=PAID`

---

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

---

### Requirement: Address management page (mp-07) OD-aligned layout
The address list page (`pages-sub/user/address/address-list`) MUST render, matching the OD golden `frontend/e2e/od-golden/mp-07-address.png` to within a 5% perceptual-diff threshold (`npm run test:visual mp-07-address`) and passing the geometry gate (`npm run test:geometry mp-07-address`):
- **Select-mode title**: "选择收货地址" shown only when `selectMode` is true (entered from mp-06's address card).
- **Address cards**: one per address, showing name/phone/full address, a "默认" badge when `isDefault`, 编辑/删除 action buttons, and — in select mode — a radio circle reflecting `selectedId === item.id`.
- **Action row layout**: the 设为默认/编辑/删除 action row MUST be laid out horizontally along the bottom of the card, separated from the address content above it by a dashed top border — NOT as a vertical sidebar along the card's side. Each action's icon and label MUST sit side by side on one line (not icon-above-label).
- **Empty state**: 📭 icon + "还没有收货地址哦" + "添加收货地址,方便下单收货" when the address list is empty.
- **Bottom add bar**: sticky "+ 添加新地址" button.

This page MUST successfully load the user's addresses via the backend address API (no 403/404 on a valid authenticated request); an auth or route failure that prevents the list from loading is a defect to fix as part of achieving this requirement, not an acceptable "empty" rendering.

#### Scenario: User has no saved addresses
- **WHEN** `addresses.length === 0`
- **THEN** the page shows the empty state illustration and copy, and the add bar remains visible

#### Scenario: Select mode reflects the currently-selected address
- **WHEN** the page opens with `selectMode=true` from mp-06
- **THEN** the "选择收货地址" title is shown
- **AND** each address card renders a radio circle, checked for the address matching `selectedId`

#### Scenario: Authenticated address list loads successfully
- **WHEN** an authenticated user navigates to mp-07
- **THEN** the backend address API returns 200 with the user's addresses (not 403/404)

#### Scenario: Action row renders as a horizontal bottom bar
- **WHEN** an address card renders its 设为默认/编辑/删除 actions
- **THEN** the three actions appear in a single horizontal row along the bottom of the card, below a dashed divider, each with its icon and label on the same line

---

### Requirement: Order list page (mp-08) OD-aligned layout
The order list page (`pages-sub/order/order-list/order-list`) MUST render, matching the OD golden `frontend/e2e/od-golden/mp-08-order-list.png` to within a 5% perceptual-diff threshold (`npm run test:visual mp-08-order-list`) and passing the geometry gate (`npm run test:geometry mp-08-order-list`):
- **Sticky header**: back button, "我的订单" title, search icon.
- **Tab pill row**: horizontal scroll (全部/待付款/待发货/待收货/已完成/已取消), active tab uses accent pill styling, each tab may show a count badge.
- **Order cards**: one per order — merchant/status line, item rows, order number + total, and the `OrderActionRow` component (per the existing `Order list and detail (mp-08) customer action row` requirement).
- **States**: loading (`shared-loading`), error (`shared-empty` with retry), empty (`shared-empty` "还没有相关订单哦" with "去逛逛" action).

This requirement covers page-level layout only; action-row button selection per order status remains governed by the `Order list and detail (mp-08) customer action row` requirement.

#### Scenario: Tab switch filters and updates counts
- **WHEN** the user taps the 待发货 tab
- **THEN** the order list re-filters to `PAID`-status orders
- **AND** the active tab pill switches to accent styling

#### Scenario: Empty filtered result shows the empty state
- **WHEN** the active tab's filtered order list is empty
- **THEN** the page shows `shared-empty` with "还没有相关订单哦" and a "去逛逛" action navigating to mp-01

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

---

### Requirement: Order detail page (mp-09) OD-aligned layout
The order detail page (`pages-sub/order/order-detail/order-detail`) MUST render, in vertical order, matching the OD golden `frontend/e2e/od-golden/mp-09-order-detail.png` to within a 5% perceptual-diff threshold (`npm run test:visual mp-09-order-detail`) and passing the geometry gate (`npm run test:geometry mp-09-order-detail`):
- **Status banner**: colored banner (`order-detail__banner--{{statusBanner.statusColor}}`) with the current status text and, when present, an estimated-delivery subtext.
- **Timeline card**: 物流轨迹 — one node per tracking event, each showing a label, time, and description.
- **Address card**: recipient info for the order.
- **Items card**: one row per line item (image, name, spec/quantity, unit price).
- **Price card**: 实付 total.
- **Bottom action bar**: sticky, rendering the applicable `OrderActionRow` actions per the order's `Order.status` (same button set as the `Order list and detail (mp-08) customer action row` requirement).

#### Scenario: Order with tracking events renders a populated timeline
- **WHEN** the order has shipped and carries tracking events
- **THEN** the timeline card renders one node per event in chronological order

#### Scenario: Status banner color matches order status
- **WHEN** the order status is `SHIPPED`
- **THEN** the status banner uses the color mapping defined for `SHIPPED` and shows the estimated-delivery subtext when available

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
