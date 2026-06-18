# Tasks: Sprint 2 / 子项目 ② A — Backend Coverage Backfill

> 实施细节见 `plan.md`(16 task 全代码,Spring Boot 4 modern stack 强制)。本文件为 OpenSpec apply 入口的 checkbox 跟踪。

## 1. Experimental Pilot (validate Spring Boot 4 modern stack)

- [x] 1.1 Confirm `MockMvcTester` + `@WebMvcTest` (new pkg) + `@MockitoBean` are on test classpath — **DONE in c8fc506** (build.gradle pinned `spring-boot-webmvc-test:4.0.6` + `spring-test:7.0.8` + `spring-security-test:7.0.6` + `assertj-core:3.27.7`); no fallback to plain JUnit (mandatory modern stack per user)
- [ ] 1.2 Create `backend/src/test/java/com/seafood/product/api/ProductControllerSliceTest.java` per plan.md Task 1 (3 cases: list, get 404, discontinue ADMIN)
- [ ] 1.3 Run `./gradlew test --tests "com.seafood.product.api.ProductControllerSliceTest"` — expect 3 tests pass
- [ ] 1.4 Commit pilot

## 2. Other Controller Slices (5 classes)

- [ ] 2.1 Create `OrderControllerSliceTest.java` (4 cases: list CUSTOMER, get 404, ship 403 CUSTOMER, ship ADMIN)
- [ ] 2.2 Create `CartControllerSliceTest.java` (3 cases: get, clear 204, unauth 403)
- [ ] 2.3 Create `AdminOrderControllerSliceTest.java` (3 cases: batchShip ADMIN, exportCsv ADMIN, unauth 403)
- [ ] 2.4 Create `AdminProductControllerSliceTest.java` (3 cases: duplicate ADMIN, batchStatus empty 409, unauth 403)
- [ ] 2.5 Create `AdminRefundControllerSliceTest.java` (4 cases: listByStatus, approve ADMIN, reject ADMIN, unauth 403)
- [ ] 2.6 Commit Phase 2 (1 commit per task)

## 3. Repository Slices (4 classes, all extend MongoIntegrationTest, @Tag("docker"))

- [ ] 3.1 Create `OrderRepositorySliceTest.java` (4 cases: save/findById round-trip, findById empty, findByUserId filters, deleteById)
- [ ] 3.2 Create `ProductRepositorySliceTest.java` (4 cases: save round-trip, findByCategory, countByStatus, deleteById)
- [ ] 3.3 Create `UserRepositorySliceTest.java` (4 cases: save round-trip, findByOpenId happy/miss, deleteById)
- [ ] 3.4 Create `RefundRepositorySliceTest.java` (4 cases: save round-trip, findByOrderId, findByStatus(String), deleteById)
- [ ] 3.5 Run each repository test (`./gradlew test --tests "...*RepositorySliceTest"`) — requires Docker
- [ ] 3.6 Commit Phase 3 (1 commit per task)

## 4. BFF Integration (5 classes)

- [ ] 4.1 Create `AdminBffControllerSliceTest.java` (3 cases: dashboard ADMIN, productStats ADMIN, dashboard unauth 403)
- [ ] 4.2 Create `AdminBffOrderDetailSliceTest.java` (2 cases: orderDetail happy, orderDetail 404)
- [ ] 4.3 Create `AdminBffOrderListSliceTest.java` (1 case: dashboard.recentOrders — **reframed**, AdminBffController has no `orderList` endpoint)
- [ ] 4.4 Create `AdminBffProductDuplicateSliceTest.java` (1 case: productStats.byCategory — **reframed**, no `productDuplicate` on BFF; covered by AdminProductControllerSliceTest)
- [ ] 4.5 Create `AdminBffBatchShipSliceTest.java` (2 cases: ADMIN boundary defense — **reframed**, no `batchShip` on BFF; covered by AdminOrderControllerSliceTest)
- [ ] 4.6 Commit Phase 4 (1 commit per task)

## 5. Verification

- [ ] 5.1 Run full backend test suite: `cd backend && ./gradlew test` — expect zero failures, zero errors (~41 new tests)
- [ ] 5.2 Run `./gradlew check` — expect ArchUnit + `checkNoRefreshScope` PASS
- [ ] 5.3 Run `./gradlew jacocoTestReport` and verify global line coverage ≥80% (CLAUDE.md §3)
- [ ] 5.4 If coverage <80%, write `coverage-gap.md` listing uncovered classes/lines (do NOT add tests to chase threshold)
- [ ] 5.5 Grep audit: `grep -rnE "new (Order|Product|User|Cart|Refund)\(" backend/src/test/java/com/seafood/ --include="*.java"` — expect zero hits outside `builders/` package
- [ ] 5.6 Final commit marking change complete (per plan.md Task 16 Step 5)

## 6. Reference

- Spec: `specs/backend-test-coverage/spec.md` (5 Requirements, 12 Scenarios)
- Design: `design.md` (195 lines, 11 sections)
- Plan: `plan.md` (16 tasks with full code per task — modern Spring Boot 4 stack mandatory)
- Parent: `test-suite-roadmap/specs/test-roadmap/spec.md` Requirement 2 "Coverage gap closure for backend"
- Pilot dep validation: commit `c8fc506` (build.gradle pinned)
