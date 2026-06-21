# Sprint 3 A 续-2 — Service-Layer Coverage Completion Design

> 2026-06-19 · 父路线图:`openspec/changes/test-suite-roadmap/design.md` §3.2 Sprint 3
> 状态: 设计已批准,待 apply

## 1. 背景

Sprint 3 A 续(`sprint-3-coverage-a-cont`)补 18 个 service-layer cases,coverage 从 75% → 76.5%。剩 3.5% 缺口在 4 个 service 的边缘路径:

1. **CartService** — 0% service-layer test(Sprint 2 A 只测 controller)
2. **ProductService** SKU 系列方法 — 5 个方法未测(`listSkus` / `replaceSkus` / `addSku` / `updateSku` / `removeSku`)
3. **OrderService** state machine — `OrderAction` 6 分支只测 ship
4. **AdminBffService** dashboard 内部 helpers — `topProducts` / `trend7d` / `lowStock` aggregation

本 change 收口 3.5% 缺口 + 升 `jacocoTestCoverageVerification` 阈值 0.75 → 0.80(CLAUDE.md §3 硬规则)+ 删 `coverage-gap.md`。

## 2. 范围(4 个新 test 类,~17 cases)

| 测试类 | 目标 service | 测的路径 | 估计 case |
|---|---|---|---|
| `CartServiceSliceTest` | CartService | get / addItem / removeItem / clear | 4 |
| `ProductServiceSkuSliceTest` | ProductService | listSkus not-found / replaceSkus valid+tooMany / addSku / removeSku re-order | 5 |
| `OrderServiceStateMachineSliceTest` | OrderService | cancel / markPaid / confirmReceive / rebuy / requestRefund | 5 |
| `AdminBffDashboardSliceTest` | AdminBffService | topProducts aggregation / topProducts catch missing / trend7d / lowStock | 3-4 |

**总**:4 个新 test 类,17-18 cases,0 新依赖。

## 3. 设计

### 3.1 测试模板(沿用 A 续已建立的)

```java
@ExtendWith(MockitoExtension.class)
class CartServiceSliceTest {
    @Mock private CartRepository cartRepository;
    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository);
    }
    // + test methods
}
```

要点:
- `@ExtendWith(MockitoExtension.class)` + `@Mock` — Mockito 5 + JUnit 5
- 构造在 `@BeforeEach` 显式做(避免 `private final` 初始化早于 `@Mock` 注入)
- `lenient().when(...)` 模式 for 跨 test 的 meter/counter stub

### 3.2 CartServiceSliceTest(4 cases)

| # | Test | Stub |
|---|---|---|
| 1 | `get_returnsStubbedCart` | findByUserId("u-1") → Cart doc |
| 2 | `addItem_appendsToCart` | findByUserId("u-1") empty items → verify save 包含 p-1 |
| 3 | `removeItem_returnsUpdatedCart` | findByUserId("u-1") items [p-1, p-2] → verify save 不含 p-1 |
| 4 | `clear_succeeds` | verify deleteByUserId("u-1") 调一次 |

注: CartService ctor 接受 CartRepository,简单。

### 3.3 ProductServiceSkuSliceTest(5 cases)

| # | Test | Stub |
|---|---|---|
| 1 | `listSkus_productNotFound_throwsNotFound` | findById("p-missing") empty |
| 2 | `replaceSkus_validCount_succeeds` | doc with 1 SKU, call replaceSkus([sku1, sku2]) → verify save 有 2 SKUs |
| 3 | `replaceSkus_tooMany_throwsDomainException` | doc, call replaceSkus(51 skus) → DomainException |
| 4 | `addSku_appendedWithSortOrder` | doc with 1 SKU (sortOrder=0), addSku(newSku) → verify save 有 2 SKUs, new SKU sortOrder=1 |
| 5 | `removeSku_reordersRemaining` | doc with 3 SKUs (sortOrder 0,1,2), removeSku("sku-1") → verify save 2 SKUs at sortOrder 0,1 |

### 3.4 OrderServiceStateMachineSliceTest(5 cases)

| # | Test | Stub |
|---|---|---|
| 1 | `cancel_paidOrder_succeeds` | doc status=PAID → cancel → status=CANCELLED + reason |
| 2 | `markPaid_pendingOrder_succeeds` | doc status=PENDING → markPaid → status=PAID |
| 3 | `confirmReceive_shippedOrder_succeeds` | doc status=SHIPPED → confirmReceive → status=DELIVERED |
| 4 | `rebuy_paidOrder_returnsCartItems` | doc items=[i1,i2] status=PAID → rebuy → list size 2 |
| 5 | `requestRefund_deliveredOrder_createsRefund` | doc totalAmount=100 status=DELIVERED → requestRefund(50) → RefundResponse.amount=50 + verify save |

注: state machine 测试用 `OrderDocument` stub(直接设 status string),不 mock 整个 Order。`markPaid` / `confirmReceive` 等方法内部会调 `Order.markXxx()` 做 state machine 校验。

### 3.5 AdminBffDashboardSliceTest(3-4 cases)

| # | Test | Stub |
|---|---|---|
| 1 | `topProducts_aggregatesByQuantity` | findRecent(500) orders with overlapping productIds, products.get(id) for each top → dashboard topProducts[0] 是最高量 product |
| 2 | `topProducts_handlesMissingProduct` | findRecent with productId "p-deleted", products.get("p-deleted") throw NotFoundException → topProducts 不含 "p-deleted" |
| 3 | `trend7d_returns7Points` | productQueryService.findTrend7d() → 7 TrendPointResponse → dashboard.trend7d size 7 |
| 4 | `lowStock_respectsThreshold` | productQueryService.lowStock(10) → 3 products → dashboard.lowStock ≤ TOP_N |

### 3.6 build.gradle 阈值调整

```groovy
jacocoTestCoverageVerification {
    dependsOn jacocoTestReport
    violationRules {
        rule {
            // A 续-2 完成后升回 CLAUDE.md §3 目标阈值
            limit { minimum = 0.80 }
        }
    }
}
```

### 3.7 coverage-gap.md 删除

`rm openspec/changes/sprint-3-ci-speed/coverage-gap.md` — 跟踪文件 purpose 结束。

## 4. TDD 顺序

| 步 | 任务 | 估时 |
|---|---|---|
| 1 | 写 `CartServiceSliceTest`(4 cases) — pilot | 30min |
| 2 | 写 `ProductServiceSkuSliceTest`(5 cases) | 1h |
| 3 | 写 `OrderServiceStateMachineSliceTest`(5 cases) | 1h |
| 4 | 写 `AdminBffDashboardSliceTest`(3-4 cases) | 1h |
| 5 | 改 `backend/build.gradle` threshold 0.75 → 0.80 | 1min |
| 6 | 删 `openspec/changes/sprint-3-ci-speed/coverage-gap.md` | 1min |
| 7 | 跑 `./gradlew check` 验 gate @ 0.80 通过 | 2min |
| 8 | 4 commit + final check + archive | 30min |

**总估时**:4-5h,留 1h 余量给 fallback。

## 5. 风险 + Fallback

| 风险 | Fallback |
|---|---|
| `ProductService.replaceSkus > 50` 边界已测(A 续),本 change focus 其他 SKU 方法 | 1-2 个 case 失败就降规模,只测核心 3-4 方法 |
| `OrderService.markPaid` 等需 Order state machine 实际工作,可能需要更复杂的 Order stub | 用 `OrderBuilder.anOrder().build()` + `OrderMapper.toDocument()` 拿到真实 doc,只覆盖状态机成功路径 |
| `AdminBffService.dashboard()` 内部 `topProducts()` catch NotFoundException 是 private helper,需通过 dashboard() 公开方法测 | 已有 spec 设计 — 通过 productService.get 抛 NotFoundException 触发 catch |
| 阈值升 0.80 后仍 < 80% | 降回 0.78,延后;再补 case 提 0.80(2-3h 内可解) |

## 6. 完成判据

- [ ] 4 个新 test 类(17-18 cases)全 PASS
- [ ] `./gradlew check -PexcludeTags=docker -x processTestAot` PASS(Jacoco gate @ 0.80 通过)
- [ ] 440 pre-existing tests + 17+ new tests = 457+ tests,0 failure
- [ ] `backend/build.gradle` `minimum` 从 0.75 升到 0.80
- [ ] `coverage-gap.md` 删除
- [ ] 5-6 commits(每 test 类 1 commit + 1 build.gradle + 1 coverage-gap delete + 1 doc)

## 7. YAGNI(明确不做)

- ❌ Sprint 4 D3 coverage dashboard(独立 sub-change)
- ❌ Sprint 4 C1 PIT mutation(独立 sub-change)
- ❌ 测所有 Cart 边缘(只测 4 个核心方法)
- ❌ 测所有 OrderAction(本 change 5 个,留 `remindShip` 给未来)
- ❌ 加新依赖(stay on `junit-jupiter` + `mockito-core` + `assertj-core`)

## 8. 文件清单

### 新建(4)
- `backend/src/test/java/com/seafood/order/application/CartServiceSliceTest.java`
- `backend/src/test/java/com/seafood/product/application/ProductServiceSkuSliceTest.java`
- `backend/src/test/java/com/seafood/order/application/OrderServiceStateMachineSliceTest.java`
- `backend/src/test/java/com/seafood/bff/admin/AdminBffDashboardSliceTest.java`

### 改(2)
- `backend/build.gradle` `minimum = 0.75` → `0.80`
- 删 `openspec/changes/sprint-3-ci-speed/coverage-gap.md`

### 不动
- 14 个 Sprint 2 controller slice / repo slice / BFF test
- 1 个 Sprint 3 B 修的 SensitiveValueBeanSerializerModifierTest
- 4 个 Sprint 3 A 续 service-layer test

## 9. 关联

- **父**:`test-suite-roadmap/design.md` §3.2 Sprint 3 — A(续)+ B
- **前置**:
  - `sprint-2-test-data-builders`(D1)
  - `sprint-2-backend-coverage`(A 后端)
  - `sprint-3-ci-speed`(B + 遗留)
  - `sprint-3-coverage-a-cont`(A 续)— 18 cases 把 coverage 提到 76.5%
- **后续**:
  - Sprint 4 D3 coverage dashboard 用本 change 升回 80% 的 baseline
  - Sprint 4 C1 PIT mutation 跑本 change 升完的测试集
- **完成判据**:本 change 末勾 `test-suite-roadmap/tasks.md` T5 + 删 coverage-gap.md
