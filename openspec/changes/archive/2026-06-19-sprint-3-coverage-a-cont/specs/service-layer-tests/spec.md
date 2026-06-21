## ADDED Requirements

### Requirement: Service-layer unit tests reach Jacoco coverage threshold
The system MUST have direct unit tests for the application service layer (`com.seafood.*.application.*Service`) that, combined with the existing controller slice / repository slice / BFF integration tests from Sprint 2 A, drive the Jacoco global line coverage to 80% or above. The threshold MUST be re-raised from 0.75 (temporary gate) to 0.80 (CLAUDE.md §3) in `backend/build.gradle` after the new tests land. The `openspec/changes/sprint-3-ci-speed/coverage-gap.md` file MUST be updated to mark the 5% gap as closed.

#### Scenario: Coverage gate passes at 0.80
- **WHEN** a developer runs `cd backend && ./gradlew check -PexcludeTags=docker -x processTestAot`
- **THEN** `jacocoTestCoverageVerification` reports `instructions covered ratio` ≥ 0.80
- **THEN** `./gradlew check` exits with status 0

#### Scenario: Coverage diff is reported per service
- **WHEN** the developer opens `backend/build/reports/jacoco/test/html/com.seafood.order.application.html`
- **THEN** `OrderService` line coverage is at or above 80% (was 0% before this change — service layer was untested)
- **THEN** `ProductService`, `UserService`, `AdminBffService` likewise at or above 80%

#### Scenario: Coverage-gap.md is closed
- **WHEN** the developer reads `openspec/changes/sprint-3-ci-speed/coverage-gap.md`
- **THEN** the file states "A 续 完成,coverage 已达 80%+" (or equivalent)
- **THEN** the file does not list any pending gap items

### Requirement: OrderService edge cases are covered by direct unit tests
The system MUST add direct unit tests for `OrderService` (package `com.seafood.order.application`) that cover the following previously-untested edge cases, using Mockito to mock `OrderRepository`, `CartRepository`, `ProductRepository`, `RefundRepository`, and `MeterRegistry`:

- `OrderService.batchShip(List<String>, String, String)` partial-failure path (some orderIds succeed, others fail with NotFoundException)
- `OrderService.findRecent(int)` when limit > 500 (truncation path)
- `OrderService.listRefunds(String, Pageable)` empty status filter
- `OrderService.renderPicklistHtml(String)` for a non-existent order (NotFoundException)
- `OrderService.requestRefund(String, BigDecimal, String)` when amount > order totalAmount (DomainException)
- `OrderService.rebuy(String)` for a CANCELLED order (no cart items added)

Each test method MUST be `void` and assert via `assertThatThrownBy(...)` or `verify(mock).method(...)` or `assertThat(result).isEqualTo(...)`. Tests MUST NOT use `anyString()` without justification — prefer specific strings to catch regressions.

#### Scenario: batchShip partial failure reports correct counts
- **WHEN** the test stubs `orderRepository.findById("o-1")` returning a SHIPPED document and `orderRepository.findById("o-missing")` returning `Optional.empty()`
- **AND** calls `orderService.batchShip(List.of("o-1", "o-missing"), "SF", "TRK")`
- **THEN** the returned `BatchShipResponse.successCount` equals 1
- **AND** `failedCount` equals 1
- **AND** `failed[0].orderId` equals "o-missing"
- **AND** `failed[0].reason` contains "订单不存在" (the NotFoundException message)

#### Scenario: findRecent truncates to 500
- **WHEN** the test stubs `orderRepository.findTop500ByOrderByCreatedAtDesc()` returning 500 documents
- **AND** calls `orderService.findRecent(1000)`
- **THEN** the returned `List<OrderResponse>` has size 500 (truncated, no exception)
- **AND** each element maps correctly from document to response

#### Scenario: listRefunds empty status returns all
- **WHEN** the test stubs `refundRepository.findByStatus("", any())` returning a Page of 3 refunds
- **AND** calls `orderService.listRefunds("", PageRequest.of(0, 20))`
- **THEN** the returned `Page<RefundResponse>` has 3 elements

#### Scenario: requestRefund amount exceeds order total throws DomainException
- **WHEN** the test stubs an order with `totalAmount = 50.00`
- **AND** calls `orderService.requestRefund("o-1", new BigDecimal("100.00"), "test")`
- **THEN** `assertThatThrownBy` catches `DomainException`
- **AND** the exception message contains "退款金额不能超过订单金额" or equivalent

### Requirement: ProductService edge cases are covered by direct unit tests
The system MUST add direct unit tests for `ProductService` (package `com.seafood.product.application`) that cover:

- `ProductService.listPublic(null, pageable)` empty category (uses `findByStatus(ACTIVE, pageable)`)
- `ProductService.listPublic("鱼类", pageable)` non-empty category (uses `findByCategory("鱼类", pageable)` and overrides status to ACTIVE)
- `ProductService.update(String, ProductRequest)` when product not found (NotFoundException)
- `ProductService.updateStatus(String, ProductStatus)` when transitioning ACTIVE → DISCONTINUED (valid)
- `ProductService.decrementStock(String, int)` when stock < quantity (DomainException)
- `ProductService.replaceSkus(String, List<Sku>)` when SKU count > 50 (DomainException from `Product.replaceSkus`)

Tests MUST use Mockito mocks for `ProductRepository` and `MeterRegistry`. The `MeterRegistry` mock allows `products.queried` counter to fire without an actual Micrometer backend.

#### Scenario: listPublic with null category queries by status
- **WHEN** the test stubs `productRepository.findByStatus(ProductStatus.ACTIVE, pageable)` returning 2 products
- **AND** calls `productService.listPublic(null, PageRequest.of(0, 20))`
- **THEN** the returned `Page<ProductResponse>` has 2 elements
- **AND** the `MeterRegistry` mock has `products.queried` counter incremented exactly 2 times

#### Scenario: decrementStock with insufficient stock throws
- **WHEN** the test stubs a product with `stock = 5`
- **AND** calls `productService.decrementStock("p-1", 10)`
- **THEN** `assertThatThrownBy` catches `DomainException` (raised by `Product.decrementStock`)

### Requirement: UserService and AdminBffService edge cases are covered
The system MUST add direct unit tests for:

- `UserService` (package `com.seafood.user.application`):
  - At least 1 test verifying role assignment on creation
  - 1 test verifying `findByOpenId(String)` returns Optional.empty for unknown openId
  - 1 test verifying `findByOpenId(String)` returns User when openId matches

- `AdminBffService` (package `com.seafood.bff.admin`):
  - `dashboard()` test that verifies the response aggregates counts from OrderService.countCreatedSince + ProductService.countByStock + UserService.count
  - `productStats()` test that verifies the response includes the `byCategory` map populated by ProductService.countByCategory
  - `orderDetail(orderId)` test that throws NotFoundException when order not found
  - At least 1 test verifying each helper aggregation (revenueToday, revenueWeek, lowStockList)

Tests MUST use Mockito for all service collaborators.

#### Scenario: AdminBffService.dashboard aggregates correctly
- **WHEN** the test stubs `orderService.countCreatedSince(today)` returning 5L
- **AND** stubs `productRepository.countByStock(0)` returning 2L
- **AND** stubs `userService.count()` returning 100L
- **AND** calls `adminBffService.dashboard()`
- **THEN** the returned `DashboardResponse.orderStats.today` equals 5L
- **AND** `lowStock` list size equals 2
- **THEN** the response is well-formed (no null required fields)

#### Scenario: AdminBffService.orderDetail throws NotFoundException
- **WHEN** the test stubs `orderService.get("o-missing")` throwing `NotFoundException`
- **AND** calls `adminBffService.orderDetail("o-missing")`
- **THEN** `assertThatThrownBy` catches `NotFoundException`

### Requirement: No regression in existing test suite
The system MUST ensure that adding the new service-layer unit tests does not break any existing test. The full backend test suite MUST pass with the new tests added.

#### Scenario: All existing tests still pass
- **WHEN** `./gradlew test -PexcludeTags=docker -x processTestAot` runs after the new tests are added
- **THEN** all 422 pre-existing tests still pass
- **AND** the new tests (≥ 15) also pass
- **THEN** total test count is ≥ 437
- **THEN** zero failures and zero errors

#### Scenario: No new dependencies added
- **WHEN** the new test files are committed
- **THEN** `backend/build.gradle` does not gain any new `testImplementation` dependency
- **THEN** all new tests use only `junit-jupiter` + `mockito-core` + `assertj-core` (all already in test classpath)
