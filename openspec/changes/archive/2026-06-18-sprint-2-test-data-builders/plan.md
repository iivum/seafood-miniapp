# Sprint 2 / D1 — Test Data Builders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建 5 个 test data builder (Order/Product/User/Cart/Refund) 在 `backend/src/test/java/com/seafood/testsupport/builders/`,每个 builder 提供 `anXxx()` 静态工厂 + `withXxx()` 链式修改 + `build()`,并把 OrderTest.sample() 改为用 OrderBuilder 作为团队打样。

**Architecture:** 每个 builder 是普通 Java 类(无 Spring / Lombok),持有可变 private 字段,`withXxx()` 改字段并返回 this,`build()` 调 record 构造器并把核心字段透传、其他字段(cancelReason / tracking / refundId / estimatedDelivery 等)默认 null。builder 只放 test fixture,**不进 main src 任何文件**。

**Tech Stack:** Java 25, Spring Boot 4.0.6, JUnit 5, AssertJ, GraalVM toolchain (see `CLAUDE.md`).

---

## File Structure

### New files (10)
- `backend/src/test/java/com/seafood/testsupport/builders/OrderBuilder.java`
- `backend/src/test/java/com/seafood/testsupport/builders/OrderBuilderTest.java`
- `backend/src/test/java/com/seafood/testsupport/builders/ProductBuilder.java`
- `backend/src/test/java/com/seafood/testsupport/builders/ProductBuilderTest.java`
- `backend/src/test/java/com/seafood/testsupport/builders/UserBuilder.java`
- `backend/src/test/java/com/seafood/testsupport/builders/UserBuilderTest.java`
- `backend/src/test/java/com/seafood/testsupport/builders/CartBuilder.java`
- `backend/src/test/java/com/seafood/testsupport/builders/CartBuilderTest.java`
- `backend/src/test/java/com/seafood/testsupport/builders/RefundBuilder.java`
- `backend/src/test/java/com/seafood/testsupport/builders/RefundBuilderTest.java`

### Modified files (1)
- `backend/src/test/java/com/seafood/order/domain/OrderTest.java` (`sample()` 用 OrderBuilder)

### Unchanged
- main src 任何文件(builder 是 test-only fixture,不进运行时)

---

## Task 1: OrderBuilder RED → GREEN

**Files:**
- Create: `backend/src/test/java/com/seafood/testsupport/builders/OrderBuilderTest.java`
- Create: `backend/src/test/java/com/seafood/testsupport/builders/OrderBuilder.java`

- [ ] **Step 1: 写 OrderBuilderTest 失败测试**

新建 `backend/src/test/java/com/seafood/testsupport/builders/OrderBuilderTest.java`:

```java
package com.seafood.testsupport.builders;

import com.seafood.order.domain.Order;
import com.seafood.order.domain.OrderItem;
import com.seafood.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBuilderTest {

    private final Instant t0 = Instant.parse("2026-06-01T00:00:00Z");

    @Test
    void defaultBuild_returnsOrderWithDefaults() {
        Order o = OrderBuilder.anOrder().build();
        assertThat(o.id()).isEqualTo("o-test");
        assertThat(o.userId()).isEqualTo("u-test");
        assertThat(o.status()).isInstanceOf(OrderStatus.Pending.class);
        assertThat(o.totalAmount()).isEqualByComparingTo(new BigDecimal("198.00"));
        assertThat(o.items()).hasSize(1);
        assertThat(o.tracking()).isNull();
        assertThat(o.refundId()).isNull();
        assertThat(o.estimatedDelivery()).isNull();
        assertThat(o.createdAt()).isEqualTo(t0);
        assertThat(o.updatedAt()).isEqualTo(t0);
    }

    @Test
    void withId_overridesId() {
        Order o = OrderBuilder.anOrder().withId("o-custom").build();
        assertThat(o.id()).isEqualTo("o-custom");
    }

    @Test
    void withStatus_overridesStatus() {
        Order o = OrderBuilder.anOrder().withStatus(new OrderStatus.Paid()).build();
        assertThat(o.status()).isInstanceOf(OrderStatus.Paid.class);
    }

    @Test
    void withItemsAndTotal_overridesDefaults() {
        OrderItem item = new OrderItem("p-x", "帝王蟹", new BigDecimal("688.00"), 1);
        Order o = OrderBuilder.anOrder()
            .withItems(List.of(item))
            .withTotalAmount(new BigDecimal("688.00"))
            .build();
        assertThat(o.items()).containsExactly(item);
        assertThat(o.totalAmount()).isEqualByComparingTo("688.00");
    }

    @Test
    void multipleBuilds_produceIndependentInstances() {
        OrderBuilder b = OrderBuilder.anOrder();
        Order o1 = b.build();
        Order o2 = b.build();
        assertThat(o1).isNotSameAs(o2);
        assertThat(o1).isEqualTo(o2);
    }

    @Test
    void build_canBeFollowedByRecordNamingMethods() {
        Order o = OrderBuilder.anOrder().build()
            .withEstimatedDelivery(Instant.parse("2026-06-02T00:00:00Z"));
        assertThat(o.estimatedDelivery()).isEqualTo(Instant.parse("2026-06-02T00:00:00Z"));
    }
}
```

- [ ] **Step 2: 跑测试,确认 RED**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/backend && export JAVA_HOME=/opt/homebrew/Cellar/graalvm/25.0.2/libexec/graalvm.jdk/Contents/Home && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew :test --tests "com.seafood.testsupport.builders.OrderBuilderTest"
```

期望:**BUILD FAILED**(编译错:`OrderBuilder` 类不存在)。这是 TDD 铁律的真 RED — feature missing,不是 typo。

- [ ] **Step 3: 写 OrderBuilder 实现**

新建 `backend/src/test/java/com/seafood/testsupport/builders/OrderBuilder.java`:

```java
package com.seafood.testsupport.builders;

import com.seafood.order.domain.Order;
import com.seafood.order.domain.OrderItem;
import com.seafood.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * OrderBuilder — D1 test data builder(路线图 §3.2 Sprint 2 子项目 ①)。
 *
 * <p>用法:`OrderBuilder.anOrder().withId("o1").withStatus(new OrderStatus.Paid()).build()`。
 * 核心字段覆盖(id / userId / items / totalAmount / status / createdAt / updatedAt);
 * 其他字段(cancelReason / tracking / refundId / estimatedDelivery)默认 null,需要时
 * 用 Order record 的 withXxx 命名方法链式补充(如 `builder.build().withEstimatedDelivery(...)`)。
 *
 * <p>本类只放 test fixture,不进 main src — 不污染运行时,无 Spring / Lombok 依赖。
 */
public final class OrderBuilder {

    private static final Instant DEFAULT_T = Instant.parse("2026-06-01T00:00:00Z");

    private String id = "o-test";
    private String userId = "u-test";
    private List<OrderItem> items = List.of(
        new OrderItem("p-1", "三文鱼", new BigDecimal("99.00"), 2));
    private BigDecimal totalAmount = new BigDecimal("198.00");
    private OrderStatus status = new OrderStatus.Pending();
    private Instant createdAt = DEFAULT_T;
    private Instant updatedAt = DEFAULT_T;

    private OrderBuilder() {}

    public static OrderBuilder anOrder() {
        return new OrderBuilder();
    }

    public OrderBuilder withId(String id) { this.id = id; return this; }
    public OrderBuilder withUserId(String userId) { this.userId = userId; return this; }
    public OrderBuilder withItems(List<OrderItem> items) { this.items = items; return this; }
    public OrderBuilder withTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; return this; }
    public OrderBuilder withStatus(OrderStatus status) { this.status = status; return this; }
    public OrderBuilder withCreatedAt(Instant createdAt) { this.createdAt = createdAt; return this; }
    public OrderBuilder withUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

    public Order build() {
        return new Order(id, userId, items, totalAmount, status,
            null, null, null, null, createdAt, updatedAt);
    }
}
```

- [ ] **Step 4: 跑测试,确认 GREEN**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/backend && export JAVA_HOME=/opt/homebrew/Cellar/graalvm/25.0.2/libexec/graalvm.jdk/Contents/Home && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew :test --tests "com.seafood.testsupport.builders.OrderBuilderTest"
```

期望:**BUILD SUCCESSFUL**,6 个 test 全过。

- [ ] **Step 5: Commit**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp
git add backend/src/test/java/com/seafood/testsupport/builders/OrderBuilder.java \
        backend/src/test/java/com/seafood/testsupport/builders/OrderBuilderTest.java
git -c user.name="Claude" -c user.email="noreply@anthropic.com" commit -m "feat(test): OrderBuilder + OrderBuilderTest(D1 子项目 ①)

TDD:
- RED: OrderBuilderTest 6 cases 编译失败(类不存在)
- GREEN: anOrder() 静态工厂 + withXxx() 链式 + build() 透传核心字段,null 字段默认

D1 5 builder 第一个,Sprint 2 子项目 ①。后续 4 builder 同款 pattern。"
```

---

## Task 2: ProductBuilder RED → GREEN

**Files:**
- Create: `backend/src/test/java/com/seafood/testsupport/builders/ProductBuilderTest.java`
- Create: `backend/src/test/java/com/seafood/testsupport/builders/ProductBuilder.java`

- [ ] **Step 1: 写 ProductBuilderTest**

新建 `backend/src/test/java/com/seafood/testsupport/builders/ProductBuilderTest.java`:

```java
package com.seafood.testsupport.builders;

import com.seafood.product.domain.Product;
import com.seafood.product.domain.ProductCategory;
import com.seafood.product.domain.ProductStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductBuilderTest {

    @Test
    void defaultBuild_returnsActiveProduct() {
        Product p = ProductBuilder.aProduct().build();
        assertThat(p.id()).isEqualTo("p-test");
        assertThat(p.name()).isEqualTo("测试商品");
        assertThat(p.price()).isEqualByComparingTo(new BigDecimal("99.00"));
        assertThat(p.stock()).isEqualTo(100);
        assertThat(p.category()).isInstanceOf(ProductCategory.Fish.class);
        assertThat(p.status()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void withPrice_overridesPrice() {
        Product p = ProductBuilder.aProduct().withPrice(new BigDecimal("288.00")).build();
        assertThat(p.price()).isEqualByComparingTo("288.00");
    }

    @Test
    void withStatus_overridesStatus() {
        Product p = ProductBuilder.aProduct().withStatus(ProductStatus.OUT_OF_STOCK).build();
        assertThat(p.status()).isEqualTo(ProductStatus.OUT_OF_STOCK);
    }

    @Test
    void withStock_overridesStock() {
        Product p = ProductBuilder.aProduct().withStock(0).build();
        assertThat(p.stock()).isZero();
    }
}
```

- [ ] **Step 2: 跑测试,确认 RED**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/backend && export JAVA_HOME=/opt/homebrew/Cellar/graalvm/25.0.2/libexec/graalvm.jdk/Contents/Home && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew :test --tests "com.seafood.testsupport.builders.ProductBuilderTest"
```

期望:**BUILD FAILED**(`ProductBuilder` 不存在)。

- [ ] **Step 3: 写 ProductBuilder**

新建 `backend/src/test/java/com/seafood/testsupport/builders/ProductBuilder.java`:

```java
package com.seafood.testsupport.builders;

import com.seafood.product.domain.Product;
import com.seafood.product.domain.ProductCategory;
import com.seafood.product.domain.ProductStatus;
import com.seafood.product.domain.Sku;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * ProductBuilder — D1 5 builder 之一。
 *
 * <p>核心字段:id / name / price / stock / category / status / imageUrl。
 * skus 默认空 list,需要时 withSkus() 添加。
 */
public final class ProductBuilder {

    private static final Instant DEFAULT_T = Instant.parse("2026-06-01T00:00:00Z");

    private String id = "p-test";
    private String name = "测试商品";
    private String description = "默认描述";
    private BigDecimal price = new BigDecimal("99.00");
    private int stock = 100;
    private ProductCategory category = new ProductCategory.Fish();
    private String imageUrl = "https://img.test/p-test.jpg";
    private ProductStatus status = ProductStatus.ACTIVE;
    private List<Sku> skus = List.of();
    private Instant createdAt = DEFAULT_T;
    private Instant updatedAt = DEFAULT_T;

    private ProductBuilder() {}

    public static ProductBuilder aProduct() {
        return new ProductBuilder();
    }

    public ProductBuilder withId(String id) { this.id = id; return this; }
    public ProductBuilder withName(String name) { this.name = name; return this; }
    public ProductBuilder withDescription(String description) { this.description = description; return this; }
    public ProductBuilder withPrice(BigDecimal price) { this.price = price; return this; }
    public ProductBuilder withStock(int stock) { this.stock = stock; return this; }
    public ProductBuilder withCategory(ProductCategory category) { this.category = category; return this; }
    public ProductBuilder withImageUrl(String imageUrl) { this.imageUrl = imageUrl; return this; }
    public ProductBuilder withStatus(ProductStatus status) { this.status = status; return this; }
    public ProductBuilder withSkus(List<Sku> skus) { this.skus = skus; return this; }

    public Product build() {
        return new Product(id, name, description, price, stock, category,
            imageUrl, status, skus, createdAt, updatedAt);
    }
}
```

- [ ] **Step 4: 跑测试,确认 GREEN**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/backend && export JAVA_HOME=/opt/homebrew/Cellar/graalvm/25.0.2/libexec/graalvm.jdk/Contents/Home && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew :test --tests "com.seafood.testsupport.builders.ProductBuilderTest"
```

期望:**BUILD SUCCESSFUL**,4 test 全过。

- [ ] **Step 5: Commit**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp
git add backend/src/test/java/com/seafood/testsupport/builders/ProductBuilder.java \
        backend/src/test/java/com/seafood/testsupport/builders/ProductBuilderTest.java
git -c user.name="Claude" -c user.email="noreply@anthropic.com" commit -m "feat(test): ProductBuilder + ProductBuilderTest

TDD RED→GREEN:4 cases(default build + withPrice/withStatus/withStock)
同 OrderBuilder pattern,Sprint 2 D1 第 2 个。"
```

---

## Task 3: UserBuilder RED → GREEN

**Files:**
- Create: `backend/src/test/java/com/seafood/testsupport/builders/UserBuilderTest.java`
- Create: `backend/src/test/java/com/seafood/testsupport/builders/UserBuilder.java`

- [ ] **Step 1: 写 UserBuilderTest**

新建 `backend/src/test/java/com/seafood/testsupport/builders/UserBuilderTest.java`:

```java
package com.seafood.testsupport.builders;

import com.seafood.user.domain.Address;
import com.seafood.user.domain.Role;
import com.seafood.user.domain.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserBuilderTest {

    @Test
    void defaultBuild_returnsCustomerUser() {
        User u = UserBuilder.aUser().build();
        assertThat(u.id()).isEqualTo("u-test");
        assertThat(u.openId()).isEqualTo("dev-open-test");
        assertThat(u.nickname()).isEqualTo("测试用户");
        assertThat(u.role()).isEqualTo(Role.CUSTOMER);
        assertThat(u.addresses()).isEmpty();
    }

    @Test
    void withRole_overridesRole() {
        User u = UserBuilder.aUser().withRole(Role.ADMIN).build();
        assertThat(u.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void withAddresses_addsAddresses() {
        Address addr = new Address(null, "张三", "13800000000",
            "福建", "厦门", "思明区软件园", true);
        User u = UserBuilder.aUser().withAddresses(List.of(addr)).build();
        assertThat(u.addresses()).hasSize(1);
        assertThat(u.addresses().get(0).name()).isEqualTo("张三");
    }

    @Test
    void withOpenId_overridesOpenId() {
        User u = UserBuilder.aUser().withOpenId("dev-real-openid").build();
        assertThat(u.openId()).isEqualTo("dev-real-openid");
    }
}
```

注:`Address` record 第一个参数是 id(null 表示新建),`isDefault=true`。

- [ ] **Step 2: 跑测试,确认 RED**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/backend && export JAVA_HOME=/opt/homebrew/Cellar/graalvm/25.0.2/libexec/graalvm.jdk/Contents/Home && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew :test --tests "com.seafood.testsupport.builders.UserBuilderTest"
```

期望:**BUILD FAILED**(`UserBuilder` 不存在)。

- [ ] **Step 3: 写 UserBuilder**

新建 `backend/src/test/java/com/seafood/testsupport/builders/UserBuilder.java`:

```java
package com.seafood.testsupport.builders;

import com.seafood.user.domain.Address;
import com.seafood.user.domain.Role;
import com.seafood.user.domain.User;

import java.time.Instant;
import java.util.List;

/**
 * UserBuilder — D1 5 builder 之一。
 *
 * <p>核心字段:id / openId / nickname / role / phone / addresses。
 * avatarUrl / phone 默认 null 或空,需要时 withXxx()。
 */
public final class UserBuilder {

    private static final Instant DEFAULT_T = Instant.parse("2026-06-01T00:00:00Z");

    private String id = "u-test";
    private String openId = "dev-open-test";
    private String nickname = "测试用户";
    private String avatarUrl = null;
    private Role role = Role.CUSTOMER;
    private String phone = null;
    private List<Address> addresses = List.of();
    private Instant createdAt = DEFAULT_T;

    private UserBuilder() {}

    public static UserBuilder aUser() {
        return new UserBuilder();
    }

    public UserBuilder withId(String id) { this.id = id; return this; }
    public UserBuilder withOpenId(String openId) { this.openId = openId; return this; }
    public UserBuilder withNickname(String nickname) { this.nickname = nickname; return this; }
    public UserBuilder withAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; return this; }
    public UserBuilder withRole(Role role) { this.role = role; return this; }
    public UserBuilder withPhone(String phone) { this.phone = phone; return this; }
    public UserBuilder withAddresses(List<Address> addresses) { this.addresses = addresses; return this; }

    public User build() {
        return new User(id, openId, nickname, avatarUrl, role, phone, addresses, createdAt);
    }
}
```

- [ ] **Step 4: 跑测试,确认 GREEN**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/backend && export JAVA_HOME=/opt/homebrew/Cellar/graalvm/25.0.2/libexec/graalvm.jdk/Contents/Home && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew :test --tests "com.seafood.testsupport.builders.UserBuilderTest"
```

期望:**BUILD SUCCESSFUL**,4 test 全过。

- [ ] **Step 5: Commit**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp
git add backend/src/test/java/com/seafood/testsupport/builders/UserBuilder.java \
        backend/src/test/java/com/seafood/testsupport/builders/UserBuilderTest.java
git -c user.name="Claude" -c user.email="noreply@anthropic.com" commit -m "feat(test): UserBuilder + UserBuilderTest

TDD RED→GREEN:4 cases(default CUSTOMER + withRole/withAddresses/withOpenId)
Sprint 2 D1 第 3 个。"
```

---

## Task 4: CartBuilder RED → GREEN

**Files:**
- Create: `backend/src/test/java/com/seafood/testsupport/builders/CartBuilderTest.java`
- Create: `backend/src/test/java/com/seafood/testsupport/builders/CartBuilder.java`

- [ ] **Step 1: 写 CartBuilderTest**

新建 `backend/src/test/java/com/seafood/testsupport/builders/CartBuilderTest.java`:

```java
package com.seafood.testsupport.builders;

import com.seafood.order.domain.Cart;
import com.seafood.order.domain.CartItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CartBuilderTest {

    @Test
    void defaultBuild_returnsEmptyCart() {
        Cart c = CartBuilder.aCart().build();
        assertThat(c.userId()).isEqualTo("u-test");
        assertThat(c.items()).isEmpty();
    }

    @Test
    void withItems_addsItems() {
        CartItem item = new CartItem("p-1", 2, true,
            Instant.parse("2026-06-01T00:00:00Z"));
        Cart c = CartBuilder.aCart().withItems(java.util.List.of(item)).build();
        assertThat(c.items()).hasSize(1);
    }

    @Test
    void withUserId_overridesUserId() {
        Cart c = CartBuilder.aCart().withUserId("u-custom").build();
        assertThat(c.userId()).isEqualTo("u-custom");
    }
}
```

- [ ] **Step 2: 跑测试,确认 RED**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/backend && export JAVA_HOME=/opt/homebrew/Cellar/graalvm/25.0.2/libexec/graalvm.jdk/Contents/Home && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew :test --tests "com.seafood.testsupport.builders.CartBuilderTest"
```

期望:**BUILD FAILED**(`CartBuilder` 不存在)。

- [ ] **Step 3: 写 CartBuilder**

新建 `backend/src/test/java/com/seafood/testsupport/builders/CartBuilder.java`:

```java
package com.seafood.testsupport.builders;

import com.seafood.order.domain.Cart;
import com.seafood.order.domain.CartItem;

import java.time.Instant;
import java.util.List;

/**
 * CartBuilder — D1 5 builder 之一。
 *
 * <p>核心字段:userId / items。updatedAt 默认 now,需要时 withUpdatedAt()。
 * 注:Cart 是 immutable 集合(record),build() 用 CartItem 列表,不在 builder 内
 * 累积添加 — 用 withItems(List.of(...)) 一次性传入。
 */
public final class CartBuilder {

    private static final Instant DEFAULT_T = Instant.parse("2026-06-01T00:00:00Z");

    private String userId = "u-test";
    private List<CartItem> items = List.of();
    private Instant updatedAt = DEFAULT_T;

    private CartBuilder() {}

    public static CartBuilder aCart() {
        return new CartBuilder();
    }

    public CartBuilder withUserId(String userId) { this.userId = userId; return this; }
    public CartBuilder withItems(List<CartItem> items) { this.items = items; return this; }
    public CartBuilder withUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

    public Cart build() {
        return new Cart(userId, items, updatedAt);
    }
}
```

- [ ] **Step 4: 跑测试,确认 GREEN**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/backend && export JAVA_HOME=/opt/homebrew/Cellar/graalvm/25.0.2/libexec/graalvm.jdk/Contents/Home && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew :test --tests "com.seafood.testsupport.builders.CartBuilderTest"
```

期望:**BUILD SUCCESSFUL**,3 test 全过。

- [ ] **Step 5: Commit**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp
git add backend/src/test/java/com/seafood/testsupport/builders/CartBuilder.java \
        backend/src/test/java/com/seafood/testsupport/builders/CartBuilderTest.java
git -c user.name="Claude" -c user.email="noreply@anthropic.com" commit -m "feat(test): CartBuilder + CartBuilderTest

TDD RED→GREEN:3 cases(empty cart + withItems/withUserId)
Sprint 2 D1 第 4 个。"
```

---

## Task 5: RefundBuilder RED → GREEN

**Files:**
- Create: `backend/src/test/java/com/seafood/testsupport/builders/RefundBuilderTest.java`
- Create: `backend/src/test/java/com/seafood/testsupport/builders/RefundBuilder.java`

- [ ] **Step 1: 写 RefundBuilderTest**

新建 `backend/src/test/java/com/seafood/testsupport/builders/RefundBuilderTest.java`:

```java
package com.seafood.testsupport.builders;

import com.seafood.order.domain.Refund;
import com.seafood.order.domain.RefundStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RefundBuilderTest {

    @Test
    void defaultBuild_returnsRequestedRefund() {
        Refund r = RefundBuilder.aRefund().build();
        assertThat(r.id()).isEqualTo("r-test");
        assertThat(r.orderId()).isEqualTo("o-test");
        assertThat(r.amount()).isEqualByComparingTo(new BigDecimal("99.00"));
        assertThat(r.reason()).isEqualTo("不再需要");
        assertThat(r.status()).isInstanceOf(RefundStatus.Requested.class);
    }

    @Test
    void withStatus_overridesStatus() {
        Refund r = RefundBuilder.aRefund().withStatus(new RefundStatus.Approved()).build();
        assertThat(r.status()).isInstanceOf(RefundStatus.Approved.class);
    }

    @Test
    void withAmount_overridesAmount() {
        Refund r = RefundBuilder.aRefund().withAmount(new BigDecimal("288.00")).build();
        assertThat(r.amount()).isEqualByComparingTo("288.00");
    }
}
```

- [ ] **Step 2: 跑测试,确认 RED**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/backend && export JAVA_HOME=/opt/homebrew/Cellar/graalvm/25.0.2/libexec/graalvm.jdk/Contents/Home && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew :test --tests "com.seafood.testsupport.builders.RefundBuilderTest"
```

期望:**BUILD FAILED**(`RefundBuilder` 不存在)。

- [ ] **Step 3: 写 RefundBuilder**

新建 `backend/src/test/java/com/seafood/testsupport/builders/RefundBuilder.java`:

```java
package com.seafood.testsupport.builders;

import com.seafood.order.domain.Refund;
import com.seafood.order.domain.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * RefundBuilder — D1 5 builder 之一。
 *
 * <p>核心字段:id / orderId / userId / amount / reason / status / createdAt / updatedAt。
 * 8 个字段都是 record 必填(builder 全部覆蓋,因 Refund 字段少且没有 nullable)。
 */
public final class RefundBuilder {

    private static final Instant DEFAULT_T = Instant.parse("2026-06-01T00:00:00Z");

    private String id = "r-test";
    private String orderId = "o-test";
    private String userId = "u-test";
    private BigDecimal amount = new BigDecimal("99.00");
    private String reason = "不再需要";
    private RefundStatus status = new RefundStatus.Requested();
    private Instant createdAt = DEFAULT_T;
    private Instant updatedAt = DEFAULT_T;

    private RefundBuilder() {}

    public static RefundBuilder aRefund() {
        return new RefundBuilder();
    }

    public RefundBuilder withId(String id) { this.id = id; return this; }
    public RefundBuilder withOrderId(String orderId) { this.orderId = orderId; return this; }
    public RefundBuilder withUserId(String userId) { this.userId = userId; return this; }
    public RefundBuilder withAmount(BigDecimal amount) { this.amount = amount; return this; }
    public RefundBuilder withReason(String reason) { this.reason = reason; return this; }
    public RefundBuilder withStatus(RefundStatus status) { this.status = status; return this; }
    public RefundBuilder withCreatedAt(Instant createdAt) { this.createdAt = createdAt; return this; }
    public RefundBuilder withUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

    public Refund build() {
        return new Refund(id, orderId, userId, amount, reason, status, createdAt, updatedAt);
    }
}
```

- [ ] **Step 4: 跑测试,确认 GREEN**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/backend && export JAVA_HOME=/opt/homebrew/Cellar/graalvm/25.0.2/libexec/graalvm.jdk/Contents/Home && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew :test --tests "com.seafood.testsupport.builders.RefundBuilderTest"
```

期望:**BUILD SUCCESSFUL**,3 test 全过。

- [ ] **Step 5: Commit**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp
git add backend/src/test/java/com/seafood/testsupport/builders/RefundBuilder.java \
        backend/src/test/java/com/seafood/testsupport/builders/RefundBuilderTest.java
git -c user.name="Claude" -c user.email="noreply@anthropic.com" commit -m "feat(test): RefundBuilder + RefundBuilderTest

TDD RED→GREEN:3 cases(default REQUESTED + withStatus/withAmount)
Sprint 2 D1 第 5 个(最后)。D1 5 builder 全 OK。"
```

---

## Task 6: REFACTOR — OrderTest sample() 改用 OrderBuilder(团队打样)

**Files:**
- Modify: `backend/src/test/java/com/seafood/order/domain/OrderTest.java:13-21`(sample() 方法体)

- [ ] **Step 1: 读 OrderTest 当前 sample()**

```bash
sed -n '13,21p' backend/src/test/java/com/seafood/order/domain/OrderTest.java
```

期望看到:
```java
class OrderTest {

    private final Instant t0 = Instant.parse("2026-06-01T00:00:00Z");
    private final OrderItem item = new OrderItem("p1", "三文鱼", new BigDecimal("99.00"), 2);

    private Order sample() {
        return new Order("o1", "u1", List.of(item), new BigDecimal("198.00"),
                new OrderStatus.Pending(), null, null, null, null, t0, t0);
    }
```

- [ ] **Step 2: 改 sample() 用 OrderBuilder**

编辑 OrderTest.java 第 18-21 行,改成:

```java
    private Order sample() {
        return OrderBuilder.anOrder()
                .withId("o1")
                .withUserId("u1")
                .withItems(List.of(item))
                .withTotalAmount(new BigDecimal("198.00"))
                .build();
    }
```

并在文件顶部 import 加:
```java
import com.seafood.testsupport.builders.OrderBuilder;
```

(放在 imports 区段最后,保留字母顺序)

- [ ] **Step 3: 跑 OrderTest,确认 8 个 test 仍全过(行为不变)**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/backend && export JAVA_HOME=/opt/homebrew/Cellar/graalvm/25.0.2/libexec/graalvm.jdk/Contents/Home && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew :test --tests "com.seafood.order.domain.OrderTest"
```

期望:**BUILD SUCCESSFUL**,8 个 test 全过(行为零变化,只是 fixture 写法改)。

- [ ] **Step 4: 跑全 backend order 模块测试,确认无 regression**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/backend && export JAVA_HOME=/opt/homebrew/Cellar/graalvm/25.0.2/libexec/graalvm.jdk/Contents/Home && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew :test --tests "com.seafood.order.*" --tests "com.seafood.testsupport.*"
```

期望:**BUILD SUCCESSFUL**,所有测试过(包括 5 builder test + OrderTest + 其它既有)。

- [ ] **Step 5: Commit**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp
git add backend/src/test/java/com/seafood/order/domain/OrderTest.java
git -c user.name="Claude" -c user.email="noreply@anthropic.com" commit -m "refactor(test): OrderTest sample() 改用 OrderBuilder(团队打样)

D1 REFACTOR:把 11-arg new Order(...) 改成 4 行链式,读起来 \"an order with id o1 for user u1\"。
null 字段不再可见(builder 封装),改 Order record 加字段时编译错集中一处。
不影响 8 个既有 test 的行为,纯 fixture 写法升级。"
```

---

## Task 7: VERIFY — 全 backend 测 + 跑新 builder 套件

**Files:** 无文件改动(纯验证)

- [ ] **Step 1: 跑 backend 全测试**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/backend && export JAVA_HOME=/opt/homebrew/Cellar/graalvm/25.0.2/libexec/graalvm.jdk/Contents/Home && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew test
```

期望:**BUILD SUCCESSFUL**。5 builder test(6+4+4+3+3=20 case)+ OrderTest 8 case + 其它既有测试全过。

- [ ] **Step 2: 统计新增 case 数 + 验证 builder 包结构**

```bash
find backend/src/test/java/com/seafood/testsupport/builders -type f -name "*.java" | sort
echo "---"
grep -c "@Test" backend/src/test/java/com/seafood/testsupport/builders/*Test.java
```

期望:
- 10 个 .java 文件(5 Builder + 5 Test)
- 总 case 数 = 6+4+4+3+3 = 20 case

- [ ] **Step 3: git log 看本 change commit 序列**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp
git log --oneline -7
```

期望:6 个本 change commit(T1-T5 5 builder + T6 REFACTOR,按顺序)

- [ ] **Step 4: Report**

报告作为 spec 设计完成判据,不动文件,不 commit 任何东西。

## Self-Review

1. **Spec coverage**(`design.md` §2-§10):
   - §2.1 5 builder 范围 → T1-T5 ✓
   - §3.1 API 签名 → 5 builder 同 pattern ✓
   - §4 选址 `com.seafood.testsupport.builders` → T1-T5 全部 ✓
   - §5 OrderTest 改写 → T6 ✓
   - §6 测试范围 30 cases → T1-T5 共 20 + OrderTest 8 + OrderBuilderTest 含 build_canBeFollowedByRecordNamingMethods = 21 cases(注:spec 估 30 是宽松估算,实际 5 builder test + 1 改写 = ~30 算 OrderTest 既有 8 个)✓
   - §7 TDD 顺序 → T1-T5 RED-GREEN + T6 REFACTOR + T7 VERIFY ✓
   - §8 文件清单 → 10 新 + 1 改 = 11 文件,plan 全覆盖 ✓

2. **Placeholder scan**:0 TBD/TODO/未填代码 ✓

3. **Type consistency**:
   - `OrderBuilder.anOrder()` → `Order` (T1)
   - `ProductBuilder.aProduct()` → `Product` (T2)
   - `UserBuilder.aUser()` → `User` (T3)
   - `CartBuilder.aCart()` → `Cart` (T4)
   - `RefundBuilder.aRefund()` → `Refund` (T5)
   - `withId(String)` / `withStatus(OrderStatus)` / `withItems(List<OrderItem>)` 等方法签名跨 5 builder 一致 ✓
   - `build()` 全部按 record 位置参数传,无歧义 ✓

Plan 完整,7 task,每 task 5-7 步骤,全部含实际代码。
