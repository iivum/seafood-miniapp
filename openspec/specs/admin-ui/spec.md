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
