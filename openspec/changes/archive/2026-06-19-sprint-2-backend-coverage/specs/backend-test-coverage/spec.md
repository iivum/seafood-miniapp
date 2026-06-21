## ADDED Requirements

### Requirement: Backend controller slice tests for all @RestController
The system MUST have at least one passing controller slice test for every `@RestController` declared under `backend/src/main/java/com/seafood/**/api/`. Controller slice tests MUST be located under `backend/src/test/java/com/seafood/<module>/api/<ControllerName>SliceTest.java` and MUST NOT start a full Spring Boot application context (no `@SpringBootTest`). Tests for endpoints guarded by `@PreAuthorize` MUST include at least one case asserting the role check rejects unauthorized callers with `AccessDeniedException`. Tests for endpoints requiring `@AuthenticationPrincipal` MUST populate `SecurityContextHolder` with a `UserPrincipal` before invoking the controller.

#### Scenario: ProductController public read endpoint returns 200 with body
- **WHEN** a test invokes `ProductController.list` with no auth and the stubbed `ProductService` returns a page containing `ProductBuilder.aProduct().build()`
- **THEN** the test asserts the response contains the builder-derived product id (`"p-test"`) and the test passes

#### Scenario: ProductController non-existent id returns 404
- **WHEN** a test invokes `ProductController.get("missing")` and the stubbed `ProductService` throws `NotFoundException("PRODUCT_NOT_FOUND", ...)`
- **THEN** the test asserts `NotFoundException` is thrown (controller propagates the exception unchanged)

#### Scenario: AdminOrderController batch-ship rejected without ADMIN role
- **WHEN** a test invokes `AdminOrderController.batchShip` with no authentication in the `SecurityContext`
- **THEN** the test asserts `AccessDeniedException` is thrown (class-level `@PreAuthorize("hasRole('ADMIN')")` blocks the call)

#### Scenario: OrderController ship endpoint rejects CUSTOMER role
- **WHEN** a test invokes `OrderController.ship("o-1")` with `SecurityContextHolder` populated as `Role.CUSTOMER`
- **THEN** the test asserts `AccessDeniedException` is thrown (method-level `@PreAuthorize("hasRole('ADMIN')")` on `ship` blocks CUSTOMER)

### Requirement: Backend repository slice tests for all MongoRepository interfaces
The system MUST have at least one passing repository slice test for every Spring Data `MongoRepository` interface under `backend/src/main/java/com/seafood/<module>/infra/`. Repository slice tests MUST extend `com.seafood.testsupport.MongoIntegrationTest` (which provides `@Container static MongoDBContainer mongo:7`) and MUST register the container URI via `@DynamicPropertySource` pointing at `MONGO.getReplicaSetUrl()`. Each repository test class MUST cover at minimum: `save → findById` round-trip, `findById` with unknown id (returns empty), `deleteById` removes the document, `existsById` returns the expected boolean. Tests MUST be tagged `@Tag("docker")` so they can be skipped in environments without Docker (`./gradlew test -PexcludeTags=docker`).

#### Scenario: OrderRepository round-trip preserves domain fields
- **WHEN** a test calls `orders.save(OrderMapper.toDocument(OrderBuilder.anOrder().withId("o-test-1").withUserId("u-1").build()))` and then `orders.findById("o-test-1")`
- **THEN** the returned `Optional` is present and `getUserId()` equals `"u-1"` (no field loss across Mongo round-trip)

#### Scenario: OrderRepository findById returns empty for unknown id
- **WHEN** a test calls `orders.findById("nonexistent")` after the collection is empty
- **THEN** the returned `Optional` is empty (Spring Data Mongo contract honoured)

#### Scenario: OrderRepository deleteById removes the document
- **WHEN** a test saves an order with id `"o-del"` and then calls `orders.deleteById("o-del")`
- **THEN** a subsequent `orders.findById("o-del")` returns empty

#### Scenario: OrderRepository existsById reflects persistence state
- **WHEN** a test saves an order with id `"o-exists"` and then calls `orders.existsById("o-exists")` and `orders.existsById("missing")`
- **THEN** the first call returns `true` and the second returns `false`

### Requirement: BFF integration tests for all /api/admin/** endpoints
The system MUST have at least one happy-path test and one auth-rejection test for every endpoint exposed under `backend/src/main/java/com/seafood/bff/admin/` that maps to `/api/admin/**`. BFF tests MUST mock `AdminBffService` and any module-level `Service` collaborator, and MUST populate `SecurityContextHolder` with a `ROLE_ADMIN` `UserPrincipal` for the happy-path case. The auth-rejection case MUST invoke the controller method with an empty `SecurityContext` and assert `AccessDeniedException` is thrown by Spring Security's `@PreAuthorize` evaluation. Endpoints that share a controller (e.g., `AdminBffController.dashboard` and `AdminBffController.productStats`) MAY be tested in the same class to reduce file count, but each endpoint MUST have its own test method.

#### Scenario: AdminBffController dashboard returns aggregated metrics for ADMIN
- **WHEN** a test populates `SecurityContextHolder` with a `ROLE_ADMIN` principal and stubs `AdminBffService.dashboard()` to return `new DashboardResponse(3L, 18L, 70L, 100L, 120L)`
- **THEN** invoking `controller.dashboard()` returns the stubbed response with `totalOrders()` equal to `3L`

#### Scenario: AdminBffController dashboard rejected without ADMIN role
- **WHEN** a test invokes `controller.dashboard()` with an empty `SecurityContext`
- **THEN** `AccessDeniedException` is thrown before the controller body executes

#### Scenario: AdminBffController orderDetail returns 404-mapped exception
- **WHEN** a test populates `ROLE_ADMIN` and stubs `AdminBffService.orderDetail("missing")` to throw `NotFoundException("ORDER_NOT_FOUND", ...)`
- **THEN** invoking `controller.orderDetail("missing")` propagates `NotFoundException` (controller does not swallow)

### Requirement: All new backend tests use D1 builder fixtures
The system MUST construct test domain entities via the builder fixtures shipped in `sprint-2-test-data-builders` (i.e., `OrderBuilder.anOrder()`, `ProductBuilder.aProduct()`, `UserBuilder.aUser()`, `CartBuilder.aCart()`, `RefundBuilder.aRefund()`), and MUST NOT use inline `new Order(...)` / `new Product(...)` / `new User(...)` / `new Cart(...)` / `new Refund(...)` constructor calls in test code (mapper calls and response-record constructions are exempt). The 15 test classes added by this change MUST import from `com.seafood.testsupport.builders.*` and call the builder `build()` method at least once per test method.

#### Scenario: New controller slice test uses ProductBuilder instead of new Product(...)
- **WHEN** a developer writes a new `*ControllerSliceTest.java` and needs a `Product` domain instance
- **THEN** the test calls `ProductBuilder.aProduct().withId("...").build()` (optionally chained with `withXxx`) and never `new Product(...)`

#### Scenario: New repository slice test uses OrderBuilder for fixture
- **WHEN** a developer writes a new `*RepositorySliceTest.java` and needs an `Order` to save
- **THEN** the test calls `OrderBuilder.anOrder().withId("o-test-1").build()` and never `new Order(...)`

#### Scenario: CI grep audit finds zero inline domain constructors in tests
- **WHEN** `grep -rnE "new (Order|Product|User|Cart|Refund)\(" backend/src/test/java/com/seafood/ --include="*.java"` is run with builder-folder and mapper-response exclusions
- **THEN** the grep returns zero hits, confirming no test class regresses on the builder fixture rule

### Requirement: Coverage threshold and verification gate
After the 15 new test classes ship, the system MUST achieve all of: (a) `./gradlew check` PASSES (ArchUnit `ArchitectureTest` + `MetricsCardinalityTest` + `checkNoRefreshScope`); (b) `./gradlew test` reports zero failures and zero errors across the full backend test suite; (c) Jacoco global line coverage is at or above 80% (CLAUDE.md §3 hard rule). If (c) is not met, the change MUST NOT add additional tests to chase the threshold — instead a follow-up note MUST be filed under `openspec/changes/sprint-2-backend-coverage/coverage-gap.md` listing the uncovered classes/lines for a future sub-change.

#### Scenario: ./gradlew check passes with new tests
- **WHEN** the developer runs `cd backend && ./gradlew check` after all 15 test classes are added
- **THEN** the command exits with status 0, ArchUnit reports zero violations, and `checkNoRefreshScope` reports zero `@RefreshScope` occurrences

#### Scenario: Full backend test suite reports zero regressions
- **WHEN** the developer runs `cd backend && ./gradlew test` after all 15 test classes are added
- **THEN** the test report shows all 77 pre-existing tests plus the ~30 new tests passing, with zero failures and zero errors

#### Scenario: Jacoco global line coverage ≥80% verified
- **WHEN** the developer runs `cd backend && ./gradlew jacocoTestReport` and opens `backend/build/reports/jacoco/test/html/index.html`
- **THEN** the "Total" line coverage row reports a value ≥ 80%