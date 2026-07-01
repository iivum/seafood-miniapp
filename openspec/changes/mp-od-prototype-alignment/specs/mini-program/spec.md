## MODIFIED Requirements

### Requirement: Home page (mp-01) OD-aligned layout
The home page (`pages/index/index`) MUST render the following layout, in vertical scroll order, with each region visually matching the OD golden `frontend/e2e/od-golden/mp-01-home.png` to within a 5% perceptual-diff threshold (verified by `npm run test:visual mp-01-home`'s odiff comparison), and passing the geometry gate (`npm run test:geometry mp-01-home`) for structural invariants (region presence/count/columns):
- **Banner**: top-of-screen image carousel (`<swiper>`) showing 3 banner tiles, each with an emoji, title, and subtitle. Indicator dots use `var(--accent, #db633c)` for the active dot.
- **Category chips**: horizontal scroll (`<scroll-view scroll-x>`) of 5 chip pills (鱼类/虾蟹/贝类/软体/海藻), each with an emoji + label. Active chip uses `var(--accent)` background; inactive uses `var(--surface)` with a 1px border.
- **Section header**: "今日推荐" in `font-display` (Fraunces), with a subtitle in `font-body` (Inter Tight) reading "每日 10 款 · 限时优惠"。
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

## ADDED Requirements

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
- **Summary card**: 商品总额 / 运费 / 优惠 / 实付 rows, 实付 emphasized in `var(--accent)` `font-display`.
- **Bottom bar**: sticky, shows the running total and a submit button that is disabled while `cartItems.length === 0` or a submission is in flight.

This page MUST NOT render with an empty `cartItems` list when reached via the normal checkout flow (cart → confirm) or the direct-buy flow (product detail → confirm, per the `Direct buy from product detail` requirement); an empty items card on entry indicates a state-passing bug, not a valid empty state.

#### Scenario: Direct-buy entry pre-loads the single product
- **WHEN** the user arrives at mp-06 via "立即购买" with `{ source: "direct_buy", items: [{ productId, quantity: 2 }] }`
- **THEN** the items card shows exactly that product at quantity 2, not an empty list

#### Scenario: Selecting a delivery method updates the summary
- **WHEN** the user taps "顺丰速运" (¥12)
- **THEN** the delivery card shows it as selected
- **AND** the summary card's 运费 row and 实付 total update to include the ¥12 fee

#### Scenario: Submit is disabled with no items
- **WHEN** `cartItems.length === 0`
- **THEN** the bottom bar's submit button carries the `is-disabled` class and taps are a no-op

---

### Requirement: Address management page (mp-07) OD-aligned layout
The address list page (`pages-sub/user/address/address-list`) MUST render, matching the OD golden `frontend/e2e/od-golden/mp-07-address.png` to within a 5% perceptual-diff threshold (`npm run test:visual mp-07-address`) and passing the geometry gate (`npm run test:geometry mp-07-address`):
- **Select-mode title**: "选择收货地址" shown only when `selectMode` is true (entered from mp-06's address card).
- **Address cards**: one per address, showing name/phone/full address, a "默认" badge when `isDefault`, 编辑/删除 action buttons, and — in select mode — a radio circle reflecting `selectedId === item.id`.
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

### Requirement: Order detail page (mp-09) OD-aligned layout
The order detail page (`pages-sub/order/order-detail/order-detail`) MUST render, in vertical order, matching the OD golden `frontend/e2e/od-golden/mp-09-order-detail.png` to within a 5% perceptual-diff threshold (`npm run test:visual mp-09-order-detail`) and passing the geometry gate (`npm run test:geometry mp-09-order-detail`):
- **Status banner**: colored banner (`status-banner--{{statusBanner.statusColor}}`) with the current status text and, when present, an estimated-delivery subtext.
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
