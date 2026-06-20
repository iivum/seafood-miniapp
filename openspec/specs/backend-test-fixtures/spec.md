# backend-test-fixtures Specification

## Purpose
TBD - created by archiving change sprint-2-test-data-builders. Update Purpose after archive.
## Requirements
### Requirement: Five reusable test data builders
The system MUST provide five test data builder classes — `OrderBuilder`, `ProductBuilder`, `UserBuilder`, `CartBuilder`, `RefundBuilder` — located in `backend/src/test/java/com/seafood/testsupport/builders/`. Each builder MUST be a plain Java class (no Spring, no Lombok) with private mutable fields, a private constructor, a static `anXxx()` (or `aXxx()` for User/Product/Cart/Refund) factory method, a fluent `withXxx()` setter per core field, and a terminal `build()` method that constructs the corresponding domain record. Each builder MUST NOT introduce runtime dependencies into `main` source code.

#### Scenario: OrderBuilder default build
- **WHEN** a test calls `OrderBuilder.anOrder().build()`
- **THEN** the returned `Order` has id `"o-test"`, userId `"u-test"`, status `OrderStatus.Pending`, one default OrderItem (`三文鱼` × 2 at 99.00), totalAmount `198.00`, createdAt equals updatedAt equals `2026-06-01T00:00:00Z`, and all nullable fields (cancelReason, tracking, refundId, estimatedDelivery) are null

#### Scenario: OrderBuilder chain overrides
- **WHEN** a test calls `OrderBuilder.anOrder().withId("o1").withUserId("u1").withItems(List.of(item)).withTotalAmount(new BigDecimal("198.00")).build()`
- **THEN** the returned `Order` has id `"o1"`, userId `"u1"`, items equal to the provided list, totalAmount `198.00`, and all other fields at their defaults

#### Scenario: OrderBuilder produces independent instances on multiple build() calls
- **WHEN** a test calls `OrderBuilder.anOrder().build()` twice in succession
- **THEN** the two returned `Order` objects are distinct instances (`isNotSameAs`) but equal by value

#### Scenario: Builder result chains into record naming methods
- **WHEN** a test calls `OrderBuilder.anOrder().build().withEstimatedDelivery(Instant.parse("2026-06-02T00:00:00Z"))`
- **THEN** the returned `Order` has estimatedDelivery set to the parsed instant and all other fields at their builder defaults

#### Scenario: ProductBuilder default build returns ACTIVE product
- **WHEN** a test calls `ProductBuilder.aProduct().build()`
- **THEN** the returned `Product` has id `"p-test"`, name `"测试商品"`, price `99.00`, stock `100`, category `ProductCategory.Fish`, status `ProductStatus.ACTIVE`

#### Scenario: UserBuilder default build returns CUSTOMER user
- **WHEN** a test calls `UserBuilder.aUser().build()`
- **THEN** the returned `User` has id `"u-test"`, openId `"dev-open-test"`, nickname `"测试用户"`, role `Role.CUSTOMER`, empty addresses list

#### Scenario: CartBuilder default build returns empty cart
- **WHEN** a test calls `CartBuilder.aCart().build()`
- **THEN** the returned `Cart` has userId `"u-test"` and empty items list

#### Scenario: RefundBuilder default build returns Requested refund
- **WHEN** a test calls `RefundBuilder.aRefund().build()`
- **THEN** the returned `Refund` has id `"r-test"`, orderId `"o-test"`, userId `"u-test"`, amount `99.00`, reason `"不再需要"`, status `RefundStatus.Requested`

### Requirement: Core-field coverage excludes nullable record fields
Each builder MUST cover only the core fields that appear in 80% or more of test scenarios for the corresponding aggregate. Nullable fields that are not commonly set during testing (for example `Order.cancelReason`, `Order.tracking`, `Order.refundId`, `Order.estimatedDelivery`) MUST be left at `null` by the default builder and supplemented via the domain record's existing naming methods (such as `withEstimatedDelivery`) when a particular test needs them.

#### Scenario: Builder leaves nullable fields at null
- **WHEN** a test calls `OrderBuilder.anOrder().build()` and does not chain any additional record naming method
- **THEN** the returned `Order` has cancelReason, tracking, refundId, and estimatedDelivery all null

#### Scenario: Test supplements nullable field via record naming method
- **WHEN** a test calls `OrderBuilder.anOrder().build().withEstimatedDelivery(Instant.parse("2026-06-19T10:00:00Z"))`
- **THEN** the returned `Order` has estimatedDelivery `2026-06-19T10:00:00Z` while all other fields remain at builder defaults

### Requirement: OrderTest refactored to use OrderBuilder
The existing `backend/src/test/java/com/seafood/order/domain/OrderTest.java` MUST be refactored to construct its sample `Order` via `OrderBuilder.anOrder()` instead of the inline 11-arg `new Order(...)` constructor call. After the refactor, all existing `OrderTest` test methods MUST continue to pass without any change to test logic or assertions — only the fixture syntax changes.

#### Scenario: OrderTest sample() uses OrderBuilder
- **WHEN** a reviewer reads `OrderTest.java`
- **THEN** the `sample()` method body consists of an `OrderBuilder.anOrder()` chain followed by `.build()`, with no direct `new Order(...)` call

#### Scenario: OrderTest test suite passes after refactor
- **WHEN** `./gradlew :test --tests "com.seafood.order.domain.OrderTest"` runs after the refactor
- **THEN** all existing test methods pass with zero failures and zero errors, and no test method body has been modified (only the `sample()` helper changed)

### Requirement: No regression in backend full test suite
After the five builders are introduced and `OrderTest.sample()` is refactored, the full backend test suite MUST continue to pass with zero failures and zero errors. No `main` source file (under `backend/src/main/java`) MUST be modified.

#### Scenario: Full backend test suite remains green
- **WHEN** `./gradlew test` runs after this change
- **THEN** the build succeeds with all existing tests passing plus the 20 new builder test cases (six for OrderBuilder, four each for ProductBuilder and UserBuilder, three each for CartBuilder and RefundBuilder)

#### Scenario: No main source files modified
- **WHEN** `git diff --stat HEAD~6 backend/src/main/` is run after the change
- **THEN** the output is empty (no production code modified)

