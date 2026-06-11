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

**Current capture state**: 1 of 9 screenshots taken.

- ✅ `step-01-cold-start.png` (167,189 bytes, 780×1524 PNG) — captured at 2026-06-12 04:04 via `mcp__weapp-dev__mp_screenshot` against a live WeChat DevTools session. The page was `pages/index/index` (the home page), as expected for the cold-start state.
- ⏳ Steps 2-9: runbook is fully drafted (the 4-line structure for each step is in place below), but the remaining 8 screenshots were NOT captured in this run. The `weapp-dev` MCP server's WebSocket connection to `ws://localhost:9420` was lost after step 1, and the MCP server has since disconnected entirely. DevTools itself is still running on the host (process tree confirms PID 43828 is alive) but the MCP server can no longer be reached from this session.

**To complete this runbook**, re-run from a fresh session that has the `weapp-dev` MCP server registered. The runbook is the single source of truth for what to do at each step — no edits are needed, just re-execute.

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
