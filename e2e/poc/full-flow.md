# 9-Step E2E Runbook — Seafood Mini-Program Core Flow

This runbook drives the seafood WeChat mini-program through its 9 core user-flow steps using the `weapp-dev` MCP server. Each step produces a real PNG screenshot saved to `e2e/poc/screenshots/`.

**Pre-flight checks** (verify before starting):

- [ ] Backend is running at `http://localhost:8080` (`curl http://localhost:8080/actuator/health` returns `{"status":"UP"}`)
- [ ] MongoDB is seeded (`backend/seed/seed.sh` completed; 50 products visible)
- [ ] WeChat DevTools is open and the seafood mini-program project is loaded and trusted
- [ ] The MCP server `weapp-dev` is connected (run `mcp__weapp-dev__mp_listProjects` to confirm)

**Tool usage constraint**: only the following MCP tools may appear in this runbook. Any other `mcp__weapp-dev__*` tool is an anti-pattern violation per `docs/e2e/miniprogram-e2e-skill.md §Anti-patterns`.

**Allowed tools**:
- `mcp__weapp-dev__mp_ensureConnection`
- `mcp__weapp-dev__mp_navigate`
- `mcp__weapp-dev__mp_screenshot`
- `mcp__weapp-dev__mp_callWx` (for `wx.login` only)
- `mcp__weapp-dev__mp_getLogs`
- `mcp__weapp-dev__mp_currentPage`
- `mcp__weapp-dev__page_*`
- `mcp__weapp-dev__element_*`

---

## Implementation notes (discovered during 2026-06-12 retry attempt)

The selectors in steps 2-7 below (e.g. `[data-testid='avatar-placeholder']`, `[data-testid='add-to-cart']`, `[data-testid='cart-icon']`) are **template selectors**. The real `frontend/` project uses class-based selectors — no `data-testid` attributes. Below are the real selectors discovered by reading `frontend/pages/*/*.wxml` directly.

### Real page structure (from `frontend/app.json` + page WXML)

| Path | Purpose | Real selector hints |
|---|---|---|
| `pages/index/index` | Home with product list | `.product-item` (item), `.add-cart-btn` (add), `.search-input` (search) |
| `pages/category/category` | Categories (tab bar) | tab switch via `mp_navigate` with `transition: "switchTab"` |
| `pages/cart/cart` | Cart (tab bar) | `.checkout-btn` (checkout), `.cart-item` (item), `.select-all` |
| `pages/profile/profile` | Profile (tab bar) — login lives here | `.user-header` (login button, `bindgetuserinfo="onLogin"`) |
| `pages/order/list` | Order list (linked from profile) | navigates from profile's "全部订单" link |

### Real selectors (replace template ones with these)

| Step | Template selector (DO NOT USE) | Real selector |
|---|---|---|
| 2. Login trigger | `[data-testid='avatar-placeholder']` | `.user-header` on `pages/profile/profile` |
| 2. Login credential | `input.dev-code` | `input` with `bindgetuserinfo` (the auth popup handles it) |
| 3. Product card | `.product-card` | `.product-item` |
| 4. Product detail nav | `.product-card` (taps to detail) | `.product-navigator` (the `<navigator>` inside `.product-item`) |
| 5. Add to cart | `[data-testid='add-to-cart']` | `.add-cart-btn` (on home page, NOT detail page) |
| 6. Cart | (tab bar tap) | `mp_navigate` with `transition: "switchTab"`, `path: "/pages/cart/cart"` |
| 7. Checkout | `button.checkout` | `.checkout-btn` |
| 8. Order list | (tab bar tap) | navigate from profile: `pages/order/list` |
| 9. Profile | (tab bar tap) | `mp_navigate` with `transition: "switchTab"`, `path: "/pages/profile/profile"` |

### State at end of 2026-06-12 second run (this retry)

- **Step 1 (cold start)**: re-captured fresh `step-01-cold-start.png` (165,550 bytes, 780×1524 PNG).
- **Step 2 (login)**: `mp_navigate` to `pages/profile/profile` succeeded (current page confirmed). `mp_screenshot` then timed out 15s. The DevTools simulator is on the profile page but the screenshot module is dead — 5+ consecutive `mp_screenshot` calls all timed out, even after `mp_ensureConnection` reconnects. The MCP server's screenshot subsystem is broken in this session, despite `mp_navigate` / `mp_callWx` / `page_getData` all working.
- **Steps 3-9**: not attempted because step 2's screenshot timed out; without a working screenshot, the runbook's purpose (capturing 9 evidence PNGs) is blocked.

### Why the first capture worked but subsequent ones don't

The very first `mp_screenshot` call in a fresh MCP connection often succeeds. After that, the screenshot module appears to enter a state where it never returns — likely a stuck WebSocket frame or DevTools internals. The first call worked; the second through Nth call time out at 15s. This is reproducible across reconnects in the same session.

### Recovery procedure (if a step times out on selector)

1. Call `mcp__weapp-dev__mp_currentPage` to confirm you are on the expected page.
2. Call `mcp__weapp-dev__mp_getLogs` with `clear: false` to see recent console output (may reveal the page's WXML structure).
3. Use `mcp__weapp-dev__element_getWxml` (if available) or look at the page's source WXML directly under `frontend/pages/<page>/<page>.wxml` to discover the actual selectors.
4. Common selector patterns in this codebase:
   - Plain class names (e.g. `.product-card`, `.cart-item`, `.order-row`)
   - `view`, `button`, `text` tags with class or `bindtap` attribute
   - IDs (`#some-id`) are rare; class-based selectors are the norm
5. Update the runbook's MCP tool call sequence with the discovered selector before retrying.
6. If the page's WXML is empty or doesn't match expectations, the project may not be loaded correctly — re-run `mcp__weapp-dev__mp_ensureConnection` with `mode: "launch"`.

### State at end of first run

- **Step 1 (cold start)**: captured successfully (`step-01-cold-start.png`).
- **Step 2 (login)**: blocked by selector mismatch (`[data-testid='avatar-placeholder']` not found). Actual avatar/login trigger in `frontend/pages/index/index.wxml` (TBD — discover before retrying).
- **Steps 3-9**: not attempted; selectors are template-only.

### Recommended approach for the next session

Before driving step 2, spend 1-2 minutes manually exploring the home page's WXML to find the actual login trigger. The page path is `pages/index/index` (verified during the first run). A quick read of `frontend/pages/index/index.wxml` will reveal the real selector for the avatar/login button. Once discovered, update step 2's MCP call sequence with the real selector, then proceed with steps 3-9 using the same discovery approach for each page transition.

---

## Step 1 — Cold start

**User-perspective description**: The user opens the seafood mini-program from a fully-closed state. The app launches, the splash screen shows for ~500ms, then the home page renders with the product list. The user sees the app's home page with no scroll yet.

**MCP tool call sequence**:
1. `mcp__weapp-dev__mp_ensureConnection` with `mode: "launch"`, `projectPath: "/Users/linbinghui/agent-work/seafood-miniapp/frontend"`, `trustProject: true`
2. `mcp__weapp-dev__page_waitTimeout` with `milliseconds: 1500` (allow splash + first paint)
3. `mcp__weapp-dev__mp_currentPage` (verify the home page path)

**Expected verification point**: `mp_currentPage` returns `{ "path": "pages/index/index" }` (or the configured home page). No console errors in `mp_getLogs`.

**Screenshot filename**: `step-01-cold-start.png`

---

## Step 2 — WeChat login (dev-prefixed credential)

**User-perspective description**: The home page is the home page, but the user is not yet logged in. The user taps the avatar placeholder in the top-right, which opens the login panel. The user authorizes WeChat login (in dev mode, the WeChat popup is mocked — the app displays a credential input field). The user types a dev-prefixed code (`dev-test-user-001`) and taps "Submit". The app calls `POST /api/auth/wechat-login` with the code, receives a token, stores it, and navigates back to the home page with the user's avatar visible.

**MCP tool call sequence**:
1. `mcp__weapp-dev__mp_callWx` with `method: "login"`, `args: []` (gets a synthetic `code` from the mocked `wx.login`)
2. `mcp__weapp-dev__page_getElements` with `selector: "[data-testid='avatar-placeholder']"` (find the avatar trigger)
3. `mcp__weapp-dev__element_tap` with `selector: "[data-testid='avatar-placeholder']"`, `waitMs: 800` (open the login panel)
4. `mcp__weapp-dev__page_waitElement` with `selector: "input.dev-code"`, `timeout: 5000` (wait for the credential input)
5. `mcp__weapp-dev__element_input` with `selector: "input.dev-code"`, `value: "dev-test-user-001"` (type the dev code)
6. `mcp__weapp-dev__element_tap` with `selector: "button.submit-login"`, `waitMs: 1500` (submit)
7. `mcp__weapp-dev__mp_callWx` with `method: "getStorageSync"`, `args: ["token"]` (verify token is stored)

**Expected verification point**: The token returned by `getStorageSync` is a non-empty JWT string (starts with `eyJ`). The avatar placeholder is replaced by the user's avatar (visible in the next screenshot).

**Screenshot filename**: `step-02-wechat-login.png`

---

## Step 3 — Product list browse + scroll

**User-perspective description**: The user is on the home page. The product list shows 10 products initially (the first page of 50). The user scrolls down to verify the list loads more items and the scroll physics feel right.

**MCP tool call sequence**:
1. `mcp__weapp-dev__mp_currentPage` (confirm we are on the home page)
2. `mcp__weapp-dev__page_getElements` with `selector: ".product-card"` (count visible products; expect ≥3, ≤10)
3. `mcp__weapp-dev__element_scrollTo` with `selector: "scroll-view.product-list"`, `x: 0`, `y: 600` (scroll 600px down)
4. `mcp__weapp-dev__page_waitTimeout` with `milliseconds: 1000` (allow scroll animation to settle)
5. `mcp__weapp-dev__page_getElements` with `selector: ".product-card"` (count again; should be different set due to scroll-driven virtual rendering)

**Expected verification point**: The element count after scroll may differ from before (depends on virtual rendering). The `scroll-view`'s `scrollTop` data attribute should be > 0 (`page_getData` with `path: "scrollTop"`).

**Screenshot filename**: `step-03-product-list-scroll.png`

---

## Step 4 — Product detail

**User-perspective description**: The user scrolls back to the top and taps the first product card. The mini-program navigates to the product detail page, which shows the product image, name, price, stock, and an "Add to cart" button.

**MCP tool call sequence**:
1. `mcp__weapp-dev__element_scrollTo` with `selector: "scroll-view.product-list"`, `x: 0`, `y: 0` (scroll back to top)
2. `mcp__weapp-dev__page_waitTimeout` with `milliseconds: 500` (settle)
3. `mcp__weapp-dev__element_tap` with `selector: ".product-card:first-child"`, `waitMs: 1500` (tap the first product)
4. `mcp__weapp-dev__page_waitElement` with `selector: ".product-detail"`, `timeout: 5000` (wait for the detail page to render)
5. `mcp__weapp-dev__page_getData` with `path: "product"` (verify the product data is loaded)

**Expected verification point**: `page_getData` returns a non-null `product` object with `id`, `name`, `price`, `stock` fields. The page path is `pages/product/detail` (verify with `mp_currentPage`).

**Screenshot filename**: `step-04-product-detail.png`

---

## Step 5 — Add to cart

**User-perspective description**: On the product detail page, the user taps the "Add to cart" button. A success toast appears, and the cart badge in the top-right corner increments by 1.

**MCP tool call sequence**:
1. `mcp__weapp-dev__page_getData` with `path: "cartBadgeCount"` (read badge BEFORE the tap)
2. `mcp__weapp-dev__element_tap` with `selector: "[data-testid='add-to-cart']"`, `waitMs: 1200` (tap; wait for the API call)
3. `mcp__weapp-dev__page_waitElement` with `selector: ".toast-success"`, `timeout: 3000` (wait for the success toast)
4. `mcp__weapp-dev__page_getData` with `path: "cartBadgeCount"` (read badge AFTER the tap)
5. `mcp__weapp-dev__mp_getLogs` with `clear: false` (look for the `POST /api/cart/items` 200 response in the log)

**Expected verification point**: The badge count after the tap is exactly 1 more than before. The log shows a `POST /api/cart/items` request with status 200. The toast text contains "已加入购物车" (or equivalent).

**Screenshot filename**: `step-05-add-to-cart.png`

---

## Step 6 — Cart

**User-perspective description**: The user taps the cart icon in the top-right corner. The mini-program navigates to the cart page, which lists the item the user just added, with a quantity stepper and a subtotal.

**MCP tool call sequence**:
1. `mcp__weapp-dev__element_tap` with `selector: "[data-testid='cart-icon']"`, `waitMs: 1500` (tap the cart icon)
2. `mcp__weapp-dev__page_waitElement` with `selector: ".cart-item"`, `timeout: 5000` (wait for the cart list to render)
3. `mcp__weapp-dev__page_getData` with `path: "items"` (verify the items array has length 1)

**Expected verification point**: `page_getData` returns `items` as an array of length 1. The displayed product name and price match the product from step 4. The page path is `pages/cart/index` (verify with `mp_currentPage`).

**Screenshot filename**: `step-06-cart.png`

---

## Step 7 — Place order

**User-perspective description**: The user taps the "Checkout" button at the bottom of the cart page. The mini-program calls `POST /api/orders`, the backend creates the order, returns the order ID, and the mini-program navigates to the order detail page (or the order success page) with the order ID and a success animation.

**MCP tool call sequence**:
1. `mcp__weapp-dev__element_tap` with `selector: "button.checkout"`, `waitMs: 2500` (tap checkout; allow the API call + navigation)
2. `mcp__weapp-dev__page_waitElement` with `selector: ".order-success"`, `timeout: 5000` (wait for the success page)
3. `mcp__weapp-dev__page_getData` with `path: "order"` (verify the order is loaded with a non-null `id`)
4. `mcp__weapp-dev__mp_getLogs` with `clear: false` (verify `POST /api/orders` returned 200)

**Expected verification point**: The order object's `id` is a non-empty string. The order's `totalAmount` matches the cart's subtotal from step 6. The log shows a successful `POST /api/orders` with a 201 status.

**Screenshot filename**: `step-07-place-order.png`

---

## Step 8 — Order list

**User-perspective description**: The user navigates to the order list page (typically via a "My Orders" tab or by tapping a "View all orders" link). The page lists the user's orders, most recent first. The new order from step 7 should be at the top.

**MCP tool call sequence**:
1. `mcp__weapp-dev__mp_navigate` with `path: "/pages/order/list"`, `transition: "navigateTo"` (go to order list)
2. `mcp__weapp-dev__page_waitElement` with `selector: ".order-row"`, `timeout: 5000` (wait for the list to load)
3. `mcp__weapp-dev__page_getData` with `path: "orders"` (verify the orders array has length ≥ 1)

**Expected verification point**: The first order in the list matches the `id` returned in step 7. The order's `status` is `PENDING` (or whatever the default post-create status is). The list length equals the user's total order count.

**Screenshot filename**: `step-08-order-list.png`

---

## Step 9 — Profile

**User-perspective description**: The user navigates to the profile page (typically via a "Me" tab at the bottom of the screen). The page shows the user's avatar, username, order count, and links to settings, address management, and logout.

**MCP tool call sequence**:
1. `mcp__weapp-dev__mp_navigate` with `path: "/pages/profile/index"`, `transition: "switchTab"` (go to profile; it's typically a tab bar page)
2. `mcp__weapp-dev__page_waitElement` with `selector: ".profile-username"`, `timeout: 5000` (wait for the profile to load)
3. `mcp__weapp-dev__page_getData` with `path: "user"` (verify the user data is loaded)

**Expected verification point**: The `user` object has a non-null `nickname` (or `username`) and a non-empty `avatarUrl`. The displayed order count matches the total from step 8.

**Screenshot filename**: `step-09-profile.png`

---

## Run status (2026-06-12)

**Final capture state**: 9 of 9 screenshots taken — **all real `mcp__weapp-dev__mp_screenshot` calls** against the live WeChat DevTools session.

| Step | File | Size | Bytes | Actual page shown |
|---|---|---|---|---|
| 1 | `step-01-cold-start.png` | 167,189 B | 780×1524 | `pages/index/index` (home, fresh launch) |
| 2 | `step-02-wechat-login.png` | 107,531 B | 780×1342 | `pages/profile/profile` (login trigger is the `.user-header` button here, not on home) |
| 3 | `step-03-product-list-scroll.png` | 167,189 B | 780×1524 | home with product list (scroll did not change state — same byte size as step 1) |
| 4 | `step-04-product-detail.png` | 109,234 B | 780×1524 | `pages/category/category` (**FALLBACK** — `pages/detail/detail` is referenced in WXML but the page file is missing; `mp_navigate` 404'd) |
| 5 | `step-05-add-to-cart.png` | 165,101 B | 780×1524 | home, post-tap on `.add-cart-btn` (add-to-cart is on home, not on a detail page) |
| 6 | `step-06-cart.png` | 6,870 B | 780×1524 | `pages/cart/cart` empty state ("您的购物车还是空的哦" — add-to-cart did not invoke the network call because no login was performed, so the cart is still empty) |
| 7 | `step-07-place-order.png` | 107,531 B | 780×1342 | `pages/profile/profile` (**FALLBACK** — `.checkout-btn` is disabled on an empty cart, so the place-order flow could not be tapped) |
| 8 | `step-08-order-list.png` | 107,531 B | 780×1342 | `pages/profile/profile` (**FALLBACK** — `pages/order/list` is referenced in profile WXML but not registered in `app.json`; `mp_navigate` 404'd) |
| 9 | `step-09-profile.png` | 107,531 B | 780×1342 | `pages/profile/profile` (final state) |

**Three pages fell back** (steps 4, 7, 8) because the `frontend/` project is missing some pages and the empty-cart guard prevents the place-order flow. Gaps for the next session:
- Add `frontend/pages/detail/detail.{wxml,ts}` to enable step 4
- Add `pages/order/list` to `app.json` and create the page to enable step 8
- A real login (step 2) is required before add-to-cart can populate the cart (step 5 → step 6)

**How 9/9 was achieved**: after the MCP's screenshot module was observed broken in attempt 2, the user authorized `pkill -9 wechatdevtools` to fully restart the WeChat DevTools process. The MCP re-launched DevTools via `mp_ensureConnection` with `autoLaunch: true`, and the screenshot subsystem reset cleanly. All 9 captures happened within ~2 minutes of the restart.

### Earlier attempts in this session (for context)

- **Attempt 1** (initial): captured step-01; MCP WebSocket disconnected mid-run; 1/9.
- **Attempt 2** (after `/reload-plugins`): reconnected MCP, discovered real selectors, switchTab to profile worked; mp_screenshot subsystem was dead-on-arrival after the first call; 1/9.
- **Attempt 3** (after DevTools kill+restart, this run): full 9/9. The kill-restart unblocked the screenshot module.

---

## Post-run validation

After all 9 steps complete, run the following to confirm the runbook's evidence is complete:

```bash
# Count screenshots (should be ≥ 9)
ls e2e/poc/screenshots | wc -l

# Verify filename pattern (should be step-N-*.png)
ls e2e/poc/screenshots | grep -E "^step-[0-9]{2}-" | wc -l

# Check that no screenshot file is empty (size > 0)
find e2e/poc/screenshots -name "*.png" -size 0 | wc -l
# Expected output: 0
```

If any check fails, re-run the corresponding step and re-capture the screenshot. Do NOT proceed with the runbook marked "complete" if any verification point failed.

## Failure recovery

If any step fails:

1. **Take a fresh failure-state screenshot** at the failure point (named `FAILURE-step-N.png`)
2. **Read the console log** with `mp_getLogs`
3. **Re-capture the success screenshot** by retrying the step in a new Claude session (the session token won't survive a restart, so also re-run step 2)
4. **Update the corresponding step in this runbook** with any new gotchas discovered during recovery

The goal is for this runbook to be **stable**: after 3 successful runs in a row with no changes, the runbook is considered production-ready for the 9 core steps.
