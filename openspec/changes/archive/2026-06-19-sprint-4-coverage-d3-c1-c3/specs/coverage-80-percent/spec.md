## ADDED Requirements

### Requirement: Jacoco line coverage reaches 80% threshold
The system MUST have at least 80% Jacoco global line coverage (CLAUDE.md §3 hard rule) and the `jacocoTestCoverageVerification` threshold MUST be re-raised from the temporary 0.79 to 0.80. The 0.5% residual gap documented in `openspec/changes/sprint-3-coverage-a-cont-2/coverage-gap.md` MUST be closed by adding the missing test cases.

#### Scenario: Coverage gate at 0.80 passes
- **WHEN** `./gradlew check -PexcludeTags=docker -x processTestAot` runs
- **THEN** `jacocoTestCoverageVerification` reports `instructions covered ratio` ≥ 0.80
- **THEN** the build exits with status 0
- **THEN** the report at `build/reports/jacoco/test/html/index.html` shows the Total coverage row at or above 80%

#### Scenario: coverage-gap.md is deleted
- **WHEN** the developer looks at `openspec/changes/sprint-3-coverage-a-cont-2/`
- **THEN** the file `coverage-gap.md` is no longer present
- **THEN** the directory contains only the original archived change artifacts (proposal / design / tasks / specs)

### Requirement: OrderService.requestRefund state machine is covered
The system MUST add a direct unit test for `OrderService.requestRefund(...)` exercising the state machine transition (e.g. PAID → REFUNDING). The test stubs `orderRepository.findById`, `orderRepository.save`, and `refundRepository.save`, sets the security context to ADMIN, and asserts the returned `RefundResponse` has the expected amount and a `Refund` document was saved.

#### Scenario: requestRefund_paidOrder_returnsRefundResponse
- **WHEN** the test sets ADMIN security context via `SecurityContextHolder`
- **AND** stubs an order with status=PAID and totalAmount=100.00
- **AND** calls `orderService.requestRefund("o-1", BigDecimal("50.00"), "test")`
- **THEN** the returned `RefundResponse.amount()` equals 50.00
- **AND** `verify(refundRepository).save(any())` is called

#### Scenario: requestRefund_alreadyRefundedOrder_throwsDomainException
- **WHEN** the test sets ADMIN security context
- **AND** stubs an order with status=REFUNDED
- **AND** calls `orderService.requestRefund("o-1", BigDecimal("50.00"), "test")`
- **THEN** `assertThatThrownBy` catches `DomainException` (state machine rejects duplicate refund)

### Requirement: No regression in existing test suite
The new test case(s) for `requestRefund` MUST NOT break any existing test. The full backend test suite MUST pass after the addition.

#### Scenario: All existing tests still pass
- **WHEN** `./gradlew test -PexcludeTags=docker -x processTestAot` runs after the new test is added
- **THEN** all 457 pre-existing tests still pass
- **AND** the new test(s) also pass
- **THEN** total test count is ≥ 458
- **THEN** zero failures and zero errors
