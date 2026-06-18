# Sprint 3 A 续 — Service-Layer Unit Test Backfill Design

> 2026-06-19 · 父路线图:`openspec/changes/test-suite-roadmap/design.md` §3.2 Sprint 3
> 状态: 设计已批准,待 writing-plans / apply

## 1. 背景

Sprint 3 B(`sprint-3-ci-speed`)Jacoco gate 落地时实测 global line coverage = 75%(HTML),`./gradlew check` 卡在 76% < 80% 阈值。`backend/build.gradle` 临时降到 0.75 才过 gate,**CLAUDE.md §3 硬规则 80% 仍未达标**。

5% 缺口(见 `openspec/changes/sprint-3-ci-speed/coverage-gap.md`)集中在 **service layer**:
- `OrderService` 直接单测 = 0%(Sprint 2 只测了 controller)
- `ProductService` 直接单测 = 0%
- `UserService` 直接单测 = 0%
- `AdminBffService` 直接单测 = 0%

Controller slice test 走 mock service,repository slice test 走 raw Mongo,Service 内部逻辑(edge case + 异常 catch 路径)未覆盖。

## 2. 范围

| 类别 | 数量 | 目标 service / 路径 |
|---|---|---|
| `OrderService` 直接 unit test | 6+ cases | batchShip partial / findRecent truncation / listRefunds empty / renderPicklist not-found / requestRefund over-amount / rebuy cancelled |
| `ProductService` 直接 unit test | 6+ cases | listPublic null/non-null / update not-found / updateStatus valid / decrementStock over-quantity / replaceSkus > 50 / duplicate name conflict |
| `UserService` 直接 unit test | 3+ cases | role assignment on create / findByOpenId empty / findByOpenId found |
| `AdminBffService` 直接 unit test | 4+ cases | dashboard aggregation / productStats byCategory / orderDetail not-found / helper aggregations |
| `build.gradle` 阈值调整 | 1 | `minimum = 0.75` → `minimum = 0.80` |
| `coverage-gap.md` 同步 | 1 | mark A 续 完成 |

**总**:4 个新 test 类,~19 test cases,0 新依赖。

## 3. 设计

### 3.1 测试技术栈

复用现有依赖(0 新增):
- `org.junit.jupiter.api.Test` + `@ExtendWith(MockitoExtension.class)` — 已在 classpath
- `org.mockito.Mockito.mock(...)` + `when(...).thenReturn(...)` — 已在 classpath
- `org.assertj.core.api.Assertions.assertThat/assertThatThrownBy` — 已在 classpath
- D1 builders(`OrderBuilder` / `ProductBuilder` / `UserBuilder` / `RefundBuilder`)— 已在 `com.seafood.testsupport.builders`

不使用 `@SpringBootTest`(不需要 Spring 上下文)也不使用 `@WebMvcTest`(只测 service)。

### 3.2 测试模板(以 OrderService 为例)

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceSliceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CartRepository cartRepository;
    @Mock private ProductRepository productRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private io.micrometer.core.instrument.MeterRegistry meterRegistry;
    // 用 5-arg constructor(支持 refund methods)
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, cartRepository, productRepository, refundRepository, meterRegistry);
        // meterRegistry 默认 mock,counter() 返 Mock Counter
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(mock(io.micrometer.core.instrument.Counter.class));
    }

    @Test
    void batchShip_partialFailure_reportsCounts() {
        var saved = new OrderDocument();
        saved.setId("o-1");
        saved.setStatus("PAID");
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(saved));
        when(orderRepository.findById("o-missing")).thenReturn(Optional.empty());
        when(orderRepository.save(any())).thenReturn(/* updated SHIPPED doc */);

        var resp = orderService.batchShip(List.of("o-1", "o-missing"), "SF", "TRK");
        assertThat(resp.successCount()).isEqualTo(1);
        assertThat(resp.failedCount()).isEqualTo(1);
        assertThat(resp.failed().get(0).orderId()).isEqualTo("o-missing");
    }
}
```

要点:
- `@Mock` + `@ExtendWith(MockitoExtension.class)` — JUnit 5 + Mockito 5 标准
- 5-arg `OrderService` constructor — 之前 design 已确认两个 constructor,5-arg 包含 `RefundRepository`(支持 refund methods)
- `MeterRegistry` mock 返 mock `Counter`,因为 `listPublic` / `findRecent` 调 `meterRegistry.counter(...)` 加 1

### 3.3 OrderService 6 cases(详细)

| # | Test | Stub 关键 |
|---|---|---|
| 1 | `batchShip_partialFailure_reportsCounts` | findById(o-1) OK + findById(o-missing) empty |
| 2 | `findRecent_truncatesTo500` | findTop500ByOrderByCreatedAtDesc 返 500 docs |
| 3 | `listRefunds_emptyStatus_returnsAll` | refundRepo.findByStatus("", any) 返 Page 3 elements |
| 4 | `renderPicklistHtml_notFound` | orders.get(o-missing) throw NotFoundException |
| 5 | `requestRefund_amountExceedsTotal` | order totalAmount=50, request 100 → DomainException |
| 6 | `rebuy_cancelledOrder` | order status CANCELLED → rebuy 返空 list(或不抛) |

### 3.4 ProductService 6 cases(详细)

| # | Test | Stub 关键 |
|---|---|---|
| 1 | `listPublic_nullCategory_queriesByStatus` | findByStatus(ACTIVE) 返 2 products |
| 2 | `listPublic_nonNullCategory_queriesByCategory` | findByCategory("鱼类") 返 1 |
| 3 | `update_productNotFound` | findById(o-missing) empty |
| 4 | `updateStatus_validTransition` | order ACTIVE → DISCONTINUED OK |
| 5 | `decrementStock_insufficient` | stock=5, decrement 10 → DomainException |
| 6 | `replaceSkus_tooMany` | skus.size=51 → DomainException |

### 3.5 UserService 3 cases

| # | Test | Stub 关键 |
|---|---|---|
| 1 | `create_assignsCustomerRoleByDefault` | role 缺省 → Role.CUSTOMER |
| 2 | `findByOpenId_unknownOpenId_returnsEmpty` | userRepository.findByOpenId("missing") empty |
| 3 | `findByOpenId_knownOpenId_returnsUser` | findByOpenId("known") 返 User |

### 3.6 AdminBffService 4 cases

| # | Test | Stub 关键 |
|---|---|---|
| 1 | `dashboard_aggregatesCounts` | orderService.countCreatedSince(今天) + productRepo.countByStock(0) + userService.count() |
| 2 | `productStats_includesByCategory` | 返 byCategory Map |
| 3 | `orderDetail_orderNotFound` | orderService.get("missing") throw NotFoundException |
| 4 | `dashboard_recentOrders_paged` | orderService.findRecent(10) 返 List 10 orders |

### 3.7 `build.gradle` 阈值调整

```groovy
jacocoTestCoverageVerification {
    dependsOn jacocoTestReport
    violationRules {
        rule {
            // A 续 完成后升回 CLAUDE.md §3 目标阈值
            limit { minimum = 0.80 }
        }
    }
}
```

`coverage-gap.md` 同步更新:
```markdown
## A 续 完成
- 4 个 service-layer unit test 类新增(~19 cases)
- Jacoco global line coverage: 75% → 80%+
- `backend/build.gradle` `minimum` 升回 0.80
- Sprint 3 末 4/4 子项目(D1 + A + B + A 续)全部归档
```

## 4. TDD 顺序

| 步 | 任务 | 估时 | 备注 |
|---|---|---|---|
| 1 | 写 `OrderServiceSliceTest`(6 cases) — 实验性 pilot | 1h | 验 `@ExtendWith(MockitoExtension.class)` + 5-arg constructor + MeterRegistry mock 跑通 |
| 2 | 写 `ProductServiceSliceTest`(6 cases) | 1h | 复用 OrderService 模板 |
| 3 | 写 `UserServiceSliceTest`(3 cases) | 0.5h | 简单 |
| 4 | 写 `AdminBffServiceSliceTest`(4 cases) | 1h | 多个 mock 协作者 |
| 5 | 改 `backend/build.gradle` 阈值 0.75 → 0.80 | 1min | 一行 |
| 6 | 跑 `./gradlew check -PexcludeTags=docker -x processTestAot` | 1min | 验 gate 通过 |
| 7 | 更新 `openspec/changes/sprint-3-ci-speed/coverage-gap.md` | 5min | mark A 续 完成 |
| 8 | 5 commit + final check + archive | 0.5h | |

**总估时**:4-5h,留 1h 余量。

## 5. 风险 + Fallback

| 风险 | Fallback |
|---|---|
| `OrderService` 5-arg constructor 接受 5 个 mock,但内部有 4-arg 兼容路径,可能 mock 错 | 跑测试验证;fail 时 fallback 4-arg constructor(skip refund methods 测试) |
| `MeterRegistry` mock 不返真实 Counter,`listPublic` NPE | stub `meterRegistry.counter(...)` 返 `mock(Counter.class)`,stub `counter.increment()` do-nothing(默认) |
| 阈值升 0.80 后仍 fail(coverage 75% → 80% 不够) | 降回 0.78,延后 A 续;或继续补 test(本 change scope 内可加 2-3 个 case) |
| `ProductService.listPublic` 内部对 null `category` 短路返 `findByStatus(ACTIVE)`,non-null 走 `findByCategory` + 强制 status | 测试明确 stub 两个 path,验证不同 repo method 被调 |

## 6. 完成判据

- [ ] 4 个新 test 类(19+ cases)全 PASS
- [ ] `./gradlew check -PexcludeTags=docker -x processTestAot` PASS(Jacoco gate @ 0.80 通过)
- [ ] 422 pre-existing tests + 19+ new tests = 441+ tests,0 failure
- [ ] `backend/build.gradle` `minimum` 从 0.75 升到 0.80
- [ ] `coverage-gap.md` mark A 续 完成
- [ ] 5 commits(每 test 类 1 commit + 1 build.gradle commit + 1 doc commit)+ final archive

## 7. YAGNI(明确不做)

- ❌ Sprint 4 D3 coverage dashboard(独立 sub-change)
- ❌ Sprint 4 C1 PIT mutation(独立 sub-change)
- ❌ 补 DTO/service-layer 之外的 coverage(application/dto 100% 已被排除)
- ❌ 加新依赖(stay on `junit-jupiter` + `mockito-core` + `assertj-core`)

## 8. 文件清单

### 新建(4)
- `backend/src/test/java/com/seafood/order/application/OrderServiceSliceTest.java`
- `backend/src/test/java/com/seafood/product/application/ProductServiceSliceTest.java`
- `backend/src/test/java/com/seafood/user/application/UserServiceSliceTest.java`
- `backend/src/test/java/com/seafood/bff/admin/AdminBffServiceSliceTest.java`

### 改(2)
- `backend/build.gradle` `minimum = 0.75` → `0.80`
- `openspec/changes/sprint-3-ci-speed/coverage-gap.md` 同步 mark A 续完成

### 不动
- 14 个 Sprint 2 controller slice / repo slice / BFF test(只跑,不改)
- 1 个 Sprint 3 B 修的 SensitiveValueBeanSerializerModifierTest(只跑,不改)

## 9. 关联

- **父**:`test-suite-roadmap/design.md` §3.2 Sprint 3 — A 续 + B
- **前置**:
  - `sprint-2-test-data-builders`(D1)— builders
  - `sprint-2-backend-coverage`(A 后端)— 14 test 文件 75% baseline
  - `sprint-3-ci-speed`(B + 遗留)— Jacoco plugin + 阈值 0.75 + 5% gap 跟踪
- **后续**:
  - Sprint 4 D3 coverage dashboard 用本 change 升回 80% 的 baseline
  - Sprint 4 C1 PIT mutation 跑本 change 升完的测试集
- **完成判据**:本 change 末勾 `test-suite-roadmap/tasks.md` T5(全部 4 子项目归档)
