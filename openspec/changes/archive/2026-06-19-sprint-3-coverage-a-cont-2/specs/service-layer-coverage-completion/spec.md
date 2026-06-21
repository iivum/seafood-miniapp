## ADDED Requirements

### Requirement: CartService has direct unit test coverage
The system MUST add direct unit tests for `CartService` (package `com.seafood.order.application`) that cover the basic CRUD operations and at least one exception path. Tests use Mockito to mock `CartRepository`.

#### Scenario: cartService_get_returnsStubbedCart
- **WHEN** the test stubs `cartRepository.findByUserId("u-1")` returning a Cart document for user "u-1"
- **AND** calls `cartService.get("u-1")`
- **THEN** the returned `CartResponse.userId()` equals "u-1"

#### Scenario: cartService_addItem_appendsToCart
- **WHEN** the test stubs `cartRepository.findByUserId("u-1")` returning a Cart with empty items
- **AND** calls `cartService.addItem("u-1", req)` with a CartItemRequest for product "p-1"
- **THEN** `verify(cartRepository).save(any())` is called with a Cart document containing "p-1" in items

#### Scenario: cartService_removeItem_returnsUpdatedCart
- **WHEN** the test stubs `cartRepository.findByUserId("u-1")` returning a Cart with items [p-1, p-2]
- **AND** calls `cartService.removeItem("u-1", "p-1")`
- **THEN** `verify(cartRepository).save(any())` is called with a Cart document NOT containing "p-1"

#### Scenario: cartService_clear_succeeds
- **WHEN** the test calls `cartService.clear("u-1")`
- **THEN** `verify(cartRepository).deleteByUserId("u-1")` is called once

### Requirement: ProductService SKU operations have direct unit test coverage
The system MUST add direct unit tests for `ProductService` SKU methods: `listSkus`, `replaceSkus`, `addSku`, `updateSku`, `removeSku`. Tests cover valid operations and at least one validation failure per method (e.g. SKU count limits).

#### Scenario: listSkus_productNotFound_throwsNotFound
- **WHEN** `productRepository.findById("p-missing")` returns `Optional.empty()`
- **AND** the test calls `productService.listSkus("p-missing")`
- **THEN** `assertThatThrownBy` catches `NotFoundException`

#### Scenario: replaceSkus_validCount_succeeds
- **WHEN** the test stubs a product document with 1 SKU
- **AND** calls `productService.replaceSkus("p-1", List.of(sku1, sku2))` with 2 SKUs
- **THEN** `verify(productRepository).save(any())` is called with a document having 2 SKUs

#### Scenario: replaceSkus_tooMany_throwsDomainException
- **WHEN** the test stubs a product document
- **AND** calls `productService.replaceSkus("p-1", listOf51Skus)`
- **THEN** `assertThatThrownBy` catches `DomainException` (raised by `Product.replaceSkus` validation)

#### Scenario: addSku_appendedWithSortOrder
- **WHEN** the test stubs a product with 1 SKU (sortOrder=0)
- **AND** calls `productService.addSku("p-1", newSku)`
- **THEN** `verify(productRepository).save(any())` is called with a document having 2 SKUs where new SKU has sortOrder=1

#### Scenario: removeSku_reordersRemaining
- **WHEN** the test stubs a product with 3 SKUs (sortOrder 0, 1, 2)
- **AND** calls `productService.removeSku("p-1", "sku-middle")` (the sortOrder=1 SKU)
- **THEN** the saved document has SKUs at sortOrder 0, 1 (re-ordered, no gaps)

### Requirement: OrderService state machine branches have direct unit test coverage
The system MUST add direct unit tests for `OrderService` state machine actions: `cancel`, `markPaid`, `confirmReceive`, `rebuy`, `requestRefund`. Tests verify valid transitions succeed and invalid transitions throw `DomainException`.

#### Scenario: cancel_paidOrder_succeeds
- **WHEN** the test stubs a PAID order document
- **AND** calls `orderService.cancel("o-1", "user changed mind")`
- **THEN** the returned `OrderResponse.status()` equals "CANCELLED"
- **AND** `cancelReason()` equals "user changed mind"

#### Scenario: markPaid_pendingOrder_succeeds
- **WHEN** the test stubs a PENDING order document
- **AND** calls `orderService.markPaid("o-1")`
- **THEN** the returned `OrderResponse.status()` equals "PAID"

#### Scenario: confirmReceive_shippedOrder_succeeds
- **WHEN** the test stubs a SHIPPED order document
- **AND** calls `orderService.confirmReceive("o-1")`
- **THEN** the returned `OrderResponse.status()` equals "DELIVERED" (or whatever the terminal name is)

#### Scenario: rebuy_paidOrder_returnsCartItems
- **WHEN** the test stubs a PAID order with 2 items
- **AND** calls `orderService.rebuy("o-1")`
- **THEN** the returned `List<CartItemResponse>` has 2 elements

#### Scenario: requestRefund_deliveredOrder_createsRefund
- **WHEN** the test stubs a DELIVERED order with totalAmount=100.00
- **AND** calls `orderService.requestRefund("o-1", new BigDecimal("50.00"), "test")`
- **THEN** the returned `RefundResponse.amount()` equals 50.00
- **AND** `verify(refundRepository).save(any())` is called

### Requirement: AdminBffService dashboard helpers have direct unit test coverage
The system MUST add direct unit tests for `AdminBffService.dashboard()` internal helpers: `topProducts()`, `trend7d()`, and the lowStock aggregation via `ProductQueryService.lowStock()`.

#### Scenario: topProducts_aggregatesByQuantity
- **WHEN** the test stubs `orderService.findRecent(500)` returning orders with overlapping productIds in items
- **AND** stubs `productService.get(id)` for each top productId
- **AND** calls `bffService.dashboard()`
- **THEN** `dashboardResponse.topProducts()` contains the highest-quantity product first
- **AND** each TopProductResponse has a non-null `product` field

#### Scenario: topProducts_handlesMissingProductGracefully
- **WHEN** the test stubs `orderService.findRecent(500)` returning an order with productId "p-deleted"
- **AND** stubs `productService.get("p-deleted")` to throw `NotFoundException`
- **AND** calls `bffService.dashboard()`
- **THEN** `dashboardResponse.topProducts()` does NOT contain "p-deleted" (skipped via catch in service)
- **AND** no exception is thrown

#### Scenario: trend7d_returns7Points
- **WHEN** the test stubs `productQueryService.findTrend7d()` returning 7 `TrendPointResponse` entries
- **AND** calls `bffService.dashboard()`
- **THEN** `dashboardResponse.trend7d()` has 7 elements

#### Scenario: lowStock_respectsThreshold
- **WHEN** the test stubs `productQueryService.lowStock(10)` returning 3 products
- **AND** calls `bffService.dashboard()`
- **THEN** `dashboardResponse.lowStock()` has at most TOP_N (10) elements
- **AND** all elements have stock < 10

### Requirement: Jacoco threshold re-raised to 0.80
After this change, the system MUST have `jacocoTestCoverageVerification` configured with `minimum = 0.80` (CLAUDE.md §3 hard rule). The `coverage-gap.md` tracking file in `openspec/changes/sprint-3-ci-speed/` MUST be deleted (purpose served).

#### Scenario: Coverage gate at 0.80 passes
- **WHEN** `./gradlew check -PexcludeTags=docker -x processTestAot` runs after this change lands
- **THEN** `jacocoTestCoverageVerification` reports `instructions covered ratio` ≥ 0.80
- **THEN** `./gradlew check` exits with status 0

#### Scenario: coverage-gap.md deleted
- **WHEN** the developer looks at `openspec/changes/sprint-3-ci-speed/`
- **THEN** the file `coverage-gap.md` is no longer present
- **THEN** the directory contains only the original archived change artifacts (proposal / design / tasks / specs)

### Requirement: No regression in existing test suite
The system MUST ensure that the new service-layer unit tests do not break any existing test. The full backend test suite MUST pass with the new tests added.

#### Scenario: All existing tests still pass
- **WHEN** `./gradlew test -PexcludeTags=docker -x processTestAot` runs after the new tests are added
- **THEN** all 440 pre-existing tests (from Sprint 2 A + Sprint 3 A 续) still pass
- **AND** the new tests (≥ 15) also pass
- **THEN** total test count is ≥ 455
- **THEN** zero failures and zero errors
