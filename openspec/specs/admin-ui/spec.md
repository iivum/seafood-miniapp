# Spec: admin-ui

## Purpose

[TBD — see change `refactor-rust-rebuild-frontend` for context. Defines the admin web UI built on React 18 + Vite + shadcn/ui, consuming the backend BFF endpoints under `/api/admin/**`.]

## Requirements

### Requirement: Admin authentication
The admin UI SHALL authenticate users via `POST /api/admin/auth/login` using a separate JWT secret from the mini-program, store the resulting access token in an `httpOnly` cookie, and SHALL NOT keep tokens in `localStorage`.

#### Scenario: Successful admin login
- **WHEN** the user submits valid admin credentials on the login form
- **THEN** the UI redirects to `/dashboard` and the access token cookie is set by the response

#### Scenario: Failed admin login
- **WHEN** the user submits invalid credentials
- **THEN** the UI shows an inline error message and does not navigate away

#### Scenario: Authenticated API call
- **WHEN** the UI makes any request to `/api/admin/**` after login
- **THEN** the browser automatically attaches the `httpOnly` cookie

#### Scenario: Token refresh
- **WHEN** any `/api/admin/**` request returns 401 due to an expired access token
- **THEN** the UI calls `POST /api/admin/auth/refresh` once, retries the original request, and only navigates to `/login` if the refresh also fails

### Requirement: Product management screens
The admin UI SHALL provide list, create, edit, and delete views for products, each backed by `/api/admin/**` aggregation endpoints and `/api/products/**` mutations.

#### Scenario: Admin opens the product list
- **WHEN** an admin navigates to `/admin/products`
- **THEN** the UI displays a paginated table of products fetched from `GET /api/admin/products/stats` plus `GET /api/products`

#### Scenario: Admin creates a product
- **WHEN** an admin submits the product form with valid fields
- **THEN** the UI calls `POST /api/products` and shows a success toast; the table refreshes

#### Scenario: Admin edits a product
- **WHEN** an admin submits the edit form with changes
- **THEN** the UI calls `PUT /api/products/{id}` and shows a success toast; the table row reflects the new value

#### Scenario: Admin deletes a product
- **WHEN** an admin confirms the delete dialog
- **THEN** the UI calls `DELETE /api/products/{id}` and removes the row; a failure shows an error toast

### Requirement: Order management screens
The admin UI SHALL provide a list of orders and a detail view that includes customer information and expanded line items.

#### Scenario: Admin opens the order list
- **WHEN** an admin navigates to `/admin/orders`
- **THEN** the UI shows a paginated list of orders from `GET /api/orders`

#### Scenario: Admin opens order detail
- **WHEN** an admin clicks an order row
- **THEN** the UI calls `GET /api/admin/orders/{id}/detail` and renders the aggregated payload (customer, line items with product info)

#### Scenario: Admin ships an order
- **WHEN** an admin clicks "Ship" on a `PAID` order
- **THEN** the UI calls `POST /api/orders/{id}/ship` and reflects the new status

### Requirement: Dashboard
The admin UI SHALL provide a dashboard at `/admin/dashboard` powered by `GET /api/admin/dashboard`.

#### Scenario: Admin opens the dashboard
- **WHEN** an admin navigates to `/admin/dashboard`
- **THEN** the UI renders three cards: order stats (today / week / month), product stats, and a top-products table

#### Scenario: Dashboard loading state
- **WHEN** the dashboard query is in-flight
- **THEN** the UI shows a skeleton placeholder for each card

#### Scenario: Dashboard error state
- **WHEN** the dashboard request fails
- **THEN** the UI shows an error state with a retry button

### Requirement: Route protection
The admin UI SHALL redirect unauthenticated users to the login page and SHALL block any non-admin role from reaching admin routes.

#### Scenario: Unauthenticated user visits a protected route
- **WHEN** an unauthenticated browser navigates to `/admin/products`
- **THEN** the UI redirects to `/admin/login` and remembers the original destination

#### Scenario: Authenticated admin visits login
- **WHEN** an already-authenticated admin navigates to `/admin/login`
- **THEN** the UI redirects to `/admin/dashboard`

### Requirement: Visual baseline
The admin UI SHALL use the shadcn/ui component library and Tailwind CSS, with a design-token file shared with the mini-program to guarantee visual coherence across surfaces.

#### Scenario: Component library is shadcn/ui
- **WHEN** any new view is built
- **THEN** the developer composes it from primitives in `src/components/ui/*` (shadcn) and avoids hand-rolling equivalent markup

#### Scenario: Colors come from tokens
- **WHEN** any element receives a color, spacing, or typography style
- **THEN** the style SHALL reference a token from the shared `tokens.json` rather than a hard-coded value

### Requirement: Admin UI scope is single-seller internal operations
The admin UI SHALL be scoped exclusively to single-seller internal operations. The system MUST NOT implement any of the following external-merchant capabilities: merchant onboarding, multi-tenant seller separation, merchant self-service portal, merchant settlement or revenue splitting, or merchant-facing analytics. Only the role `INTERNAL_OPERATOR` (or `INTERNAL_CS`) may authenticate and use the admin UI. The system MUST reject any JWT carrying a `MERCHANT` role at the filter chain with HTTP 401 `code=AUTH_INVALID_ROLE`.

#### Scenario: No external merchant routes exist
- **WHEN** a developer inspects `admin-ui/src/features/` and `admin-ui/src/routes.tsx` (or equivalent)
- **THEN** there is no route for merchant onboarding, settlement, storefront management, or seller analytics
- **AND** the backend `AdminBffController` exposes no endpoint matching `/api/admin/merchants/**` or `/api/admin/sellers/**`

#### Scenario: MERCHANT role JWT rejected
- **WHEN** a JWT carrying `role: "MERCHANT"` is presented at any `/api/admin/**` endpoint
- **THEN** the request is rejected with HTTP 401 and `code: "AUTH_INVALID_ROLE"`

---

### Requirement: Admin UI consumes OKLch token system
The admin UI MUST consume the `tokens.tailwind.ts` file generated by the same build step as the mini-program's `tokens.wxss`, ensuring visual parity between the two surfaces. The admin UI MUST NOT introduce any new hardcoded hex color values in JSX or CSS outside of the `tokens.tailwind.ts` source.

#### Scenario: Tailwind theme extends tokens
- **WHEN** a developer inspects `admin-ui/tailwind.config.ts`
- **THEN** `theme.extend.colors` exposes `bg`, `surface`, `fg`, `muted`, `border`, `accent`, `accent-soft`, `accent-strong`, `accent-deep`, `success`, `warning`, `error`, `info` (and their `-soft` variants)
- **AND** `theme.extend.fontFamily` exposes `display`, `body`, `mono`
- **AND** all values reference the imported `tokens` object

#### Scenario: Component uses token-derived class
- **WHEN** a developer writes `<div className="bg-accent text-bg font-body">`
- **THEN** the compiled CSS uses `oklch(64% 0.16 38)` and `oklch(99% 0.006 60)` (the canonical token values)

---

### Requirement: Admin UI six operational screens
The admin UI SHALL provide exactly six operational screens: login (`/login`), dashboard (`/`), product list (`/products`), product form (`/products/new` and `/products/{id}/edit`), order list (`/orders`), and order detail (`/orders/{id}`). All six screens MUST consume `/api/admin/**` endpoints (or, for product write paths, `/api/products/**` mutations). The detailed functional behavior of each screen is defined in the `admin-ui-modules` capability.

#### Scenario: Six routes registered
- **WHEN** a developer inspects the admin UI router
- **THEN** exactly six route entries exist: `/login`, `/`, `/products`, `/products/new`, `/products/{id}/edit`, `/orders`, `/orders/{id}`

#### Scenario: Product form saves via the public product API
- **WHEN** an admin submits the product form
- **THEN** the UI calls `POST /api/products` (for new) or `PUT /api/products/{id}` (for edit)
- **AND** the resulting product is visible in the product list

---

### Requirement: Admin UI deployment as third Docker service
The admin UI SHALL be deployed as a third Docker service `admin-ui` in `docker-compose.yml`, separate from `backend` and `mongodb`. The `admin-ui` service MUST depend on `backend` being healthy before starting. The bundled nginx configuration MUST reverse-proxy all `/api/**` requests to `http://backend:8080` so the browser does not need to know the backend's internal address. The service MUST expose port 5173 on the host for direct browser access.

#### Scenario: docker-compose up brings all three services
- **WHEN** a developer runs `docker-compose up -d` from a clean state
- **THEN** three services start: `mongodb`, `backend`, `admin-ui`
- **AND** `http://localhost:5173/` returns HTTP 200 with the admin login HTML

#### Scenario: API calls proxy through nginx
- **WHEN** the admin UI's JavaScript makes a request to `/api/admin/auth/cookie-login`
- **THEN** nginx forwards the request to `http://backend:8080/api/admin/auth/cookie-login`
- **AND** the response's `Set-Cookie` header is honored by the browser

---

### Requirement: Admin UI access matrix
The admin UI routes SHALL be guarded as follows:
- `/login` — public
- `/` (dashboard) — `INTERNAL_OPERATOR` or `INTERNAL_CS`
- `/products/**` — `INTERNAL_OPERATOR` only (write paths), all internal roles for read
- `/orders/**` — `INTERNAL_OPERATOR` only (write paths), all internal roles for read

When the user lacks the required role for a route, the admin UI MUST redirect to `/login` (when unauthenticated) or display a 403 page (when authenticated but lacking the role).

#### Scenario: Unauthenticated user visits `/products`
- **WHEN** a browser with no auth cookie navigates to `/products`
- **THEN** the UI redirects to `/login` and remembers the original destination

#### Scenario: Authenticated INTERNAL_CS visits `/products/new`
- **WHEN** a user with role `INTERNAL_CS` navigates to `/products/new`
- **THEN** the UI displays a 403 page with the message "权限不足"
- **AND** does not render the product form

---

### Requirement: Visual baseline refresh
The admin UI SHALL use the OKLch-based token system for all colors, typography, radii, and shadows. The admin UI MUST NOT use any Tailwind default colors (e.g. `bg-blue-500`, `text-red-600`) — all color usage MUST go through the token-derived utilities (`bg-accent`, `text-fg`, etc.). The design posture documented in the `visual-design-system` capability applies to the admin UI equally.

#### Scenario: No default Tailwind colors in components
- **WHEN** a developer searches `admin-ui/src/` for Tailwind color classes
- **THEN** the search finds no matches for `bg-(red|blue|green|yellow|purple|pink|gray|slate|zinc|neutral|stone|amber|orange|lime|emerald|teal|cyan|sky|indigo|violet|fuchsia|rose)-[0-9]`
- **AND** all color usage is via token-derived classes

#### Scenario: Three-font typography system in use
- **WHEN** a developer inspects any rendered admin UI page
- **THEN** headings and prices use the `font-display` (Fraunces) family
- **AND** body text uses the `font-body` (Inter Tight) family
- **AND** order IDs and numeric KPIs use the `font-mono` (Geist Mono) family
