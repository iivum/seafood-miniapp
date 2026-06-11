---
name: miniprogram-e2e
description: Write, debug, and walk through end-to-end tests for the seafood WeChat mini-program frontend using the weapp-dev MCP server (which wraps miniprogram-automator). Covers 9 core user-flow steps from cold start to profile, with each operation mapped to a specific MCP tool, anti-patterns to avoid, and a runbook template.
when-to-use: miniprogram E2E, miniprogram-automator authoring, walk through core flow, WeChat mini-program E2E test, write WeChat E2E, drive miniprogram in CI
---

# Miniprogram E2E Skill Guide

This document is a **runnable guide**, not just a reference. It pairs with the [`e2e/poc/full-flow.md`](../../e2e/poc/full-flow.md) runbook and the [`e2e/poc/screenshots/`](../../e2e/poc/screenshots/) artifact directory. Together they form a complete, reproducible E2E walkthrough of the seafood mini-program's core user journey.

## When to use this guide

Use it whenever you are:

- Authoring a new E2E test for the mini-program (login flow, product browse, cart, checkout, order management, profile)
- Debugging a flaky E2E test that previously passed
- Onboarding a new engineer to the `weapp-dev` MCP toolset
- Converting a hand-tested flow into an automated script
- Investigating why a screenshot looks wrong but the assertion passed

Do **not** use it for:

- Backend API testing (use the Spring Boot integration tests in `backend/src/test/`)
- Frontend unit/component tests (use Jest in `frontend/src/`)
- Admin UI testing (use Playwright, not weapp-dev)
- Performance / load testing (separate concern, not in scope)

## Prerequisites

Before you start, confirm:

1. **WeChat DevTools is installed and logged in** on the local machine. The `mcp__weapp-dev__*` MCP server launches DevTools under the hood; without a logged-in DevTools, `mp_ensureConnection` will fail.
2. **The seafood mini-program project is trusted in DevTools** — open it once manually, click "Trust", close. Subsequent automation launches work without prompts.
3. **The backend is running locally** at `http://localhost:8080` (or set `WECHAT_ENABLED=false` and run `npm run dev` in the frontend root to hit the dev-mode auth bypass). The mini-program must reach the backend for any non-mock API call.
4. **MongoDB is seeded** with the standard fixture (`backend/seed/seed.sh`). Without seed data, the product list and order history will be empty.
5. **`mcp__weapp-dev__*` tools are visible** in your Claude Code tool list. If absent, check `~/.claude/settings.json` for the MCP server registration.

## Tool landscape

The `weapp-dev` MCP server exposes roughly 30 tools. They fall into 5 categories:

| Category | Tools | Purpose |
|---|---|---|
| **Connection** | `mp_ensureConnection`, `mp_listProjects`, `mp_setDefaultProject` | Lifecycle: launch / reconnect / project selection |
| **Navigation** | `mp_navigate`, `mp_callWx` | Cross-page jumps and low-level wx API calls |
| **Inspection** | `mp_currentPage`, `mp_screenshot`, `mp_getLogs`, `page_getData`, `page_getElements` | Read the current state |
| **Interaction** | `element_tap`, `element_input`, `element_scrollTo`, `page_waitElement`, `page_waitTimeout` | Drive user actions |
| **Mutation (DO NOT USE in runbook)** | `page_setData`, `mp_mockWxMethod`, `element_setData` | Bypass the user; explicitly anti-pattern (see §Anti-patterns) |

The Operations Catalog (§Operations Catalog) below covers only the first four categories — those are the tools you should actually call. The fifth category is listed for completeness so you know what to avoid.

---

## Operations Catalog

The catalog below maps **user intents** to **specific MCP tool calls**. Each entry includes the intent, the tool, a minimal JSON call example, and where the resulting screenshot should land in [`e2e/poc/screenshots/`](../../e2e/poc/screenshots/).

### 1. Launch the mini-program (cold start)

| Column | Value |
|---|---|
| **Intent** | Bring the app from a fully-closed state to its first interactive page |
| **MCP tool** | `mcp__weapp-dev__mp_ensureConnection` |
| **Minimal JSON call** | `{ "connection": { "mode": "launch", "projectPath": "/Users/linbinghui/agent-work/seafood-miniapp/frontend" } }` |
| **Screenshot location** | `e2e/poc/screenshots/step-01-cold-start.png` |

**Details:**

- The `mode` field is either `launch` (cold start) or `connect` (attach to an already-running DevTools session). Use `launch` for the first step of every runbook.
- The `projectPath` must point to the directory containing `project.config.json` (the mini-program project root, not the repo root).
- On success, the tool returns a confirmation message and the project enters the foreground of DevTools. The first page that renders is typically the splash, then the home page after ~500ms.
- **Verification point**: `mp_currentPage` should return `{ path: "pages/index/index" }` (or whatever the configured home page is) within 3 seconds.

**Common failure modes:**

- `project.config.json` missing → "project config not found"; double-check the path.
- DevTools not logged in → "automation service unavailable"; log in manually first.
- Trusted-project prompt → call `mp_ensureConnection` with `trustProject: true` to skip.

### 2. Tap a button (most common user action)

| Column | Value |
|---|---|
| **Intent** | User taps a clickable element (button, list item, tab, icon) |
| **MCP tool** | `mcp__weapp-dev__element_tap` |
| **Minimal JSON call** | `{ "selector": "button.add-to-cart", "waitMs": 800 }` |
| **Screenshot location** | `e2e/poc/screenshots/step-05-add-to-cart.png` (after the tap) |

**Details:**

- `selector` accepts any valid WXSS selector: tag, class, id, attribute, or compound. Prefer semantic selectors (`[data-testid="add-to-cart"]`) over position-based ones (`.btn:nth-child(2)`) — the latter break when DOM order shifts.
- `waitMs` is the delay after the tap before returning. Set this generously (500-1500ms) for any action that triggers a network call; a 200ms wait followed by a screenshot will catch the loading spinner, not the resulting state.
- **Verification point**: after the tap, the page should navigate or the UI should update. Use `page_getData` or `mp_currentPage` to assert the new state before proceeding.
- For elements inside a custom component, use the two-level `selector` + `innerSelector` form: `{ "selector": "#product-card", "innerSelector": "button.buy" }`.

**Common failure modes:**

- Element not visible (off-screen, behind modal) → call `element_scrollTo` first to bring it into view, then tap.
- Selector matches multiple elements → tap fails with "ambiguous selector"; disambiguate with `[index=N]`.
- Tap registered but no effect → check the element's `bindtap` handler; some components swallow taps during transitions.

### 3. Input text into a field

| Column | Value |
|---|---|
| **Intent** | User types into an input / textarea / search box |
| **MCP tool** | `mcp__weapp-dev__element_input` |
| **Minimal JSON call** | `{ "selector": "input.search", "value": "fish" }` |
| **Screenshot location** | `e2e/poc/screenshots/step-02-login-credential.png` (after typing the dev- prefixed code) |

**Details:**

- `value` is the exact text to type, character by character. The MCP server uses WeChat's input event under the hood, so component-level bindings (e.g. controlled inputs) update correctly.
- For the dev login flow, the value **must** start with `dev-` (e.g. `dev-test-user-001`). The backend's `AuthService` only accepts dev-prefixed codes when `WECHAT_ENABLED=false`.
- After input, the field's `value` attribute updates, and any `bindinput` / `bindchange` handlers fire. Use `page_getData` to confirm the new value is reflected in the page's data.
- **Verification point**: the input element's bound state should equal the typed value, and any downstream validation (e.g. login submit button enabled state) should reflect that.

**Common failure modes:**

- Input is a `password` type → typing works, but the value attribute is masked; use `page_getData` instead of reading the DOM attribute.
- IME composition in progress (Chinese / Japanese input) → wait for composition end with `page_waitTimeout` before asserting.
- Element is `disabled` or `readonly` → input fails silently; check the element's attributes first.

### 4. Scroll a list (vertical scroll, pull-to-refresh, horizontal swiper)

| Column | Value |
|---|---|
| **Intent** | User scrolls a `scroll-view`, list page, or swiper |
| **MCP tool** | `mcp__weapp-dev__element_scrollTo` (for `scroll-view`); `mcp__weapp-dev__page_callMethod` with `wx.pageScrollTo` for page-level scroll |
| **Minimal JSON call** | `{ "selector": "scroll-view.product-list", "x": 0, "y": 600 }` |
| **Screenshot location** | `e2e/poc/screenshots/step-03-product-list-scroll.png` |

**Details:**

- `element_scrollTo` works only on `<scroll-view>` components (per the tool's constraint). For page-level scrolling, call `mp_callWx` with `wx.pageScrollTo({ scrollTop: 600 })` — but note this is `page_scrollTo` semantics, not user-finger swipe.
- For a true user-finger swipe (e.g. to trigger pull-to-refresh or test inertia), use `mp_callWx` with `wx.createSelectorQuery().selectViewport().boundingClientRect()` followed by a manual `touchstart` / `touchmove` / `touchend` event sequence via `page_evaluate`. This is the only path that exercises scroll physics; use it when testing pull-to-refresh or sticky-header behavior.
- Coordinates are in CSS pixels relative to the scroll container's top-left, not the viewport. A `y` of 600 scrolls 600px down from the current position.
- **Verification point**: after scrolling, the visible items change. Compare `page_getElements` results before/after to confirm.

**Common failure modes:**

- Selector points to a non-scroll-view element → tool returns "not a scroll-view"; use `mp_callWx` fallback for page-level scroll.
- Scroll position resets on data load → re-call `element_scrollTo` after the data fetch completes.
- Skyline renderer → scroll APIs differ slightly; check the project's renderer mode in `app.json` (`renderer: "skyline"` vs `webview`).

### 5. Wait for an element to appear (explicit waits)

| Column | Value |
|---|---|
| **Intent** | User (or test) pauses until a specific element is rendered |
| **MCP tool** | `mcp__weapp-dev__page_waitElement` |
| **Minimal JSON call** | `{ "selector": ".product-card", "timeout": 5000, "retryInterval": 200 }` |
| **Screenshot location** | (used inline before any screenshot that depends on async data) |

**Details:**

- `timeout` is the max wait in milliseconds; default is 5000. Set this to 10000+ for elements that depend on a network fetch (e.g. product detail loading from `/api/products/{id}`).
- `retryInterval` controls polling frequency; default 200ms is fine for most cases. Lower it (50-100ms) for fast-spinning loading indicators, raise it (500-1000ms) for elements that take >2s.
- **Why explicit waits matter**: without them, an `element_tap` on a not-yet-rendered element silently fails. The `page_waitElement` call is the synchronization point between async data and user action.
- **Verification point**: the call returns the matched element (or throws on timeout). The throw is loud — treat it as a test failure, not a retry cue.
- For WeChat-specific waits (e.g. network request completion), use `mp_getLogs` to look for the request's response log line, then proceed.

**Common failure modes:**

- Selector typo or wrong DOM structure → element never appears; timeout fires. Add a `mp_screenshot` just before the wait to capture the "stuck" state for diagnosis.
- Element is conditionally rendered behind a `wx:if` → wait for the parent condition to flip first; use a parent selector.
- Race between data load and re-render → increase timeout; do not just retry.

### 6. Take a screenshot (visual evidence)

| Column | Value |
|---|---|
| **Intent** | Capture the current viewport for evidence / debugging / diff |
| **MCP tool** | `mcp__weapp-dev__mp_screenshot` |
| **Minimal JSON call** | `{ "path": "e2e/poc/screenshots/step-04-product-detail.png" }` |
| **Screenshot location** | (the path you pass) |

**Details:**

- The `path` is relative to the project root or absolute. Always use a **fresh filename** for each step (e.g. `step-N-action.png`) — never reuse a filename from a previous run, because the previous PNG will be overwritten and you lose the "before/after" comparison.
- The screenshot captures the current viewport only, not the full page. For a full-page capture, scroll to the top first, then call `mp_screenshot` for each section.
- Format is always PNG; quality is fixed. If you need a smaller file (e.g. for embedding in a PR), post-process with `sips -Z 1200 path.png` on macOS.
- **Verification point**: the file exists at the specified path and `file path.png` reports a valid PNG (`PNG image data, ...`).
- **Anti-pattern reminder**: never reuse a previous run's screenshot. If the test failed and you want to "see what it looked like", take a new screenshot at the failure point — that's the actual evidence.

**Common failure modes:**

- Path directory doesn't exist → mkdir first; the tool does not auto-create directories.
- DevTools not in foreground → screenshot is blank/black; bring DevTools to focus, then retry.
- Skyline renderer's `mp_screenshot` has known issues with certain `<scroll-view>` modes; if a screenshot looks corrupted, fall back to `mp_getLogs` + `page_getElements` for evidence.

### 7. Cross-page navigate (programmatic page jump)

| Column | Value |
|---|---|
| **Intent** | User taps a link / button that navigates to a new page |
| **MCP tool** | `mcp__weapp-dev__mp_navigate` |
| **Minimal JSON call** | `{ "path": "/pages/cart/index", "query": { "from": "pdp" }, "transition": "navigateTo" }` |
| **Screenshot location** | `e2e/poc/screenshots/step-06-cart.png` |

**Details:**

- `transition` accepts `navigateTo` (push), `redirectTo` (replace), `reLaunch` (full reset), `switchTab` (tab bar), or `navigateBack`. Pick the one that matches what the user is doing, not what's shortest for the test.
- `query` parameters are passed as an object; they appear in `onLoad(options)` on the target page.
- Prefer `element_tap` on the actual link/button for tests that exercise the user flow. Use `mp_navigate` only when (a) the target page has no in-app link, (b) you need to test the target page in isolation, or (c) you are recovering from a flaky state.
- **Verification point**: after navigation, `mp_currentPage` returns the new path; the new page's `onLoad` should have fired (check `page_getData` for any expected initial state).

**Common failure modes:**

- `switchTab` to a non-tab-bar page → "not a tabBar page" error; check `app.json` `tabBar.list`.
- Page not registered in `app.json` `pages` array → navigation fails; register it first.
- `navigateTo` hits the 10-page stack limit → use `redirectTo` or `reLaunch` instead.

### 8. WeChat login (mock credential flow)

| Column | Value |
|---|---|
| **Intent** | User logs in via the WeChat login panel (dev mode: mock credential) |
| **MCP tool** | `mcp__weapp-dev__mp_callWx` (for `wx.login`) + `mcp__weapp-dev__element_input` (for credential typing) |
| **Minimal JSON call (login)** | `{ "method": "login", "args": [] }` |
| **Minimal JSON call (credential)** | `{ "selector": "input.dev-code", "value": "dev-test-user-001" }` |
| **Screenshot location** | `e2e/poc/screenshots/step-02-wechat-login.png` |

**Details:**

- In dev mode (`WECHAT_ENABLED=false`), the mini-program accepts any code starting with `dev-` as a valid login. The backend's `AuthService` short-circuits the WeChat code-to-openid exchange and creates a session for the dev user.
- The flow has two parts: (1) call `wx.login` to get a `code`, (2) submit the code to `POST /api/auth/wechat-login`. In dev mode, the code can be synthetic.
- **Do NOT skip this step** in the runbook. Even if "the test doesn't care about auth", every other step depends on the session token. A flaky login breaks everything downstream.
- The credential input field is rendered conditionally — wait for it with `page_waitElement` before calling `element_input`.
- **Verification point**: after login, the home page renders the user's avatar / username (not the "please log in" placeholder). Also check `wx.getStorageSync('token')` returns a non-empty string.

**Common failure modes:**

- Code doesn't start with `dev-` → backend returns 400 "invalid code"; use a fixed `dev-test-user-001` to avoid drift.
- `wx.login` returns a real WeChat code (not dev-prefixed) in production builds → ensure you're testing against a dev build (`WECHAT_ENABLED=false`).
- Network error during login → backend not running; check `localhost:8080/actuator/health` first.

---

## Anti-patterns

The following practices look reasonable but produce tests that pass even when the app is broken, or that mask real user-facing bugs. **Do not use them in the runbook or in any committed E2E test.**

### Anti-pattern 1: Substituting `mp_callWx` for `element_tap`

| Column | Explanation |
|---|---|
| **Wrong practice** | Use `mcp__weapp-dev__mp_callWx` to invoke a button's `bindtap` handler directly, bypassing the actual tap event |
| **Why it's bad** | A real user tap fires a `tap` event that includes touchstart/touchend sequencing, hit-testing, and event bubbling. Calling the handler directly skips all of these. A bug in the hit-test region (e.g. a modal overlay absorbing the tap) is invisible to the test |
| **Right practice** | Always use `mcp__weapp-dev__element_tap` with a CSS selector. The test then exercises the same code path a user would |

**Example of the wrong way:**

```json
{ "method": "addToCart", "args": [{ "productId": "p-001" }] }
```

**Right way:**

```json
{ "selector": "[data-testid='add-to-cart-p-001']", "waitMs": 800 }
```

### Anti-pattern 2: Substituting `page_setData` for a real user action

| Column | Explanation |
|---|---|
| **Wrong practice** | Use `mcp__weapp-dev__page_setData` to mutate page data directly, simulating the result of a user action |
| **Why it's bad** | `setData` is a private API of the page lifecycle. Real users never call it. A test that bypasses user actions tests nothing about the user experience — it only tests that the page can render the post-action state, which unit tests already cover |
| **Right practice** | Drive the user action that *causes* the data change. For example, to test "cart badge increments on add", tap the "Add to cart" button (which calls `page_setData` internally) and then read the badge value |

### Anti-pattern 3: Substituting `mp_mockWxMethod` for real API calls

| Column | Explanation |
|---|---|
| **Wrong practice** | Use `mcp__weapp-dev__mp_mockWxMethod` to mock `wx.request` responses, making the app think the backend returned data without actually calling it |
| **Why it's bad** | E2E tests are supposed to verify the full stack: frontend + backend + database. Mocking the network layer reduces the test to a frontend unit test, and any backend regression (auth, validation, business logic) is invisible. Mocking also hides CORS, TLS, and serialization issues |
| **Right practice** | Run the real backend locally. If a specific endpoint is too slow or flaky, mock only that endpoint's *response body* (using a backend test fixture or a request interceptor in test mode), never the network layer itself |

**Note on legitimate uses of `mp_mockWxMethod`**: it has one valid use — mocking `wx.login` to return a synthetic `dev-` code without actually contacting WeChat. The backend's dev-mode auth path then accepts it. Even this is rare; most tests should just call `element_input` to type the dev code directly.

### Anti-pattern 4: Reusing screenshots from previous runs

| Column | Explanation |
|---|---|
| **Wrong practice** | Copy a screenshot from a previous test run into the current run's evidence directory, or compare new screenshots against a known-good "golden" image without regenerating it |
| **Why it's bad** | Reused screenshots cannot reflect the current state of the app. If the test "passes" because the old screenshot looked right, the test is a lie. Golden image comparison has its place (visual regression), but it must be intentional and the golden must be regenerated when the design changes |
| **Right practice** | Take a fresh `mcp__weapp-dev__mp_screenshot` at every verification point. If you want a golden image, store it in a separate `e2e/golden/` directory and regenerate it explicitly via a `npm run e2e:update-golden` script |

### Anti-pattern 5: Skipping the login state

| Column | Explanation |
|---|---|
| **Wrong practice** | Begin the runbook at the product list page without first authenticating, either by directly calling `mp_navigate` to a deep page or by setting a fake token via `wx.setStorageSync` |
| **Why it's bad** | Every page except the home page assumes a valid session. A test that bypasses login never exercises the auth check, never validates the token refresh path, and never catches regressions in the login UI itself. It also produces flaky tests because the page may render a "please log in" placeholder instead of the expected content, and the assertion may coincidentally pass |
| **Right practice** | Always start with the login step (Operation #8 above). The 9-step runbook explicitly calls this out as step 2. The login takes ~3 seconds and prevents 80% of "test passed but app is broken" failures |

---

## The 9-step core flow (overview)

The complete end-to-end walkthrough is documented step-by-step in [`e2e/poc/full-flow.md`](../../e2e/poc/full-flow.md). High-level summary:

1. **Cold start** — `mp_ensureConnection` to launch the app
2. **WeChat login** — `wx.login` + dev- prefixed code typed via `element_input`
3. **Product list browse + scroll** — `mp_currentPage` confirms the list page; `element_scrollTo` to verify scroll behavior
4. **Product detail** — `element_tap` on a product card; `page_waitElement` for the detail content
5. **Add to cart** — `element_tap` on the "Add to cart" button; assert the success toast
6. **Cart** — `mp_navigate` to `/pages/cart/index`; assert items
7. **Place order** — `element_tap` on "Checkout"; assert the success page
8. **Order list** — `mp_navigate` to `/pages/order/list`; assert the new order appears
9. **Profile** — `mp_navigate` to `/pages/profile/index`; assert user info is shown

Each step produces a screenshot at `e2e/poc/screenshots/step-N-{action}.png`.

---

## How to reproduce locally (3 steps)

1. **Start the backend stack** — `cd backend && docker-compose up -d && ./gradlew bootRun` (or use the native binary if compiled). Confirm `curl http://localhost:8080/actuator/health` returns `{"status":"UP"}`.
2. **Open WeChat DevTools and load the project** — open the seafood mini-program project (`/Users/linbinghui/agent-work/seafood-miniapp/frontend`), trust it, and leave DevTools running in the background. Confirm the project shows in the simulator pane.
3. **Run the runbook in Claude Code** — issue `/goal` with the runbook condition, or manually drive each step from [`e2e/poc/full-flow.md`](../../e2e/poc/full-flow.md). Each step should complete in <5 seconds; the full 9-step flow runs in 2-4 minutes.

---

## How to convert this guide into a Claude skill (3 steps)

This document is intentionally shaped so it can be promoted to a real Claude skill with minimal effort:

1. **Copy the YAML frontmatter** (top of this file) verbatim into a new `SKILL.md` file. The `when-to-use` keywords already match common E2E trigger phrases.
2. **Rewrite the Operations Catalog** (this document's middle section) as numbered **Step** sections in `SKILL.md` — each step gets a heading, a "When to use this step" sentence, the JSON tool call, and a verification point. Drop the 4-column table format; in a skill, the structure is heading + body, not table.
3. **Place the file at `.claude/skills/miniprogram-e2e/SKILL.md`** in the project, then trigger it once (`/skill miniprogram-e2e`) to confirm the description and trigger keywords resolve. If the skill doesn't fire, tune the `when-to-use` keywords in the frontmatter.

Once converted, the skill can be invoked from any session to get the same guidance without re-reading this 500-line document.

---

## Notes

- This guide targets the `weapp-dev` MCP server version shipped with the project as of June 2026. If the MCP tool names change (e.g. `mp_callWx` is renamed), update the Operations Catalog and the runbook in lockstep.
- For Skyline-renderer-specific behavior (which differs from webview), see the `skyline-*` skills in the project's `.claude/skills/` directory. This guide assumes a webview or hybrid project unless explicitly noted.
- The 9-step flow is the **minimum** core journey. Additional journeys (search, filter, address management, order cancel) are documented in [`openspec/changes/add-miniapp-e2e-tests/design.md`](../../openspec/changes/add-miniapp-e2e-tests/design.md) and can be added to this runbook as separate files.

---

## Troubleshooting

When a step in the runbook fails, work through this checklist before assuming the app is broken.

### Step 1: Capture the failure state

The single most useful thing you can do is take a fresh screenshot **at the failure point**. Call:

```json
{ "path": "e2e/poc/screenshots/FAILURE-{step-name}.png" }
```

Then call `mp_getLogs` (with `clear: false` to preserve the log buffer):

```json
{ "clear": false }
```

The failure screenshot + the console log buffer together resolve 80% of "why did this fail" questions.

### Step 2: Classify the failure

| Symptom | Likely cause | Next action |
|---|---|---|
| `mp_ensureConnection` returns "automation service unavailable" | DevTools not logged in, or DevTools version mismatch | Log in to DevTools manually; check `~/.claude/mcp-logs/weapp-dev.log` for the version it tried to launch |
| `page_waitElement` times out | Selector wrong, or data didn't load, or page is mid-transition | Take a screenshot, then `mp_getLogs` to look for the API request that should have populated the data |
| `element_tap` returns "ambiguous selector" | Selector matches multiple elements | Disambiguate with `[index=N]` or use a more specific class |
| `mp_screenshot` returns blank/black image | DevTools not in foreground, or Skyline render bug | Bring DevTools to focus; if the issue persists, switch to a webview-only build for testing |
| Login fails with "invalid code" | Code doesn't start with `dev-` | Use a fixed `dev-test-user-001` literal; do not invent new codes per run |
| Network errors in log | Backend not running, or wrong `API_BASE_URL` | `curl http://localhost:8080/actuator/health` to confirm backend; check the mini-program's `app.ts` for `API_BASE_URL` |
| Token-related 401s | Token expired or not persisted | Call `wx.getStorageSync('token')`; if empty, re-run the login step |

### Step 3: Iterate, don't restart

If only the last step failed, do not re-run the whole 9-step flow. The earlier steps consumed backend resources (created an order, etc.). Either:
- Reset the backend state (`./gradlew resetSeed` or `mongo seafood --eval "db.dropDatabase()"` followed by `seed.sh`)
- Continue from the failed step in a new Claude session, **but** re-verify steps 1-2 (login) because the session token won't survive a session boundary

### Step 4: When in doubt, read the page

`mp_currentPage` is cheap. Call it whenever something looks wrong:

```json
{}
```

The result includes the current page path, query parameters, and the renderer's mode (webview vs skyline). If the path is wrong, you navigated to the wrong place. If the renderer is unexpected, you may be hitting Skyline-only behavior that the runbook doesn't cover.

---

## Performance expectations

Knowing roughly how long each step should take helps you spot slow steps that are about to fail.

| Step | Expected duration | Why |
|---|---|---|
| 1. Cold start | 1-3s | DevTools launch + simulator first paint |
| 2. Login | 2-4s | `wx.login` roundtrip + dev-mode auth bypass + token storage |
| 3. Product list + scroll | 1-2s | `GET /api/products` (cached after first call) + scroll animation |
| 4. Product detail | 1-2s | `GET /api/products/{id}` + page transition |
| 5. Add to cart | <1s | Optimistic UI update + background `POST /api/cart/items` |
| 6. Cart | 1-2s | `GET /api/cart` + page transition |
| 7. Place order | 2-4s | `POST /api/orders` + business validation + cart clear |
| 8. Order list | 1-2s | `GET /api/orders` + page transition |
| 9. Profile | <1s | Local data from `wx.getStorageSync` |

**Total expected runtime**: 10-20 seconds for a healthy run. If a step takes >2x its expected duration, something is wrong (network slow, backend overloaded, or the step is stuck waiting on an element that never appeared).

**Token cost**: each MCP tool call costs roughly 1-3k tokens (input + output). The 9-step runbook involves ~20-25 tool calls, so a single complete run is ~30-75k tokens. Plan your budget accordingly; do not run the full flow more than a few times per session unless you're debugging.

---

## Appendix: Full MCP tool reference

The `weapp-dev` MCP server exposes the following tools. The "Used in runbook" column indicates whether the tool appears in the 9-step runbook or in the Operations Catalog (✓) versus being mentioned only as an anti-pattern (✗).

### Connection lifecycle

| Tool | Purpose | Used in runbook |
|---|---|---|
| `mp_ensureConnection` | Launch or attach to WeChat DevTools | ✓ (Step 1) |
| `mp_listProjects` | List recent projects (for project selection prompt recovery) | — |
| `mp_setDefaultProject` | Set default project path | — |

### Navigation

| Tool | Purpose | Used in runbook |
|---|---|---|
| `mp_navigate` | Cross-page jump (`navigateTo` / `redirectTo` / etc.) | ✓ (Steps 6, 8, 9) |
| `mp_callWx` | Low-level `wx.*` API call (e.g. `wx.login`, `wx.getStorageSync`) | ✓ (Step 2, login only) |

### Inspection

| Tool | Purpose | Used in runbook |
|---|---|---|
| `mp_currentPage` | Get current page path, query, renderer | ✓ (verification helper) |
| `mp_screenshot` | Capture viewport as PNG | ✓ (every step) |
| `mp_getLogs` | Get console logs (with optional clear) | ✓ (failure recovery) |
| `page_getData` | Read page instance's data object | ✓ (verification helper) |
| `page_getElements` | List elements matching a selector | ✓ (verification helper) |
| `page_getElementByXpath` | XPath-based element lookup | — |
| `page_getElementsByXpath` | XPath-based element list | — |

### Interaction

| Tool | Purpose | Used in runbook |
|---|---|---|
| `element_tap` | Tap an element by selector | ✓ (Steps 2, 4, 5, 7) |
| `element_input` | Type into an input field | ✓ (Step 2, credential) |
| `element_scrollTo` | Scroll a `<scroll-view>` | ✓ (Step 3) |
| `page_waitElement` | Wait for an element to appear | ✓ (every step, before tap) |
| `page_waitTimeout` | Wait for a fixed duration | — (use `page_waitElement` instead) |
| `page_callMethod` | Call an exposed method on the page | — |

### Mutation (anti-pattern — do not use in runbook)

| Tool | Purpose | Why it's an anti-pattern |
|---|---|---|
| `page_setData` | Set the page's data directly | Bypasses user actions; covered in §Anti-pattern 2 |
| `element_setData` | Set a custom component's data | Same as `page_setData`; never use |
| `mp_mockWxMethod` | Mock `wx.*` method responses | Bypasses real network; covered in §Anti-pattern 3 |

### Custom component helpers (use sparingly)

| Tool | Purpose | Used in runbook |
|---|---|---|
| `element_callMethod` | Call a method on a custom component instance | — (only for custom components with exposed methods) |
| `element_getAttributes` | Get HTML-style attributes | — |
| `element_getData` | Get a custom component's data | — |
| `element_getInnerElement(s)` | Scope element search to a component | ✓ (occasionally, for nested components) |
| `element_getStyles` | Get computed styles | — (visual regression territory) |
| `element_getWxml` | Get element's WXML source | — (debugging) |
| `element_getBoundingClientRect` | Get element position/size | — (debugging) |

### Configuration

| Tool | Purpose | Used in runbook |
|---|---|---|
| `mp_listProjects` | List recent projects | — |
| `mp_setDefaultProject` | Set default project path | — |

---

## Further reading

- [`openspec/changes/add-miniapp-e2e-tests/design.md`](../../openspec/changes/add-miniapp-e2e-tests/design.md) — the OpenSpec change that motivated this guide; contains the broader E2E design (CI integration, data isolation, flaky mitigation)
- [`openspec/changes/add-miniapp-e2e-tests/proposal.md`](../../openspec/changes/add-miniapp-e2e-tests/proposal.md) — the why: which user journeys matter, what risks the E2E suite mitigates
- WeChat DevTools automation docs: <https://developers.weixin.qq.com/miniprogram/dev/devtools/auto/>
- `miniprogram-automator` API reference: <https://github.com/wechat-miniprogram/miniprogram-automator>
- The `weapp-dev` MCP server source / config lives in the project's `~/.claude/settings.json` (not in the repo)

---

## Changelog

| Version | Date | Change |
|---|---|---|
| 1.0 | 2026-06-12 | Initial guide. 9-step core flow + 8 Operation Catalog entries + 5 Anti-patterns. Created alongside the OpenSpec change `add-miniapp-e2e-tests` to give that change a runnable, tool-grounded reference |

### Future work (not yet implemented)

- **Search / filter journey** — `GET /api/products?category=...&q=...` flow, separate runbook file
- **Address management journey** — add / edit / delete address; checkout-with-address
- **Order cancel journey** — `POST /api/orders/{id}/cancel` with reason codes
- **Skyline-renderer parity** — the current 9 steps assume a webview render; Skyline-specific behavior (worklet animations, semi-modal sheets) needs separate validation
- **CI integration** — convert the runbook to a `miniprogram-automator` npm script and wire into `.github/workflows/ci.yml` as the `e2e` job (see `design.md §3` for the deferred-CI rationale)
- **Visual regression** — store golden images in `e2e/golden/` and diff against the runbook's screenshots; requires an `npm run e2e:update-golden` script
- **Parallel journeys** — run the 9 steps + the 4 future journeys in parallel jobs; cuts total CI time from 2-4 minutes to 1-2 minutes
