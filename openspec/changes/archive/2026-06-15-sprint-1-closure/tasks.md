## 1. Backend — Order state-transition table & unified entry

- [ ] 1.1 Add `OrderAction` enum (CANCEL, PAY, CONFIRM_RECEIVE, REBUY, REFUND, REMIND_SHIP) to `backend/src/main/java/com/seafood/order/domain/`
- [ ] 1.2 Add `OrderStatus.canTransitionTo(OrderStatus target)` static method + `Map<OrderAction, TransitionRule>` table (allowed-from set + target state) in `OrderStatus.java`
- [ ] 1.3 Refactor `OrderService` to add `transition(orderId, action, principal)` unified method that looks up the rule, calls the existing business logic (cancel/restock, pay/capture, etc.), increments the right `OrderMetrics` counter, and returns the updated `Order`
- [ ] 1.4 Wire the 4 customer-side controller endpoints (`POST /api/orders/{id}/{cancel|pay|confirm-receive|rebuy}`) to delegate to `OrderService.transition()`
- [ ] 1.5 Add `remind-ship` endpoint stub: `POST /api/orders/{id}/remind-ship` → no state change, increments `orders.remind_ship` counter
- [ ] 1.6 Add `OrderStatusTest` cases for 6 actions × 2 outcomes (allowed/denied) covering PENDING/PAID/SHIPPED/COMPLETED/CANCELLED/REFUNDING transitions
- [ ] 1.7 Add `OrderServiceTest` integration test that walks PENDING → PAID → SHIPPED → COMPLETED happy path with counter assertions at each step
- [ ] 1.8 Update `META-INF/native-image/` reflect-config for any new enums/records via `./gradlew nativeTest` agent (don't hand-write)

## 2. Backend — Admin login lockout (IP + account, 3/15min)

- [ ] 2.1 Add `login_attempts` MongoDB collection with TTL index on `ts` (900s) — `MongoIndexInitializer` config
- [ ] 2.2 Add `LoginAttemptRecord` infra document + `LoginAttemptRepository`
- [ ] 2.3 Add `LoginLockoutService` with methods `recordSuccess(ip, account)`, `recordFailure(ip, account)`, `isIpLocked(ip)`, `isAccountLocked(account)`, `getLockoutState(ip, account)`. Rolling-window: count failures in the last 15 minutes, lock if ≥ 3
- [ ] 2.4 Wire `AdminCookieAuthController.cookieLogin` to call `LoginLockoutService` before authentication — return 429 with `Retry-After: 900` and body `{ code: "AUTH_LOCKED", retryAfterSeconds: 900 }` if IP is locked; return 423 with `code: "ACCOUNT_LOCKED"` if account is locked
- [ ] 2.5 Increment `users.login.attempts{result=locked}` Micrometer counter on either lockout
- [ ] 2.6 Add `GET /api/auth/login-lock?phone={phone}&ip={ip}` endpoint (read-only stub, public) returning `{ locked, until, scope: "IP"|"ACCOUNT"|"NONE" }`
- [ ] 2.7 Add `LoginLockoutServiceTest` covering: 3-fail locks IP, 3-fail locks account, correct creds during lockout still 423, successful login clears counter, lock expires after window, lock status query returns correct shape
- [ ] 2.8 Add `AdminCookieAuthControllerTest` cases for 3-fail IP lockout (429 shape), 3-fail account lockout (423 shape), GET /login-lock returns correct `scope`

## 3. Frontend — Home page (mp-01) OD layout

- [ ] 3.1 Rewrite `frontend/pages/index/index.wxml` with `<swiper>` banner + `<scroll-view scroll-x>` chips + section header + 2-column product grid (preserve `index.js` data/logic)
- [ ] 3.2 Rewrite `frontend/pages/index/index.wxss` with v2 token references only (`var(--accent)`, `var(--surface)`, `var(--radius-pill)`, `font-display`, etc.); remove all v1 class names
- [ ] 3.3 Wire `<shared-empty>` for empty/filter-empty/error states; `<shared-loading>` for initial skeleton
- [ ] 3.4 Add `frontend/src/__tests__/home-page.test.ts` covering: chip active state toggle, banner indicator color, product grid 2-col layout (snapshot)
- [ ] 3.5 Static check: `grep -rE '(\.color-primary|\.color-text|\.bg-primary|\.card|\.btn|\.section-title)' frontend/pages/index/` returns no matches
- [ ] 3.6 Run `TZ=UTC npx jest src/__tests__/home-page.test.ts` — pass
- [ ] 3.7 E2E: add `frontend/e2e/mp-home.test.ts` (miniprogram-automator) — launch mp, screenshot home, save to `frontend/e2e/screenshots/mp-01-home-actual.png`

## 4. Frontend — Category page (mp-02) OD layout

- [ ] 4.1 Rewrite `frontend/pages/category/category.wxml` with left-rail category list (vertical) + top chips + right-pane 2-col grid
- [ ] 4.2 Rewrite `frontend/pages/category/category.wxss` — flex layout, v2 tokens only
- [ ] 4.3 Wire active category state (left-rail 4px accent border) + product refetch on tap
- [ ] 4.4 E2E: `frontend/e2e/mp-category.test.ts` — launch, switch category, screenshot 2nd category

## 5. Frontend — Product detail page (mp-03) OD layout

- [ ] 5.1 Rewrite `frontend/pages-sub/product/product-detail/product-detail.wxml` — image carousel + price block + title/desc + SKU placeholder row + stepper + 3-button bottom bar
- [ ] 5.2 Rewrite `frontend/pages-sub/product/product-detail/product-detail.wxss` — fixed bottom action bar, v2 tokens
- [ ] 5.3 Wire stepper cap at `Product.stock`; "已售罄" badge when stock = 0; both buttons disabled when stock = 0
- [ ] 5.4 Wire "立即购买" → navigate to mp-06 with `{ source: "direct_buy", items: [{ productId, quantity }] }`
- [ ] 5.5 Wire "加入购物车" → `POST /api/cart/items` + success toast + cart badge increment
- [ ] 5.6 E2E: `frontend/e2e/mp-product-detail.test.ts` — launch, screenshot, test stock=0 disabled state

## 6. Frontend — Cart page (mp-04) OD layout

- [ ] 6.1 Rewrite `frontend/pages/cart/cart.wxml` — header with count + "管理" toggle, cart list with checkbox + image + name + price + stepper, fixed bottom bar with 全选 + 合计 + 结算
- [ ] 6.2 Rewrite `frontend/pages/cart/cart.wxss` — flex layout, v2 tokens, fixed bottom bar
- [ ] 6.3 Wire master checkbox (all selected / partial / none), single-item toggle, 合计 recalculation, 结算 count
- [ ] 6.4 Wire empty state (`<shared-empty>` "购物车空空如也" + "去逛逛" button → mp-01)
- [ ] 6.5 Wire 结算 button → navigate to mp-06 with selected items
- [ ] 6.6 E2E: `frontend/e2e/mp-cart.test.ts` — launch, add 1 product, screenshot, toggle checkbox, verify 合计 updates

## 7. Frontend — Order list (mp-08) action row & state machine wiring

- [ ] 7.1 Refactor `frontend/src/features/order/components/OrderActionRow/index.ts` to a 6×5 status→actions matrix matching the spec scenarios (PENDING/PAID/SHIPPED/COMPLETED/CANCELLED/REFUNDING → 取消/付款/提醒发货/确认收货/再次购买/申请退款/查看物流/评价/退款处理中/删除)
- [ ] 7.2 Add API client methods in `frontend/src/features/order/api.ts` for the 6 state-transition endpoints (`cancel`, `pay`, `remind-ship`, `confirm-receive`, `rebuy`, `refund`) and `getTracking`
- [ ] 7.3 Wire action button taps: show loading state → call API → on 200 refresh the order → on 409 show toast "订单状态已变更" + refresh → on 403/404 show "订单不存在或无权限" toast
- [ ] 7.4 Rewrite `frontend/pages-sub/order/order-list/order-list.wxml` with the OD-aligned card layout (status badge, items list, action row at bottom)
- [ ] 7.5 Rewrite `frontend/pages-sub/order/order-list/order-list.wxss` — v2 tokens, card with status accent border
- [ ] 7.6 E2E: `frontend/e2e/mp-order-list.test.ts` — launch, screenshot, walk PENDING → cancel, then PENDING → pay, verify action row updates

## 8. Admin UI — Login page (ad-01) completion

- [ ] 8.1 Verify `LoginPage.tsx` uses `react-hook-form` + `zod` resolver with phone pattern `^1[3-9]\d{9}$` and password min 6 (PR #24 scaffold, complete as needed)
- [ ] 8.2 Add lockout UX: countdown timer, disabled submit button during lockout, message "请 X 分 Y 秒后再试" / "登录尝试次数过多,请 X 分钟后再试"
- [ ] 8.3 Wire `sessionStorage` persistence: write `admin-login-lockout` on 429/423, restore on mount if `until > Date.now()`
- [ ] 8.4 Add `LoginPage.test.tsx` cases for: phone validation, password validation, 401 inline error, 423 ACCOUNT_LOCKED message, 429 AUTH_LOCKED timer, sessionStorage restore
- [ ] 8.5 E2E: `admin-ui/e2e/admin-login.spec.ts` (Playwright) — fill valid creds → land on dashboard, fill invalid × 3 → 429 + disabled button + countdown

## 9. Admin UI — Dashboard (ad-02) completion

- [ ] 9.1 Complete `DashboardPage.tsx` with 4 KPI cards (今日订单 / 今日营收 / 待发货 / 库存预警) in 2×2 grid, using shadcn Card
- [ ] 9.2 Add Recharts `<LineChart>` for 7-day trend (`trend7d` payload, xAxis=date, yAxis=count)
- [ ] 9.3 Add "近期订单" `<DataTable>` (5 rows from `recentOrders` payload, each row navigates to `/orders/{id}`)
- [ ] 9.4 Add "低库存" `<DataTable>` (≤10 rows from `lowStock` payload, each row navigates to `/products/{id}`)
- [ ] 9.5 Wire skeleton loading state (pulsing rounded rects) and error state with retry button
- [ ] 9.6 Add `DashboardPage.test.tsx` cases for: 4 KPI render values, click 待发货 navigates, click 库存预警 navigates, error state with retry
- [ ] 9.7 E2E: `admin-ui/e2e/admin-dashboard.spec.ts` (Playwright) — login → dashboard → verify 4 KPI visible + Recharts SVG present + click a recent order

## 10. CI / coverage / native smoke verification

- [ ] 10.1 Run `cd frontend && TZ=UTC npx jest --coverage` — exit 0, coverage ≥ 88% for statements/branches/lines/functions
- [ ] 10.2 Run `cd admin-ui && npx vitest run` — exit 0
- [ ] 10.3 Run `cd admin-ui && npx vite build` — exit 0
- [ ] 10.4 Run `npm run build:tokens && npm run test:tokens` — exit 0
- [ ] 10.5 Run `cd backend && ./gradlew check` — exit 0 (covers the 14 new OrderState cases + LoginLockout cases + ArchUnit + checkNoRefreshScope)
- [ ] 10.6 Run `cd backend && ./gradlew nativeTest` to refresh `META-INF/native-image/` reflect-config for `OrderAction` enum + `LoginAttemptRecord` document
- [ ] 10.7 Run `cd backend && ./gradlew nativeCompile` — verify native binary still builds
- [ ] 10.8 Visual diff: 5 mp screens (mp-01/02/03/04/08) via miniprogram-automator, compare with `docs/redesign/mp-screenshots/design-ref/` high-res references using haiku+Read. Each screen visual diff ≤ 5%
- [ ] 10.9 Visual diff: 2 admin screens (ad-01 login, ad-02 dashboard) via Playwright, compare with `docs/redesign-requirements.md` § 3 descriptions. Each screen visual diff ≤ 5%

## 11. OpenSpec archive

- [ ] 11.1 Sync 4 delta specs to main: `admin-ui`, `auth`, `backend-api`, `mini-program` (re-run `openspec instructions sync-specs`)
- [ ] 11.2 Archive `openspec/changes/sprint-1-closure/` to `openspec/changes/archive/2026-06-16-sprint-1-closure/`
- [ ] 11.3 Update `openspec/specs/mini-program/spec.md` etc. to include the 9 new ADDED Requirements (after sync)

## 12. PR & deploy

- [ ] 12.1 Create branch `feat/sprint-1-closure` off main; logical commits (1 backend transitions, 2 backend lockout, 3-7 mp pages, 8-9 admin pages, 10 ci verification, 11 openspec, 12 docs)
- [ ] 12.2 Push branch + open PR with body listing 6 commits + Sprint 1 acceptance checklist (mp 4 屏 ≤5% / mp-08 5 状态 E2E / ad-01 3-fail lock / ad-02 4 KPI + 7d trend / coverage ≥ 88% / native compile)
- [ ] 12.3 Verify all CI checks pass (Jest, vitest, gradle check, native compile, Trivy, TruffleHog)
- [ ] 12.4 Merge PR → Sprint 1 truly complete
