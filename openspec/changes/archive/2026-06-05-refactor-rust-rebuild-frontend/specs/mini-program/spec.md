## ADDED Requirements

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
