## Context

Six independent cleanup items surfaced by `mp-od-prototype-alignment`'s final cross-screen review, plus one that review flagged as "worth considering" (shared order-action controller extraction). Full audit during this proposal's research widened item 2's scope beyond what the original note recorded, and the controller extraction turned out to intersect with an already-approved, unmet spec requirement (mp-08's "申请退款" action row contract).

## Goals / Non-Goals

**Goals:**
- Make every wxml `bindtap` target follow the `onXxx` convention this codebase otherwise uses consistently
- Bring `order-detail.wxss` in line with the BEM style every other screen's `.wxss` uses
- Collapse the three different address-card class-name variants into one
- Remove dead JSON config
- One shared money-rounding util instead of two divergent implementations
- One shared order-action dispatch implementation instead of two ~120-line near-duplicates, closing the compliance gap this duplication caused (mp-08 spec's refund requirement, unmet on the list screen)

**Non-Goals:**
- `order-confirm.js#selectAddress` (confirmed dead code — the wxml uses a plain `<navigator url="...">`, never calls this method) and `cart.js` never reading back `selectedAddressFromList` — both real, pre-existing functional gaps discovered during this proposal's research, logged in proposal.md as new legacy issues, not touched here
- Any visual/layout change beyond the specific renames and class consolidation listed — this is a naming/organization change, not a redesign
- Extending the order-action controller to cover anything beyond what `order-list.js`/`order-detail.js` already both implement (no new actions, no new states)

## Decisions

### D1: Back-button naming — `goBack` → `onBack` everywhere
`order-confirm.js`/`address-list.js` rename `goBack` to `onBack`; update the corresponding `bindtap="goBack"` in `order-confirm.wxml:22` and `address-list.wxml:28` to `bindtap="onBack"`. `order-list.js`/`order-detail.js`/`product-detail.js` already use `onBack` — no change there.

### D2: bindtap handler audit — full list of renames (wider than the original legacy note)
A full audit of every `.wxml` file's `bind`/`catchtap` targets (done during this proposal's research, not assumed from the old note) found bare-verb handlers beyond what was originally logged. Full rename list:

| File | Old name | New name | wxml reference(s) |
|---|---|---|---|
| `address-list.js` | `selectAddress` | `onSelectAddress` | `address-list.wxml:47` |
| `address-list.js` | `editAddress` | `onEditAddress` | `address-list.wxml:76` |
| `address-list.js` | `deleteAddress` | `onDeleteAddress` | `address-list.wxml:86` |
| `address-list.js` | `addNewAddress` | `onAddNewAddress` | `address-list.wxml:113` |
| `address-list.js` | `setDefaultAddress` | `onSetDefaultAddress` | `address-list.wxml:66` |
| `cart.js` | `selectAddress` | `onSelectAddress` | `cart.wxml:37` |
| `index.js` | `addToCart` | `onAddToCart` | `index.wxml` |
| `category.js` | `addToCart` | `onAddToCart` | `category.wxml` |
| `product-detail.js` | `goToProductDetail` | `onGoToProductDetail` | `product-detail.wxml:111` |

Every rename is a **paired** change: the `.js` method definition AND every `.wxml` `bindtap`/`catchtap` reference to it must change in the same commit — this codebase's single most common bug class is exactly a wxml/JS binding drifting apart (documented across `mp-01`/`mp-02`/`mp-03`/`mp-08` diagnoses in the archived `mp-od-prototype-alignment` change). Each rename needs a wxml-contract-style test (this codebase already has the pattern: `address-list-wxml-contract.test.js` scans a `.wxml` file's `bindtap="xxx"` targets and asserts `xxx` exists as a real method on the page config) — extend the existing contract test where one exists for a file, add one following that exact pattern where it doesn't (`cart.wxml`, `index.wxml`, `category.wxml`, `product-detail.wxml` do not currently have one).

Method calls from *within* JS (not from wxml) don't need renaming — e.g. internal helpers like `fetchOrders`, `computeTotals`, `refreshCart` are not wxml-bound and the `onXxx` convention doesn't apply to them (its purpose is signaling "this is a user-facing event handler," not a blanket rule for every method).

### D3: `order-detail.wxss` → BEM
Rename the file's flat/abbreviated classes to `order-detail-card__xxx` (or similar block name — implementer's call, pick one block name and apply it consistently) style, mirroring `order-list.wxss`'s `.order-card__xxx` pattern. This file is small (82 lines wxss / 92 lines wxml) — a full pass is tractable in one sitting. Purely cosmetic/organizational: CSS class renames carry zero functional risk (no `bindtap`/`data-*` involvement), only requires updating the matching `class="..."` attributes in `order-detail.wxml` in lockstep.

### D4: Address-card class consolidation — `cart-address` → `address-card`
`cart.wxml`/`cart.wxss`'s `cart-address` block (and its `__main`/`__head`/`__name`/`__phone`/`__detail`/`__empty`/`__placeholder`/`__arrow` elements) renames to `address-card` (and matching `__body`/`__head`/`__name`/`__phone`/`__detail`/`__empty`/`__placeholder`/`__arrow`), aligning with what `order-confirm.wxml` and `address-list.wxml` already use. Note `cart.wxml`'s structure doesn't have a nested `__body` wrapper the way `order-confirm.wxml`/`address-list.wxml` do (its `__main` sits directly under the block) — when renaming, decide whether to also restructure to match that nesting or just rename the block/most element names 1:1; either is acceptable, prefer whichever produces a smaller diff since this task's goal is naming consistency, not structural unification.

### D5: `navigationStyle: "custom"` — drop no-op fields
`order-confirm.json`/`address-list.json`: remove `navigationBarBackgroundColor` and `navigationBarTextStyle` (both are inert once `navigationStyle: "custom"` hides the native bar). Keep `navigationBarTitleText` — even under custom style, the compiled page's title metadata is still used by the OS task switcher / share previews, it's not purely inert like the color/text-style fields. Keep `navigationStyle: "custom"` itself, obviously.

### D6: Shared `roundYuan` util
New `frontend/utils/money.js` (matching this project's existing `frontend/utils/*.js` convention for small shared helpers, e.g. `featureflag.js`, `order-detail-derive.js` — not `src/shared/utils/`, which is TS-first and would need a `.ts` + a runtime shim for a one-function module, more ceremony than this warrants). Exports `roundYuan(amount)` — exact same implementation as `order-confirm.js`'s current local function (`Math.round(amount * 100) / 100`). `order-confirm.js` imports it instead of defining it locally; `cart.js`'s two `.toFixed(2)` call sites (`totalPrice`, `selectedPrice`) switch to `String(roundYuan(...))` — note `.toFixed(2)` returns a *string* already formatted to 2 decimals, while `roundYuan` returns a *number*; cart.js's display fields expect strings (used directly in wxml interpolation), so the call sites need `.toFixed(2)` re-applied to `roundYuan`'s output, or the util should offer a second `formatYuan(amount)` that returns the string form. **Decision: add both** — `roundYuan(amount): number` for order-confirm's use (feeds further arithmetic) and `formatYuan(amount): string` (`roundYuan(amount).toFixed(2)`) for cart.js's direct-to-display use. This avoids forcing order-confirm's arithmetic-then-round pipeline into string form and back.

### D7: Shared order-action controller
New `frontend/utils/order-actions.js` exporting a dispatch function taking
`(action, order, refresh)` — **`order` is the full order object, not just an id** (the
refund fix below needs `order.totalAmount`, and passing the whole object avoids adding a
second parameter later for any other action that needs another field). `order-detail.js`
already holds the full order at `this.data.order`. `order-list.js` currently only gets
`orderId` from `e.currentTarget.dataset.id` (`order-list.wxml:69,129` only bind `data-id`,
not the whole item) — its `onActionTap`/`handleAction` needs one extra line to look up the
full order from `this.data.orders` (or `filteredOrders`) by id before calling the shared
dispatch; this is a one-line `.find()`, not a wxml change. Beyond the parameter shape, the
function MUST:
- Own the single `switch (action) { case 'pay': ...; case 'cancelOrder': ...; }` dispatch body, the `confirmThenCancel`/`confirmThenDelete`/`handleRebuy` helper functions, and the try/catch 409/403/404 error-toast handling — currently duplicated near-verbatim between `order-list.js:146-283` and `order-detail.js:105-224`.
- Take the differences between the two call sites as parameters, not hardcode either page's specific behavior:
  - order-list refreshes via `this.fetchOrders()` after a successful action; order-detail refreshes via `this.refreshOrder()` (re-fetches the single order, not a list) — the shared controller takes a `refresh` callback.
  - order-detail's `orderId` comes from `this.data.order.id` (page holds the current order); order-list's comes from `e.currentTarget.dataset.id` (per-card in a list) — this is already resolved *before* calling into the shared dispatch (both pages' existing `onActionTap` already do this resolution), so the shared function just takes `orderId` as a parameter, not a page-context object it has to introspect.
  - order-detail's `viewTracking`/`requestRefund`/`afterSale` branches route to page-specific methods (`viewLogistics()`, `applyRefund()`) that do things the shared controller shouldn't own (clipboard copy, a modal specific to that page's layout) — keep these as callbacks the calling page still owns, OR fold the now-identical "confirm modal + call `OrderAPI.requestRefund`" logic into the shared controller (see below) and leave only `viewTracking`'s clipboard-copy as a page-owned callback (order-detail already implements it; order-list currently does `wx.navigateTo` to the detail page instead — these differ legitimately since order-list has no inline tracking UI to show, and that's fine, not a divergence to fix here).
- **Refund unification — corrected mid-research, both existing implementations are actually broken, not just one.** Original assumption was "order-detail's `applyRefund()` already does this correctly, just wire order-list to match it." That assumption was wrong: the backend `RefundRequest` DTO (`backend/src/main/java/com/seafood/order/api/dto/RefundRequest.java`) requires **both** `amount` (`@NotNull @DecimalMin("0.01")`) **and** `reason` — but `order-detail.js#applyRefund()`'s current `request({url: '.../refund', method: 'POST', data: {reason: '用户主动申请'}})` never sends `amount` at all. This would 400 against the real backend; it only appears to "work" because `order-detail.test.js`'s mock (`mockRequest.mockResolvedValueOnce(...)`) doesn't validate the request body — the exact same "test asserts too little, masks a real bug" pattern this whole engagement keeps finding. **Corrected design**: the shared controller's `requestRefund`/`afterSale` branch must call `orderStore.requestRefund(orderId, order.totalAmount, '用户主动申请')` — not a raw `request()`/`OrderAPI.requestRefund()` call. `orderStore.requestRefund` (`src/features/order/store.ts:113-131`) already exists, is already tested (`store.test.ts`: optimistic `status=REFUNDING` before the await, rollback on failure), and — critically — already forwards `amount` correctly to `OrderAPI.requestRefund(id, {amount, reason})`. Passing `order.totalAmount` as a full-refund default is the only amount either screen has ever collected from a user (neither has an amount-entry UI, and building one is out of scope for this task per its Non-Goals) — this fixes both screens to be genuinely spec-compliant and functional, not just "no longer divergent from a broken reference implementation."

Alternative considered: keep the two files' implementations separate and just fix the `err.status`/`err.statusCode`-class bugs individually as they're found. Rejected — that's the status quo that caused the previous change's bug in the first place (one copy fixed, the other silently not), and the user explicitly chose to fold this extraction into the current change specifically to address that root cause once.

## Risks / Trade-offs

- [Risk] D2's rename list touches `bindtap` targets across 6 files — the single highest-risk item in this change (silent dead-binding is this codebase's most common defect class) → Mitigation: pair every rename with a wxml-contract test extension/addition per file, run each affected page's existing test suite before AND after, treat any test file lacking contract coverage as a gap to close as part of this same task, not defer
- [Risk] D7's controller extraction is the largest, most architecturally significant item — a subtle behavior-preservation slip (like the previous change's Gap-2 existence-check narrowing) is plausible → Mitigation: TDD from both existing files' current test suites as the baseline — every existing `order-list.test.js`/`order-detail.test.js` action-dispatch test must still pass after the extraction (adjusted only where the refund placeholder test intentionally flips to the real-call assertion), not rewritten away
- [Risk] D6's cart.js `.toFixed(2)` → `formatYuan()` swap touches user-visible price text — a rounding-direction or type mismatch would be visible to real users → Mitigation: `formatYuan` is defined as exactly `roundYuan(amount).toFixed(2)`, i.e. strictly more precise than the old bare `.toFixed(2)` (adds explicit half-up rounding before the string conversion, `.toFixed()` alone already rounds so behavior is unchanged for all normal inputs — this is not expected to change any displayed value, just centralize the logic)

## Migration Plan

No data migration (frontend-only, no persisted state format changes). Each of the 7 decisions (D1-D7) is independently revertable — no shared migration state between them. Recommended implementation order: D5 (trivial, zero risk) → D4 (CSS-only) → D6 (small, isolated util) → D3 (CSS-only, larger but zero functional risk) → D1+D2 (JS rename, real risk, needs careful contract-test pairing) → D7 (largest, most architecturally significant, save for last once the codebase's other naming is already settled).
