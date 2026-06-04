## 1. Backend Bootstrap

- [x] 1.1 Delete `backend/` multi-module structure (gateway, product-service, order-service, user-service, common, discovery-service, config-service)
- [x] 1.2 Create new single-module `backend/` with `settings.gradle` declaring `seafood-backend`
- [x] 1.3 Set up `build.gradle` for Spring Boot 4.0.6 + Java 25 (native plugin still `apply false` at this stage)
- [x] 1.4 Scaffold `com.seafood.{product,order,user,bff.admin,shared}` packages with `api/application/domain/infra` layers
- [x] 1.5 Add `SeafoodApplication.java` entry point and `application.yml` / `application-docker.yml`
- [x] 1.6 Provide MongoDB seed fixtures (50 products, 5 categories, 1 admin, 1 customer) under `seed/`

## 2. Auth & Security

- [x] 2.1 Implement `JwtTokenProvider` (HS256, fail-fast on missing `JWT_SECRET`, 15m access / 7d refresh)
- [x] 2.2 Implement `JwtAuthenticationFilter` with public-endpoint skip list
- [x] 2.3 Configure `SecurityConfig` with method security (`@PreAuthorize`) and access matrix
- [x] 2.4 Implement `AuthService.login` / `refresh` for `/api/auth/**` and separate `/api/admin/auth/**`
- [x] 2.5 Enforce single-use refresh tokens (reject reused `jti`, revoke token family)
- [x] 2.6 Add static-analysis check (CI) that fails the build if `@RefreshScope` is referenced anywhere

## 3. Product Module

- [x] 3.1 Model `Product` aggregate root + `ProductCategory` sealed interface + `ProductStatus`
- [x] 3.2 Implement `ProductRepository` interface and `ProductMongoRepository` (`@Document`)
- [x] 3.3 Implement `ProductService` (create, update, delete, get, list, stats, groupByCategory)
- [x] 3.4 Implement `ProductController` for `/api/products/**` with role-based authorization
- [x] 3.5 Write unit tests for domain (≥95% coverage) and application tests with Mockito (≥85%)

## 4. Cart & Order Module

- [x] 4.1 Model `Cart` (1 doc per userId) and `Order` aggregate root with `OrderItem`
- [x] 4.2 Model `OrderStatus` as a sealed interface (`PENDING/PAID/SHIPPED/COMPLETED/CANCELLED`) with transition rules
- [x] 4.3 Implement `CartService` (addItem, list, clear) scoped to the caller's userId
- [x] 4.4 Implement `OrderService.create` capturing price/stock snapshots and decrementing stock atomically
- [x] 4.5 Implement `OrderService.list` (own vs all by role) and `OrderService.ship` (admin-only state transition)
- [x] 4.6 Write integration tests with Testcontainers MongoDB for stock-decrement and invalid-transition paths

## 5. User Module

- [x] 5.1 Model `User` aggregate with `Customer` / `Admin` role discriminator
- [x] 5.2 Implement `UserService` (get, list, address add/update/remove) and `UserRepository`
- [x] 5.3 Wire WeChat login flow: `POST /api/auth/wechat-login` exchanges `code` → `openId`
- [x] 5.4 Write unit and integration tests for user lifecycle

## 6. BFF Admin Endpoints

- [x] 6.1 Implement `GET /api/admin/orders/{id}/detail` aggregating order + customer + line-item products in-process
- [x] 6.2 Implement `GET /api/admin/products/stats` returning totals and per-category counts
- [x] 6.3 Implement `GET /api/admin/dashboard` returning order stats (today/week/month) + product stats + top products
- [x] 6.4 Restrict all `/api/admin/**` endpoints to ADMIN role
- [x] 6.5 Add `@WebMvcTest` coverage for each BFF endpoint (mocked application services)

## 7. GraalVM Native Build

- [x] 7.1 Enable `org.graalvm.buildtools.native` plugin in `backend/build.gradle` (`apply false` → applied)
- [x] 7.2 Configure `graalvmNative` block (imageName, mainClass, buildArgs) per `design.md` §3.1
- [x] 7.3 Add `native-image/reflect-config.json`, `resource-config.json`, `proxy-config.json` (or generate via nativeTest)
- [x] 7.4 Update `Dockerfile` to multi-stage `ghcr.io/graalvm/native-image:ol9-java25` → `distroless/base-debian12`
- [x] 7.5 Verify `nativeCompile` produces a binary that boots < 2s and serves `GET /actuator/health` 200
- [x] 7.6 Add `MongoIndexInitializer` (`@EventListener(ApplicationReadyEvent.class)`) and verify it runs in Native mode

## 8. Mini-Program Refactor

- [ ] 8.1 Restructure `frontend/src/` from type-based (`pages/`, `utils/`, `types/`) to feature-based (`features/{product,cart,order,user,admin}/`) + `shared/`
- [ ] 8.2 Move all `.wxml/.wxss/.ts` pages into `pages/` and wire them to consume `features/*/components`
- [ ] 8.3 Centralize `shared/api/request.ts` with auto token-refresh on 401
- [ ] 8.4 Implement `features/auth/store.ts` for WeChat login flow (`wx.login` → `POST /api/auth/wechat-login`)
- [ ] 8.5 Wire `features/cart` and `features/order` pages to new cart/order endpoints
- [ ] 8.6 Add the shared `tokens.json` consumer so WXSS reads `var(--color-primary)` etc. (CI-synced with admin-ui)

## 9. Admin UI (new stack)

- [ ] 9.1 Delete old `backend/admin-ui/` Vue 3 + Element Plus sources
- [ ] 9.2 Scaffold new top-level `admin-ui/` with Vite + React 18 + TypeScript strict
- [ ] 9.3 Initialize shadcn/ui (`npx shadcn@latest init`) and add primitives (Button, Card, Dialog, Form, Input, Select, Table)
- [ ] 9.4 Configure Tailwind to consume shared `tokens.json` for color/spacing/typography
- [ ] 9.5 Build `Login` view + httpOnly-cookie auth flow against `/api/admin/auth/login`
- [ ] 9.6 Build `Dashboard` view consuming `GET /api/admin/dashboard`
- [ ] 9.7 Build `ProductList` + `ProductForm` views against `/api/products/**` and `/api/admin/products/stats`
- [ ] 9.8 Build `OrderList` + `OrderDetail` views against `GET /api/orders` and `GET /api/admin/orders/{id}/detail`
- [ ] 9.9 Configure route protection (redirect anonymous to `/admin/login`)

## 10. Static Asset Hosting

- [x] 10.1 Build `admin-ui/` to `backend/src/main/resources/static/admin/`
- [x] 10.2 Add a Spring `WebConfig` (or `RouterFunction`) that serves `/admin/**` from `classpath:/static/admin/` with history-API fallback to `index.html` for unknown client routes

## 11. Shared Error Handling

- [x] 11.1 Implement `GlobalExceptionHandler` mapping `NotFoundException` / `ValidationException` / `DomainException` to `ErrorResponse` records (404 / 400 / 409)
- [x] 11.2 Map Bean Validation failures to `code=VALIDATION` with `fieldErrors`
- [x] 11.3 Add integration tests for each exception path

## 12. Integration & Deployment

- [x] 12.1 Rewrite `docker-compose.yml` to 2 services: `backend` (Native binary) and `mongodb`
- [x] 12.2 Add GitHub Actions workflow: `./gradlew test`, `./gradlew nativeCompile`, `npm test` (mini-program), `npm run build` (admin-ui), `docker build`
- [x] 12.3 Tag old multi-module code as `v2.0-multi-module-archived` and move artifacts to `archive/backend-multi-module-2026-06/`
- [x] 12.4 Run cutover: deploy new monolith on :8081, keep old :8080 as fallback for 24h, then switch base URL and shut down old services
- [x] 12.5 Verify Definition-of-Done items: E2E "browse → login → order → view", coverage ≥ 80% (backend) / ≥ 88% (frontend), P99 < 200ms, peak RSS < 200MB, startup < 2s
