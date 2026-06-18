# Sprint 2 / D1 — Test Data Builders Design

> 2026-06-18 · 父路线图:`openspec/changes/test-suite-roadmap/design.md` §2.1 子项目 ① + §3.2 Sprint 2
> 状态: 设计待批准 → 走 standard proposal → design → specs → tasks → apply

## 1. 背景

Sprint 1 closure 后,backend 已有 77 测试覆盖 domain/application/infra,但**测试 fixture 散落在各测试类的 `sample()` 私有方法里**(`OrderTest.sample()` / `CartTest.anEmptyCart()` 等),没有任何共享工厂:

- 跨测试类复制 setup 代码
- 改 `Order` record 加字段时,所有 sample() 编译失败散落
- 测试可读性差:11-arg `new Order(...)` 调用读不到意图

路线图 §3.3 明确 D1 排第一:不开这条路,A/B/C1 三个子项目都会写重复 fixture,Sprint 4 末还得回头补。

## 2. 范围

### 2.1 5 个 Builder(必做)

| Builder | 模块 | 默认字段 |
|---|---|---|
| **OrderBuilder** | order | id="o-test", userId="u-test", items=[三文鱼×2 99.00], totalAmount=198.00, status=PENDING, createdAt=updatedAt=2026-06-01 |
| **ProductBuilder** | product | id="p-test", name="测试商品", price=99.00, stock=100, category=鱼类, status=ACTIVE |
| **UserBuilder** | user | id="u-test", openId="dev-open-test", nickname="测试用户", phone=null, addresses=[] |
| **CartBuilder** | order | userId="u-test", items=[] |
| **RefundBuilder** | order | id="r-test", orderId="o-test", reason="不再需要", status=PENDING, amount=99.00 |

### 2.2 不做(YAGNI)

- ❌ Mongo persist helper(save/findById)— D1 范畴是 builder,不是 fixture infra
- ❌ 自动值生成(随机 id / 时间)— 默认值即可,测试可读性高于随机性
- ❌ Builder 子类 / 子场景 preset(aPaidOrderFor("u1"))— 5+ withXxx 已经够清晰

## 3. API 签名

### 3.1 OrderBuilder 完整签名(其它 builder 同款)

```java
public final class OrderBuilder {
  private String id = "o-test";
  private String userId = "u-test";
  private List<OrderItem> items = List.of(
      new OrderItem("p-1", "三文鱼", new BigDecimal("99.00"), 2));
  private BigDecimal totalAmount = new BigDecimal("198.00");
  private OrderStatus status = new OrderStatus.Pending();
  private Instant createdAt = Instant.parse("2026-06-01T00:00:00Z");
  private Instant updatedAt = Instant.parse("2026-06-01T00:00:00Z");

  public static OrderBuilder anOrder() { return new OrderBuilder(); }

  public OrderBuilder withId(String id)                    { this.id = id; return this; }
  public OrderBuilder withUserId(String userId)            { this.userId = userId; return this; }
  public OrderBuilder withItems(List<OrderItem> items)     { this.items = items; return this; }
  public OrderBuilder withTotalAmount(BigDecimal total)   { this.totalAmount = total; return this; }
  public OrderBuilder withStatus(OrderStatus status)      { this.status = status; return this; }
  public OrderBuilder withCreatedAt(Instant createdAt)    { this.createdAt = createdAt; return this; }
  public OrderBuilder withUpdatedAt(Instant updatedAt)    { this.updatedAt = updatedAt; return this; }

  public Order build() {
    return new Order(id, userId, items, totalAmount, status,
        null, null, null, null, createdAt, updatedAt);
  }
}
```

### 3.2 约定(5 个 builder 共用)

- **`anXxx()`** 静态工厂,读起来像英语:"an order that..."
- **`withXxx()`** 链式修改,返回 `this`(builder 可变,build 后丢弃)
- **`build()`** 终态,返回领域 record(不可变)。`build()` 可调用多次,每次返回独立实例
- **核心字段**才进 builder(80% 测试场景);nullable 字段(tracking / refundId / estimatedDelivery / cancelReason)在 builder 内部默认 null,需要时由 Order 的 `withTracking(...)` / `withEstimatedDelivery(...)` 等命名方法补充

## 4. 选址

```
backend/src/test/java/com/seafood/
├── testsupport/
│   ├── MongoIntegrationTest.java          (已存在 — Testcontainers base)
│   └── builders/                          (新 — 本 change)
│       ├── OrderBuilder.java
│       ├── ProductBuilder.java
│       ├── UserBuilder.java
│       ├── CartBuilder.java
│       └── RefundBuilder.java
├── order/domain/OrderTest.java            (改 — sample() 改用 OrderBuilder)
└── ... (其它测试不动 — 给团队看 builder 用法,Sprint 3 起逐步迁移)
```

**理由**:
- 与 `MongoIntegrationTest` 同包,后续 Sprint 4 Testcontainers reuse 可组合
- 跨模块共享:Product 测试能 `import com.seafood.testsupport.builders.UserBuilder` 无跨模块路径
- 不选址 `domain/builders/`:每模块导入别模块 builder 路径长,5 模块散 5 个 builders 目录难找

## 5. OrderTest 改写示范

```java
// 改写前 (现有 OrderTest.java:18-21)
private Order sample() {
  return new Order("o1", "u1", List.of(item), new BigDecimal("198.00"),
          new OrderStatus.Pending(), null, null, null, null, t0, t0);
}

// 改写后
private Order sample() {
  return OrderBuilder.anOrder()
      .withId("o1")
      .withUserId("u1")
      .withItems(List.of(item))
      .withTotalAmount(new BigDecimal("198.00"))
      .build();
}
```

**好处**:
- 11-arg constructor 改 4 行链式,读 "an order with id o1 for user u1"
- null 字段不再可见,由 builder 封装
- 改 Order record 加字段时,builder 编译错集中(只有一个 sample() 改 vs 散落所有)

**副作用**:OrderTest 8 个测试都改 sample() — 一次性 commit。

## 6. 测试(6 文件,~30 cases)

| 测试 | 内容 |
|---|---|
| `OrderBuilderTest`(新) | 默认 build / withXxx 单字段 / 多次 build 独立 / null 字段默认 / 不抛错 / 字段全填 |
| `ProductBuilderTest`(新) | 默认 build / withPrice 改 / ACTIVE 状态默认 |
| `UserBuilderTest`(新) | 默认 build / withAddresses 加项 |
| `CartBuilderTest`(新) | 默认 empty cart / withItems 加项 |
| `RefundBuilderTest`(新) | 默认 PENDING / withStatus 改 |
| `OrderTest`(改) | 8 test 用新 sample(),行为不变 |

总 ~30 cases。

## 7. TDD 顺序

1. **RED 1**:写 `OrderBuilderTest.defaultBuildReturnsValidOrder()` + 5 builder 类空 stub → 编译失败
2. **GREEN 1**:OrderBuilder 实现 → OrderBuilderTest 6 cases pass
3. **RED-GREEN 2-5**:同样模式做 Product / User / Cart / Refund Builder + 各自 test
4. **REFACTOR 6**:OrderTest 改写 sample() 用 OrderBuilder → 8 现有 test 仍 pass
5. **VERIFY 7**:全 backend `./gradlew test` 仍 100% pass(无 regression)

每个 builder ~30min RED-GREEN,5 builder + 改写 + verify = ~3-4h 实施。

## 8. 文件清单

### 新文件
1. `backend/src/test/java/com/seafood/testsupport/builders/OrderBuilder.java`
2. `backend/src/test/java/com/seafood/testsupport/builders/OrderBuilderTest.java`
3. `backend/src/test/java/com/seafood/testsupport/builders/ProductBuilder.java`
4. `backend/src/test/java/com/seafood/testsupport/builders/ProductBuilderTest.java`
5. `backend/src/test/java/com/seafood/testsupport/builders/UserBuilder.java`
6. `backend/src/test/java/com/seafood/testsupport/builders/UserBuilderTest.java`
7. `backend/src/test/java/com/seafood/testsupport/builders/CartBuilder.java`
8. `backend/src/test/java/com/seafood/testsupport/builders/CartBuilderTest.java`
9. `backend/src/test/java/com/seafood/testsupport/builders/RefundBuilder.java`
10. `backend/src/test/java/com/seafood/testsupport/builders/RefundBuilderTest.java`

### 改文件
1. `backend/src/test/java/com/seafood/order/domain/OrderTest.java`(sample() 改用 OrderBuilder)

**不改** main src 任何文件(builder 是 test-only fixture,不进运行时)

## 9. 不进什么(YAGNI)

- ❌ Spring 容器注入 builder(本 change 纯 Java,无 Spring 配置)
- ❌ Lombok(@Builder 用 Lombok 一行)— 项目 CLAUDE.md 无 Lombok,本 change 避免引入新依赖
- ❌ 静态 builder 方法(`OrderBuilder.anOrderPendingFor("u1")` 等)— 5+ withXxx 已表达清楚,preset 方法属 API 膨胀
- ❌ 全字段 builder(覆盖 Order 11 字段)— 80/20 原则,核心字段够用

## 10. Sprint 2 后续衔接

本 change 完成后:
- Sprint 3 A 后端 coverage backfill:6+ controller slice + 4+ repo slice 测试都用 `*Builder`,无重复 fixture
- Sprint 3 B CI 速度:Testcontainers reuse + ci.yml 拆 job,builder 不动
- Sprint 4 C1 PIT mutation:builder 提高 mutation score(测试代码本身简单清晰)

订单路线图验收 §5 → D1 子项目 ① 标记完成
