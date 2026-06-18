# Sprint 2 / 子项目 ② A — Backend Coverage Backfill Design

> 2026-06-18 · 父路线图:`openspec/changes/test-suite-roadmap/design.md` §2.1 子项目 ②
> 状态: 设计已批准, 待 writing-plans → apply

## 1. 背景

Sprint 1 closure 后 backend 77 测试,但有显著缺口:
- **Controller slice 只 2/8**(`AdminCookieAuthController` / `AuthControllerLogout` 两个 user 模块的 controller test)— 业务模块(product / order / cart / bff admin)零覆盖
- **Repository slice = 0**(`@DataMongoTest` Spring Boot 4.0.6 starter-test 不 bundle — 走 `MongoIntegrationTest` base class raw MongoClient 模式)
- **BFF integration 只 1**(`AdminBffServiceTest` 服务级)— `/api/admin/orders` / `/api/admin/products` 等端到端路径零测试

不补完,改 controller / repository 时无 safety net,refactor 风险高。父路线图 §3.2 明确 Sprint 2 包含"D1 + A 后端部分",**Sprint 2 第二个 sub-change 启动 A**。

## 2. 范围(15+ 新测试)

| 类别 | 数量 | 目标 controller / repository / endpoint |
|---|---|---|
| **Controller slice** | 6 | ProductController / OrderController / CartController / AdminOrderController / AdminProductController / AdminRefundController |
| **Repository slice** | 4 | OrderRepository / ProductRepository / UserRepository / RefundRepository |
| **BFF integration** | 5 | `/api/admin/dashboard` / `/api/admin/orders/{id}/detail` / `/api/admin/orders` / `/api/admin/products/{id}/duplicate` / `/api/admin/orders/batch-ship` |

**总**:15 个测试类,~30+ 测试 case。

## 3. Controller slice 工具(Spring Boot 4 现代生态)

### 3.1 选型依据(find-docs 验证)

查 Spring Boot 4.0.0 官方文档(via Context7 /spring-projects/spring-boot/v4.0.0):

- **`@WebMvcTest`** 仍官方推荐(Controller slice 标准方式)
- **`@MockBean` 老包 `org.springframework.boot.test.mock.mockito.MockBean` 不可用** — 改用 Spring Boot 4 新包 `org.springframework.test.context.bean.override.mockito.MockitoBean`
- **`MockMvcTester`** 新 API,AssertJ 链式断言,比老 `MockMvc.perform()` 简洁

**结论**:走 Spring Boot 4 现代生态(`@WebMvcTest` + `@MockitoBean` + `MockMvcTester`),跟得上时代,CLAUDE.md 老 gotcha 不再生效。

### 3.2 模板

```java
@WebMvcTest(ProductController.class)
class ProductControllerSliceTest {

    @Autowired private MockMvcTester mockMvc;
    @MockitoBean private ProductService productService;

    @Test
    void listPublicProducts_returnsProductList() { /* happy path */ }

    @Test
    void getProduct_notFound_returns404() { /* 404 path */ }
}
```

要点:
- `@WebMvcTest(ProductController.class)` 只装 MVC + 指定 Controller;**Service / Repository 不会被加载**(CLAUDE.md §1 第④条 controllers 不可持 Repository + DDD 分层)
- `@MockitoBean` 注入 mock 的 Service(替代老 `@MockBean`)
- `MockMvcTester` 用 AssertJ 链式断言
- Service 返回值用 **D1 新建的 ProductBuilder** 造(不是 `new ProductResponse(...)`)— 体现"D1 是 A 的地基"

## 4. Repository slice 模板(`MongoIntegrationTest` 复用)

```java
class OrderRepositorySliceTest extends MongoIntegrationTest {

    @Autowired private OrderRepository orders;

    @Test
    void save_thenFindById_returnsSameOrder() {
        var saved = orders.save(OrderMapper.toDocument(OrderBuilder.anOrder()
            .withId("o-test-1").withUserId("u-1").build()));
        var found = orders.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo("u-1");
    }
    // + 3 more cases per repo:findById unknown / deleteById / existsById
}
```

要点:
- `extends MongoIntegrationTest`(D1 已建 base class,`@Container static` mongo:7)— Spring Boot 4 starter-test 不含 `@DataMongoTest`,必须走 raw `MongoClient` 模式
- 走 Spring Data Mongo 真实路径(不是 mock)— 测 mongo 实际集成行为
- 4 repo × 4 cases = 16 cases(`save` / `findById` happy / `findById` empty / `deleteById` / `existsById`)
- entity 用 **D1 OrderBuilder** 造

## 5. BFF integration 模板(同 controller slice 风格)

```java
@WebMvcTest(AdminBffController.class)
class AdminBffControllerSliceTest {

    @Autowired private MockMvcTester mockMvc;
    @MockitoBean private AdminBffService bff;

    @Test
    @WithMockUser(roles = "ADMIN")
    void orderDetail_returnsOrderWithCustomerAndItems() {
        var detail = new OrderDetailResponse(
            OrderResponse.from(OrderBuilder.anOrder().build()),
            /* customer */ null, /* items */ List.of());
        when(bff.orderDetail(eq("o-1"))).thenReturn(detail);

        mockMvc.get().uri("/api/admin/orders/o-1/detail")
            .expectStatus().isOk()
            .expectBody().asJson()
            .containsPath("$.order.id").value("o-test");
    }

    @Test
    void orderList_withoutAuth_returns403() { /* @PreAuthorize bypass test */ }
}
```

要点:
- 跟 controller slice 同模板,只测 BFF controller
- `@WithMockUser(roles = "ADMIN")` 测 `@PreAuthorize("hasRole('ADMIN')")` 真挡
- 5 cases:`/dashboard` / `/orders/{id}/detail` / `/orders` / `/products/{id}/duplicate` / `/orders/batch-ship`

## 6. TDD 顺序

| 步 | Task | 估时 | 备注 |
|---|---|---|---|
| 1 | **实验性**:写 `ProductControllerSliceTest`(2 cases) + `@WebMvcTest` + `@MockitoBean` + `MockMvcTester` 跑通 | 1h | 若失败走 fallback |
| 2 | 推 5 个 controller(每 2 cases,共 10 cases):OrderController / CartController / AdminOrderController / AdminProductController / AdminRefundController | 2-3h | 模板复用 |
| 3 | Repo:4 个 MongoIntegrationTest 派生 test(每 4 cases,共 16) | 2h | builder 复用 |
| 4 | BFF:5 cases | 1h | @WithMockUser 验证鉴权 |
| 5 | 跑 `./gradlew check` 验 ArchUnit + Jacoco 全局 ≥80%(CLAUDE.md §3) | 30min | 硬规则卡点 |
| 6 | 全 backend `./gradlew test` verify zero regression | 30min | 75+ + 30+ 新 = 100+ |

**总估时**:7-9h,留 1-2h 余量给 fallback 切换。

## 7. 风险 + Fallback

| 风险 | Fallback |
|---|---|
| `@WebMvcTest` 在本仓 Boot 4.0.6 + JDK 25 跑不起来 | 改 `MockMvcBuilders.standaloneSetup(controller).build()` 手装 — 跟 Sprint 1 `AuthControllerLogoutTest` 同款 |
| `MockMvcTester` 启动慢或 API 不熟 | 仍用老 `MockMvc.perform()` + `andExpect()`(Boot 4 仍兼容)|
| `@MockitoBean` 新包找不到 / 编译错 | 试老 `@MockBean` 包(可能 deprecation warning 但能跑);如全失败,改 `@MockBean` 用 `@Autowired` 手 mock |

**最低承诺**:即使全 fallback,本 change 仍交付:
- 15+ 新测试 + D1 builder 复用
- 文档(本 design + plan + 失败记录)供 Sprint 3 继续
- 类似 Sprint 1 closure fallback mode — 部分收益 + 经验沉淀

## 8. 文件清单

### 新文件(15)

**Controller slice(6)**:
1. `backend/src/test/java/com/seafood/product/api/ProductControllerSliceTest.java`
2. `backend/src/test/java/com/seafood/order/api/OrderControllerSliceTest.java`
3. `backend/src/test/java/com/seafood/order/api/CartControllerSliceTest.java`
4. `backend/src/test/java/com/seafood/bff/admin/AdminOrderControllerSliceTest.java`
5. `backend/src/test/java/com/seafood/bff/admin/AdminProductControllerSliceTest.java`
6. `backend/src/test/java/com/seafood/bff/admin/AdminRefundControllerSliceTest.java`

**Repository slice(4)**:
7. `backend/src/test/java/com/seafood/order/infra/OrderRepositorySliceTest.java`
8. `backend/src/test/java/com/seafood/product/infra/ProductRepositorySliceTest.java`
9. `backend/src/test/java/com/seafood/user/infra/UserRepositorySliceTest.java`
10. `backend/src/test/java/com/seafood/order/infra/RefundRepositorySliceTest.java`

**BFF integration(5)**:
11. `backend/src/test/java/com/seafood/bff/admin/AdminBffControllerSliceTest.java`(dashboard)
12. `backend/src/test/java/com/seafood/bff/admin/AdminBffOrderDetailSliceTest.java`
13. `backend/src/test/java/com/seafood/bff/admin/AdminBffOrderListSliceTest.java`
14. `backend/src/test/java/com/seafood/bff/admin/AdminBffProductDuplicateSliceTest.java`
15. `backend/src/test/java/com/seafood/bff/admin/AdminBffBatchShipSliceTest.java`

### 改文件

无(纯新增 test,不动 main src 或既有测试)

## 9. 不进什么(YAGNI)

- ❌ Spring Boot 4 Testcontainers reuse(Sprint 4 B 子项目做)— 本 change 沿 D1 `@Container static` 单 mongo:7
- ❌ PIT mutation testing(Sprint 4 C1)
- ❌ k6 负载测试(Sprint 4 C3)
- ❌ Jacoco badge / Codecov 接入(Sprint 4 D3)— 本 change 只跑 `./gradlew check` 验 ≥80%,不接外部
- ❌ mp / admin-ui 覆盖率(Sprint 3 / Sprint 4)

## 10. 验收 / 完成判据

- [ ] 6 controller slice + 4 repository slice + 5 BFF integration = 15 测试类,30+ case
- [ ] `./gradlew check` PASS(ArchUnit + checkNoRefreshScope)
- [ ] Jacoco 全局 ≥80%(CLAUDE.md §3 硬规则)
- [ ] 全 backend `./gradlew test` 0 失败 0 错误
- [ ] 全部测试使用 **D1 builder** 造 fixture,无 inline `new Xxx(...)`(父路线图 §2.1 "50%+ 现有测试改用 builder" 触发条件)

## 11. 关联

- **父**:`test-suite-roadmap/design.md` §2.1 子项目 ② + §3.2 Sprint 2
- **前置**:`sprint-2-test-data-builders`(D1)— builder 是本 change 的 fixture 地基
- **后续**:`sprint-3-ci-speed`(B)、`sprint-4-coverage-dashboard`(D3)— 用本 change 跑出的覆盖率基线

订单路线图验收 §5 → 子项目 ② 完成。
