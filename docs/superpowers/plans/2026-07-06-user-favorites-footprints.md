# 收藏 + 浏览足迹 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让用户可以收藏商品(mp-03 商品详情页两个心形入口接真后端)、浏览商品详情时静默记录足迹,mp-05「我的」页显示真实收藏/足迹数,并新增两个可查看/管理的列表页。

**Architecture:** 后端在既有 `com.seafood.user` 模块下新增两个 Application Service(`FavoriteService`/`ProductViewService`)+ 两个自服务式(self-scoped,身份取自 JWT principal,不接受外部 userId 参数)Controller(`/api/favorites`、`/api/product-views`),`User` 聚合根新增 `favoriteProductIds` 字段(嵌入,对齐既有 `addresses` 惯例),浏览足迹独立建 `product_views` 集合(高频写 + 需要按用户裁剪到最近 100 条)。富化商品信息(名称/价格/图片)+ 失效商品降级复用 `CartService#enrich` 已确立的先例。前端新增两个 feature API 客户端(`.ts` 源码 + `.js` shim 成对,契约测试锁死同步,防止本项目反复出现的"ts 有/js shim 没有"drift),`product-detail.js` 的底部/悬浮顶栏两个收藏入口统一到同一个真实状态,`profile.js` 接真实计数,新增收藏网格页 + 足迹列表页。

**Tech Stack:** Spring Boot 4.0.6 + MongoDB(Spring Data MongoDB,record 域对象)、JUnit 5 + AssertJ + Mockito;微信小程序原生(Page/Component,无框架)+ Jest 29。

## Global Constraints

- 跨模块只走 ApplicationService,不可直调 Repository(design §1.3)——`FavoriteService`/`ProductViewService` 都在 `user` 模块内,可直接依赖 `UserRepository`/`ProductViewRepository`(同模块);两者都经 `ProductService`(product 模块)富化商品信息,不直碰 `ProductRepository`
- TDD 优先:每个 Task 都是"先写失败测试→跑红→实现→跑绿→commit"
- 覆盖率全局 ≥80%,核心 ≥90%(`./gradlew check` 会跑 jacoco 校验)
- 无硬编码密钥、无 `console.log`(日志走 SLF4J);前端不用 `any`(测试文件除外,本计划全是 `.js`/纯 JS,不涉及)
- API 端点风格对齐既有自服务式门面(`/api/addresses`、`/api/cart`,身份取自 `@AuthenticationPrincipal UserPrincipal me`,不接受外部 `userId` 路径参数)——不是 `/api/users/me/xxx` 嵌套(design.md 里写的 `/api/users/me/favorites` 路径在本计划里改为 `/api/favorites`,与本仓库 `AddressController`/`CartController` 的真实既有惯例一致,是纯路径风格对齐,不改变任何字段/行为)
- 收藏无上限(YAGNI,design.md Risk 已记录);足迹按用户裁剪到最近 100 条,同一商品反复查看只保留最新一条(upsert 语义,不新增重复记录)
- 失效商品(已下架/被删除)在列表里降级显示,不能让单个失效商品 500 掉整个请求(复用 `CartService#enrich` 的 try/catch-NotFoundException 模式)
- 幂等性:重复收藏已收藏商品、取消收藏未收藏商品,均返回成功(no-op),不报错
- 不分页(足迹上限 100、收藏预计量级也不大,一次性返回全部)
- 文件:多小优于少大(200-400 行,≤800);高内聚按领域组织

---

## Task 1: `User` 聚合根新增 `favoriteProductIds` 字段

**Files:**
- Modify: `backend/src/main/java/com/seafood/user/domain/User.java`
- Modify: `backend/src/main/java/com/seafood/user/infra/UserDocument.java`
- Modify: `backend/src/main/java/com/seafood/user/infra/UserMapper.java`
- Modify: `backend/src/test/java/com/seafood/testsupport/builders/UserBuilder.java`
- Modify: `backend/src/test/java/com/seafood/user/domain/UserTest.java`(修复 4 处受字段新增影响的位置参数构造调用 + 新增 addFavorite/removeFavorite 测试)
- Test: `backend/src/test/java/com/seafood/user/domain/UserTest.java`

**Interfaces:**
- Produces: `User.favoriteProductIds(): List<String>`、`User.addFavorite(String productId): User`(幂等,已收藏则原样返回;新收藏插入列表头部——"最近收藏优先"排序)、`User.removeFavorite(String productId): User`(幂等,未收藏则原样返回)。`User` record 构造器新增第 8 个位置参数 `favoriteProductIds`(在 `addresses` 之后、`createdAt` 之前)——Task 2+ 的 `UserMapper`/`UserBuilder` 都依赖这个确切的字段名和位置。

`User.java` 现状(仅列变更相关部分,完整文件见 `backend/src/main/java/com/seafood/user/domain/User.java`):
```java
public record User(
        String id,
        String openId,
        String nickname,
        String avatarUrl,
        Role role,
        String phone,
        List<Address> addresses,
        Instant createdAt
) {
    public User {
        if (openId == null || openId.isBlank()) {
            throw new DomainException("openId 不能为空");
        }
        if (role == null) {
            throw new DomainException("role 不能为空");
        }
        addresses = addresses == null ? List.of() : List.copyOf(addresses);
    }
    // ... addAddress/updateAddress/removeAddress/setDefaultAddress/findAddress/mutateAddresses/isAdmin/isCustomer
}
```

- [ ] **Step 1: 写失败测试(User.addFavorite/removeFavorite)**

在 `backend/src/test/java/com/seafood/user/domain/UserTest.java` 末尾(`isAdmin_andIsCustomer` 测试之后,`}`  之前)追加:

```java
    @Test
    void addFavorite_insertsAtHead_mostRecentFirst() {
        User u = sample().addFavorite("p1").addFavorite("p2");
        assertThat(u.favoriteProductIds()).containsExactly("p2", "p1");
    }

    @Test
    void addFavorite_alreadyFavorited_isNoOp() {
        User u = sample().addFavorite("p1");
        User again = u.addFavorite("p1");
        assertThat(again.favoriteProductIds()).containsExactly("p1");
        assertThat(again).isSameAs(u);
    }

    @Test
    void addFavorite_blankProductId_throws() {
        assertThatThrownBy(() -> sample().addFavorite(" "))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void removeFavorite_removesById() {
        User u = sample().addFavorite("p1").addFavorite("p2").removeFavorite("p1");
        assertThat(u.favoriteProductIds()).containsExactly("p2");
    }

    @Test
    void removeFavorite_notFavorited_isNoOp() {
        User u = sample().addFavorite("p1");
        User again = u.removeFavorite("nope");
        assertThat(again.favoriteProductIds()).containsExactly("p1");
        assertThat(again).isSameAs(u);
    }
```

同时修复 4 处受字段新增影响、按位置参数直接调用 `new User(...)` 的既有测试(每处在 `addresses` 参数 `List.of()` 之后插入一个新的 `List.of()` 参数——`favoriteProductIds` 位置):

```java
    // constructor_rejectsNullRole (原第 29 行)
    assertThatThrownBy(() -> new User("u1", "open-1", "n", "u", null, null, List.of(), List.of(), t0))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining("role");

    // constructor_rejectsBlankOpenId (原第 36 行)
    assertThatThrownBy(() -> new User("u1", " ", "n", "u", Role.CUSTOMER, null, List.of(), List.of(), t0))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining("openId");

    // isAdmin_andIsCustomer (原第 94 行)
    assertThat(new User("a", "o", "n", "u", Role.ADMIN, null, List.of(), List.of(), t0).isAdmin()).isTrue();
```

`sample()` helper(第 18-21 行)也要加一个 `List.of()`:
```java
    private User sample() {
        return new User("u1", "open-1", "nick", "http://a", Role.CUSTOMER,
                "13900000000", List.of(), List.of(), t0);
    }
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && ./gradlew test --tests "com.seafood.user.domain.UserTest"`
Expected: 编译失败(`User` 构造器还是 8 个参数,测试传了 9 个)或方法不存在(`addFavorite`/`removeFavorite`/`favoriteProductIds` 未定义)

- [ ] **Step 3: 实现 `User.java`**

完整替换 `backend/src/main/java/com/seafood/user/domain/User.java`:

```java
package com.seafood.user.domain;

import com.seafood.shared.error.DomainException;
import com.seafood.shared.error.NotFoundException;
import com.seafood.shared.security.Role;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * User 聚合根 — 单一 record,role 字段充当 Customer/Admin 判别(参见 design.md §6.1)。
 *
 * <p>业务方法集中在聚合根;UserService 不直接操作字段,以便以后想分两类子类时只改这里。
 */
public record User(
        String id,
        String openId,
        String nickname,
        String avatarUrl,
        Role role,
        String phone,
        List<Address> addresses,
        List<String> favoriteProductIds,
        Instant createdAt
) {

    public User {
        if (openId == null || openId.isBlank()) {
            throw new DomainException("openId 不能为空");
        }
        if (role == null) {
            throw new DomainException("role 不能为空");
        }
        addresses = addresses == null ? List.of() : List.copyOf(addresses);
        favoriteProductIds = favoriteProductIds == null ? List.of() : List.copyOf(favoriteProductIds);
    }

    // ----- 地址管理 -----

    /** 新增地址;若 isDefault=true 把其它地址的 default 取消(只能一个默认)。 */
    public User addAddress(Address newAddr) {
        if (newAddr == null) {
            throw new DomainException("地址不能为空");
        }
        String id = newAddr.id() == null || newAddr.id().isBlank()
                ? UUID.randomUUID().toString() : newAddr.id();
        Address normalized = new Address(id, newAddr.name(), newAddr.phone(),
                newAddr.province(), newAddr.city(), newAddr.district(), newAddr.detail(),
                newAddr.isDefault() || addresses.isEmpty());

        List<Address> next = new ArrayList<>(addresses.size() + 1);
        for (Address a : addresses) {
            next.add(new Address(a.id(), a.name(), a.phone(), a.province(),
                    a.city(), a.district(), a.detail(), normalized.isDefault() ? false : a.isDefault()));
        }
        next.add(normalized);
        return mutateAddresses(next);
    }

    public User updateAddress(String addressId, Address patch) {
        Address existing = findAddress(addressId);
        if (existing == null) {
            throw new NotFoundException("地址不存在:" + addressId);
        }
        Address merged = new Address(
                existing.id(),
                patch.name() == null || patch.name().isBlank() ? existing.name() : patch.name(),
                patch.phone() == null || patch.phone().isBlank() ? existing.phone() : patch.phone(),
                patch.province() == null ? existing.province() : patch.province(),
                patch.city() == null ? existing.city() : patch.city(),
                patch.district() == null ? existing.district() : patch.district(),
                patch.detail() == null ? existing.detail() : patch.detail(),
                patch.isDefault() || existing.isDefault()
        );
        List<Address> next = new ArrayList<>(addresses.size());
        for (Address a : addresses) {
            if (a.id().equals(addressId)) {
                next.add(merged);
            } else {
                next.add(new Address(a.id(), a.name(), a.phone(), a.province(),
                        a.city(), a.district(), a.detail(), merged.isDefault() ? false : a.isDefault()));
            }
        }
        return mutateAddresses(next);
    }

    public User removeAddress(String addressId) {
        if (findAddress(addressId) == null) {
            throw new NotFoundException("地址不存在:" + addressId);
        }
        List<Address> next = addresses.stream()
                .filter(a -> !a.id().equals(addressId))
                .toList();
        return mutateAddresses(next);
    }

    public User setDefaultAddress(String addressId) {
        Address target = findAddress(addressId);
        if (target == null) {
            throw new NotFoundException("地址不存在:" + addressId);
        }
        List<Address> next = new ArrayList<>(addresses.size());
        for (Address a : addresses) {
            next.add(new Address(a.id(), a.name(), a.phone(), a.province(),
                    a.city(), a.district(), a.detail(), a.id().equals(addressId)));
        }
        return mutateAddresses(next);
    }

    private Address findAddress(String id) {
        return addresses.stream().filter(a -> a.id().equals(id)).findFirst().orElse(null);
    }

    private User mutateAddresses(List<Address> next) {
        return new User(id, openId, nickname, avatarUrl, role, phone, next, favoriteProductIds, createdAt);
    }

    // ----- 收藏(mp-cross-screen-cleanup 之后的下一个 change:收藏 + 浏览足迹)-----

    /**
     * 收藏商品,幂等(已收藏时原样返回,不重复插入)。新收藏插入列表头部——
     * "最近收藏优先",{@code GET /api/favorites} 按列表原始顺序返回即为该排序,
     * 不需要额外时间戳字段或运行时排序。
     */
    public User addFavorite(String productId) {
        if (productId == null || productId.isBlank()) {
            throw new DomainException("商品 id 不能为空");
        }
        if (favoriteProductIds.contains(productId)) {
            return this;
        }
        List<String> next = new ArrayList<>(favoriteProductIds.size() + 1);
        next.add(productId);
        next.addAll(favoriteProductIds);
        return mutateFavorites(next);
    }

    /** 取消收藏,幂等(未收藏时原样返回)。 */
    public User removeFavorite(String productId) {
        if (!favoriteProductIds.contains(productId)) {
            return this;
        }
        List<String> next = favoriteProductIds.stream()
                .filter(id -> !id.equals(productId))
                .toList();
        return mutateFavorites(next);
    }

    private User mutateFavorites(List<String> next) {
        return new User(id, openId, nickname, avatarUrl, role, phone, addresses, next, createdAt);
    }

    // ----- role helpers -----

    public boolean isAdmin() { return role == Role.ADMIN; }
    public boolean isCustomer() { return role == Role.CUSTOMER; }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && ./gradlew test --tests "com.seafood.user.domain.UserTest"`
Expected: 全部 PASS(原有 10 个 + 新增 5 个 = 15 个测试)

- [ ] **Step 5: 修复 `UserMapper.java`**

`backend/src/main/java/com/seafood/user/infra/UserMapper.java` 里 `toDomain`/`toDocument` 都要加 `favoriteProductIds` 的映射,`toDocument` 用同 `addresses` 一样的 null-safe 兜底:

```java
package com.seafood.user.infra;

import com.seafood.shared.security.Role;
import com.seafood.user.domain.User;

import java.time.Instant;

public final class UserMapper {

    private UserMapper() {}

    public static User toDomain(UserDocument d) {
        if (d == null) return null;
        Role role = d.getRole() == null ? Role.CUSTOMER : Role.valueOf(d.getRole());
        return new User(
                d.getId(),
                d.getOpenId(),
                d.getNickname(),
                d.getAvatarUrl(),
                role,
                d.getPhone(),
                d.getAddresses(),
                d.getFavoriteProductIds(),
                d.getCreatedAt());
    }

    public static UserDocument toDocument(User u) {
        UserDocument d = new UserDocument();
        d.setId(u.id());
        d.setOpenId(u.openId());
        d.setNickname(u.nickname());
        d.setAvatarUrl(u.avatarUrl());
        d.setRole(u.role().name());
        d.setPhone(u.phone());
        d.setAddresses(u.addresses() == null ? java.util.List.of() : u.addresses());
        d.setFavoriteProductIds(u.favoriteProductIds() == null ? java.util.List.of() : u.favoriteProductIds());
        d.setCreatedAt(u.createdAt() == null ? Instant.now() : u.createdAt());
        return d;
    }
}
```

- [ ] **Step 6: 修复 `UserDocument.java`**

在 `backend/src/main/java/com/seafood/user/infra/UserDocument.java` 的 `addresses` 字段之后加 `favoriteProductIds` 字段 + getter/setter:

```java
    private List<Address> addresses;
    private List<String> favoriteProductIds;
    private Instant createdAt;
```

```java
    public List<Address> getAddresses() { return addresses; }
    public void setAddresses(List<Address> addresses) { this.addresses = addresses; }

    public List<String> getFavoriteProductIds() { return favoriteProductIds; }
    public void setFavoriteProductIds(List<String> favoriteProductIds) { this.favoriteProductIds = favoriteProductIds; }

    public Instant getCreatedAt() { return createdAt; }
```

- [ ] **Step 7: 修复 `UserBuilder.java`(测试支持类)**

`backend/src/test/java/com/seafood/testsupport/builders/UserBuilder.java` 加字段 + `withFavoriteProductIds` + `build()` 传参:

```java
    private List<Address> addresses = List.of();
    private List<String> favoriteProductIds = List.of();
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
    public UserBuilder withFavoriteProductIds(List<String> favoriteProductIds) { this.favoriteProductIds = favoriteProductIds; return this; }

    public User build() {
        return new User(id, openId, nickname, avatarUrl, role, phone, addresses, favoriteProductIds, createdAt);
    }
```

- [ ] **Step 8: 全量跑 backend 确认无遗漏的编译错误**

Run: `cd backend && ./gradlew compileJava compileTestJava`
Expected: BUILD SUCCESSFUL(确认没有其它文件还在用旧的 8 参数 `User` 构造器——`grep -rn "new User(" backend/src` 之前已核查过只有这 6 处调用点,这一步是编译期二次确认)

- [ ] **Step 9: Commit**

```bash
cd backend
git add src/main/java/com/seafood/user/domain/User.java \
        src/main/java/com/seafood/user/infra/UserDocument.java \
        src/main/java/com/seafood/user/infra/UserMapper.java \
        src/test/java/com/seafood/testsupport/builders/UserBuilder.java \
        src/test/java/com/seafood/user/domain/UserTest.java
git commit -m "feat(user): User 聚合根新增 favoriteProductIds 字段 + addFavorite/removeFavorite"
```

---

## Task 2: `ProductView` 域对象 + 独立集合(infra 层)

**Files:**
- Create: `backend/src/main/java/com/seafood/user/domain/ProductView.java`
- Create: `backend/src/main/java/com/seafood/user/infra/ProductViewDocument.java`
- Create: `backend/src/main/java/com/seafood/user/infra/ProductViewRepository.java`
- Create: `backend/src/main/java/com/seafood/user/infra/ProductViewMapper.java`
- Modify: `backend/src/main/java/com/seafood/shared/infra/MongoIndexInitializer.java`
- Test: `backend/src/test/java/com/seafood/user/infra/ProductViewRepositorySliceTest.java`

**Interfaces:**
- Consumes: 无(新增独立集合,不依赖 Task 1 的改动)
- Produces: `ProductView(String id, String userId, String productId, Instant viewedAt)` 域 record;`ProductViewDocument`(MongoDB document,collection=`product_views`,无 `@Indexed`/`@CompoundIndex` 注解——唯一约束是手写 critical 索引,见 Step 4);`ProductViewRepository.findByUserIdAndProductId(userId, productId): Optional<ProductViewDocument>`、`findByUserIdOrderByViewedAtDesc(userId): List<ProductViewDocument>`、`countByUserId(userId): long`——Task 4 的 `ProductViewService` 依赖这三个方法的确切签名。

- [ ] **Step 1: 写失败测试(Repository slice)**

Create `backend/src/test/java/com/seafood/user/infra/ProductViewRepositorySliceTest.java`:

```java
package com.seafood.user.infra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Testcontainers
class ProductViewRepositorySliceTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Autowired
    private ProductViewRepository repo;

    private ProductViewDocument doc(String userId, String productId, Instant viewedAt) {
        ProductViewDocument d = new ProductViewDocument();
        d.setUserId(userId);
        d.setProductId(productId);
        d.setViewedAt(viewedAt);
        return d;
    }

    @Test
    void findByUserIdAndProductId_returnsMatch() {
        repo.save(doc("u1", "p1", Instant.parse("2026-07-01T00:00:00Z")));

        Optional<ProductViewDocument> found = repo.findByUserIdAndProductId("u1", "p1");

        assertThat(found).isPresent();
        assertThat(found.get().getProductId()).isEqualTo("p1");
    }

    @Test
    void findByUserIdAndProductId_noMatch_returnsEmpty() {
        assertThat(repo.findByUserIdAndProductId("u1", "nope")).isEmpty();
    }

    @Test
    void findByUserIdOrderByViewedAtDesc_sortsNewestFirst() {
        repo.save(doc("u2", "p1", Instant.parse("2026-07-01T00:00:00Z")));
        repo.save(doc("u2", "p2", Instant.parse("2026-07-03T00:00:00Z")));
        repo.save(doc("u2", "p3", Instant.parse("2026-07-02T00:00:00Z")));

        List<ProductViewDocument> result = repo.findByUserIdOrderByViewedAtDesc("u2");

        assertThat(result).extracting(ProductViewDocument::getProductId)
                .containsExactly("p2", "p3", "p1");
    }

    @Test
    void countByUserId_countsOnlyThatUser() {
        repo.save(doc("u3", "p1", Instant.now()));
        repo.save(doc("u3", "p2", Instant.now()));
        repo.save(doc("u4", "p1", Instant.now()));

        assertThat(repo.countByUserId("u3")).isEqualTo(2);
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && ./gradlew test --tests "com.seafood.user.infra.ProductViewRepositorySliceTest" -PexcludeTags=docker`
Expected: 编译失败(`ProductViewDocument`/`ProductViewRepository` 不存在)。若本机无 Docker,这条 slice test 需要 Testcontainers——先确认本机 Docker 可用(`docker ps`),不可用则跳过 Step 2/4 的实际执行,直接凭代码 review 确认正确性,在 Step 9 commit message 里注明"本机无 Docker,未跑 IT,已凭代码走查确认"。

- [ ] **Step 3: 实现 `ProductView.java`(域对象)**

Create `backend/src/main/java/com/seafood/user/domain/ProductView.java`:

```java
package com.seafood.user.domain;

import java.time.Instant;

/**
 * 浏览足迹域对象(收藏 + 浏览足迹,design.md D1/D2)。
 *
 * <p>不是聚合根(没有需要保护的不变量,去重/裁剪逻辑在
 * {@code ProductViewService} 里,不在这里)——纯数据载体,{@code id} 由
 * MongoDB 自动生成,不在构造时校验。
 */
public record ProductView(String id, String userId, String productId, Instant viewedAt) {
}
```

- [ ] **Step 4: 实现 `ProductViewDocument.java` + `ProductViewRepository.java`**

Create `backend/src/main/java/com/seafood/user/infra/ProductViewDocument.java`:

```java
package com.seafood.user.infra;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * product_views collection(收藏 + 浏览足迹,design.md D1)。
 *
 * <p>{@code userId+productId} 唯一约束是手写 critical 索引(见
 * {@code MongoIndexInitializer}),不用 {@code @CompoundIndex} 注解——这个仓库的
 * 惯例是:annotation-derived 索引失败仅 warn(性能类),而这个唯一约束是
 * upsert/去重语义的正确性前提(同一商品反复查看只保留最新一条),缺失会让
 * "去重刷新 viewedAt" 退化成"每次都新增一条",裁剪到 100 条的语义也会跟着错——
 * 所以走 {@code ensureCritical},同 {@code users.openId} 唯一索引一样。
 */
@Document(collection = "product_views")
public class ProductViewDocument {

    @Id
    private String id;

    private String userId;
    private String productId;
    private Instant viewedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public Instant getViewedAt() { return viewedAt; }
    public void setViewedAt(Instant viewedAt) { this.viewedAt = viewedAt; }
}
```

Create `backend/src/main/java/com/seafood/user/infra/ProductViewRepository.java`:

```java
package com.seafood.user.infra;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ProductViewRepository extends MongoRepository<ProductViewDocument, String> {
    Optional<ProductViewDocument> findByUserIdAndProductId(String userId, String productId);
    List<ProductViewDocument> findByUserIdOrderByViewedAtDesc(String userId);
    long countByUserId(String userId);
}
```

Create `backend/src/main/java/com/seafood/user/infra/ProductViewMapper.java`:

```java
package com.seafood.user.infra;

import com.seafood.user.domain.ProductView;

public final class ProductViewMapper {

    private ProductViewMapper() {}

    public static ProductView toDomain(ProductViewDocument d) {
        if (d == null) return null;
        return new ProductView(d.getId(), d.getUserId(), d.getProductId(), d.getViewedAt());
    }
}
```

- [ ] **Step 5: 跑测试确认通过**

Run: `cd backend && ./gradlew test --tests "com.seafood.user.infra.ProductViewRepositorySliceTest" -PexcludeTags=docker`
Expected: 4/4 PASS(若本机无 Docker,跳过,见 Step 2 备注)

- [ ] **Step 6: 注册手写 critical 唯一索引**

在 `backend/src/main/java/com/seafood/shared/infra/MongoIndexInitializer.java` 里,`ensureCritical("users", ...)` 那个 try 块之后(第 106 行 `criticalFailures.add(e); }` 之后、下一个 `// Sprint 2 §3.3` 注释之前)插入:

```java
        // 收藏 + 浏览足迹 —— product_views.userId+productId 复合唯一索引:
        // 缺失会让"同一商品反复查看只保留最新一条"的去重/裁剪语义失效(退化成每次
        // 都新增一条记录,ProductViewService 的 upsert 逻辑依赖这条唯一约束防止竞态
        // 下的重复插入)。design.md D2。
        try {
            ensureCritical("product_views",
                    new Index().on("userId", org.springframework.data.domain.Sort.Direction.ASC)
                            .on("productId", org.springframework.data.domain.Sort.Direction.ASC)
                            .unique()
                            .named("uk_userId_productId"),
                    "unique userId+productId — required for view-dedup/prune correctness");
        } catch (IndexInitializationException e) {
            criticalFailures.add(e);
        }
```

- [ ] **Step 7: 跑全量 backend 测试**

Run: `cd backend && ./gradlew test -PexcludeTags=docker`
Expected: 全绿,无回归

- [ ] **Step 8: Commit**

```bash
cd backend
git add src/main/java/com/seafood/user/domain/ProductView.java \
        src/main/java/com/seafood/user/infra/ProductViewDocument.java \
        src/main/java/com/seafood/user/infra/ProductViewRepository.java \
        src/main/java/com/seafood/user/infra/ProductViewMapper.java \
        src/main/java/com/seafood/shared/infra/MongoIndexInitializer.java \
        src/test/java/com/seafood/user/infra/ProductViewRepositorySliceTest.java
git commit -m "feat(user): 新增 product_views 集合(浏览足迹独立存储)+ 唯一索引"
```

---

## Task 3: `FavoriteService` + `FavoriteController`

**Files:**
- Create: `backend/src/main/java/com/seafood/user/api/dto/FavoriteItemResponse.java`
- Create: `backend/src/main/java/com/seafood/user/application/FavoriteService.java`
- Create: `backend/src/main/java/com/seafood/user/api/FavoriteController.java`
- Test: `backend/src/test/java/com/seafood/user/application/FavoriteServiceTest.java`
- Test: `backend/src/test/java/com/seafood/user/api/FavoriteControllerTest.java`

**Interfaces:**
- Consumes: `User.addFavorite`/`removeFavorite`/`favoriteProductIds()`(Task 1);`ProductService.get(String id): ProductResponse`(既有,抛 `NotFoundException` 表示商品不存在)
- Produces: `FavoriteService.addFavorite(userId, productId): List<String>`、`removeFavorite(userId, productId): List<String>`、`list(userId): List<FavoriteItemResponse>`——`FavoriteController` 依赖这三个方法名。端点:`POST /api/favorites/{productId}`、`DELETE /api/favorites/{productId}`、`GET /api/favorites`。

- [ ] **Step 1: 写失败测试(FavoriteService)**

Create `backend/src/test/java/com/seafood/user/application/FavoriteServiceTest.java`:

```java
package com.seafood.user.application;

import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.application.ProductService;
import com.seafood.shared.error.NotFoundException;
import com.seafood.shared.security.Role;
import com.seafood.user.api.dto.FavoriteItemResponse;
import com.seafood.user.domain.User;
import com.seafood.user.infra.UserDocument;
import com.seafood.user.infra.UserMapper;
import com.seafood.user.infra.UserRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FavoriteServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final ProductService productService = mock(ProductService.class);
    private final FavoriteService favorites = new FavoriteService(users, productService);

    private User sampleUser(List<String> favoriteProductIds) {
        return new User("u1", "open-1", "nick", null, Role.CUSTOMER, null,
                List.of(), favoriteProductIds, Instant.parse("2026-07-01T00:00:00Z"));
    }

    private void stubLoad(User u) {
        when(users.findById("u1")).thenReturn(Optional.of(UserMapper.toDocument(u)));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void addFavorite_persistsAndReturnsUpdatedList() {
        stubLoad(sampleUser(List.of()));

        List<String> result = favorites.addFavorite("u1", "p1");

        assertThat(result).containsExactly("p1");
    }

    @Test
    void addFavorite_alreadyFavorited_isNoOp_returnsUnchanged() {
        stubLoad(sampleUser(List.of("p1")));

        List<String> result = favorites.addFavorite("u1", "p1");

        assertThat(result).containsExactly("p1");
    }

    @Test
    void removeFavorite_removesFromList() {
        stubLoad(sampleUser(List.of("p1", "p2")));

        List<String> result = favorites.removeFavorite("u1", "p1");

        assertThat(result).containsExactly("p2");
    }

    @Test
    void list_enrichesWithProductInfo() {
        stubLoad(sampleUser(List.of("p1")));
        when(productService.get("p1")).thenReturn(
                new ProductResponse("p1", "三文鱼", "desc", new BigDecimal("58.00"), 10,
                        "鱼类", "http://img/p1.png", com.seafood.product.domain.ProductStatus.ACTIVE,
                        Instant.now(), Instant.now()));

        List<FavoriteItemResponse> result = favorites.list("u1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).productId()).isEqualTo("p1");
        assertThat(result.get(0).productName()).isEqualTo("三文鱼");
        assertThat(result.get(0).available()).isTrue();
    }

    @Test
    void list_unavailableProduct_degradesGracefully() {
        stubLoad(sampleUser(List.of("p-gone")));
        when(productService.get("p-gone")).thenThrow(new NotFoundException("商品不存在:p-gone"));

        List<FavoriteItemResponse> result = favorites.list("u1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).productName()).isEqualTo("商品已下架");
        assertThat(result.get(0).available()).isFalse();
    }

    @Test
    void addFavorite_userNotFound_throws() {
        when(users.findById("nope")).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> favorites.addFavorite("nope", "p1"))
                .isInstanceOf(com.seafood.shared.error.NotFoundException.class);
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && ./gradlew test --tests "com.seafood.user.application.FavoriteServiceTest"`
Expected: 编译失败(`FavoriteService`/`FavoriteItemResponse` 不存在)

- [ ] **Step 3: 实现 `FavoriteItemResponse.java`**

Create `backend/src/main/java/com/seafood/user/api/dto/FavoriteItemResponse.java`:

```java
package com.seafood.user.api.dto;

import java.math.BigDecimal;

/**
 * 收藏列表富化响应(收藏 + 浏览足迹,design.md D3)。
 *
 * <p>{@code available=false} 对应商品已下架/被删除的降级场景,同
 * {@code CartLineItemResponse} 的既有先例:productName 用占位文案,price 置 0,
 * imageUrl 置空——该行仍展示(用户可以取消收藏),只是不可跳转到真实商品详情。
 */
public record FavoriteItemResponse(
        String productId,
        String productName,
        BigDecimal price,
        String imageUrl,
        boolean available
) {
}
```

- [ ] **Step 4: 实现 `FavoriteService.java`**

Create `backend/src/main/java/com/seafood/user/application/FavoriteService.java`:

```java
package com.seafood.user.application;

import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.application.ProductService;
import com.seafood.shared.error.NotFoundException;
import com.seafood.user.api.dto.FavoriteItemResponse;
import com.seafood.user.domain.User;
import com.seafood.user.infra.UserDocument;
import com.seafood.user.infra.UserMapper;
import com.seafood.user.infra.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 收藏服务(收藏 + 浏览足迹,design.md)。self-scoped——身份始终是调用者本人
 * (由 {@link FavoriteController} 从 JWT principal 取,不接受外部 userId 参数,
 * 同 {@code AddressController} 既有惯例),不需要额外授权校验。
 *
 * <p>富化商品信息 + 失效商品降级复用 {@code CartService#enrich} 已确立的先例
 * (ApplicationService → ApplicationService,不直碰 ProductRepository)。
 */
@Service
public class FavoriteService {

    private static final String UNAVAILABLE_PRODUCT_NAME = "商品已下架";

    private final UserRepository users;
    private final ProductService productService;

    public FavoriteService(UserRepository users, ProductService productService) {
        this.users = users;
        this.productService = productService;
    }

    public List<String> addFavorite(String userId, String productId) {
        User u = loadOrThrow(userId);
        User updated = u.addFavorite(productId);
        persist(updated);
        return updated.favoriteProductIds();
    }

    public List<String> removeFavorite(String userId, String productId) {
        User u = loadOrThrow(userId);
        User updated = u.removeFavorite(productId);
        persist(updated);
        return updated.favoriteProductIds();
    }

    public List<FavoriteItemResponse> list(String userId) {
        User u = loadOrThrow(userId);
        return u.favoriteProductIds().stream().map(this::enrich).toList();
    }

    private FavoriteItemResponse enrich(String productId) {
        try {
            ProductResponse p = productService.get(productId);
            return new FavoriteItemResponse(productId, p.name(), p.price(), p.imageUrl(), true);
        } catch (NotFoundException e) {
            return new FavoriteItemResponse(productId, UNAVAILABLE_PRODUCT_NAME, BigDecimal.ZERO, "", false);
        }
    }

    private User loadOrThrow(String userId) {
        return users.findById(userId)
                .map(UserMapper::toDomain)
                .orElseThrow(() -> new NotFoundException("用户不存在:" + userId));
    }

    private void persist(User u) {
        users.save(UserMapper.toDocument(u));
    }
}
```

- [ ] **Step 5: 跑测试确认通过**

Run: `cd backend && ./gradlew test --tests "com.seafood.user.application.FavoriteServiceTest"`
Expected: 6/6 PASS

- [ ] **Step 6: 写失败测试(FavoriteController,纯 Mockito,不走 @WebMvcTest——同 `AddressControllerTest` 既有惯例)**

Create `backend/src/test/java/com/seafood/user/api/FavoriteControllerTest.java`:

```java
package com.seafood.user.api;

import com.seafood.shared.security.Role;
import com.seafood.shared.security.UserPrincipal;
import com.seafood.user.api.dto.FavoriteItemResponse;
import com.seafood.user.application.FavoriteService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FavoriteControllerTest {

    private final FavoriteService favoriteService = mock(FavoriteService.class);
    private final FavoriteController controller = new FavoriteController(favoriteService);
    private final UserPrincipal me = new UserPrincipal("u-1", Role.CUSTOMER);

    @Test
    void add_delegatesWithPrincipalId() {
        when(favoriteService.addFavorite("u-1", "p1")).thenReturn(List.of("p1"));

        List<String> result = controller.add("p1", me);

        assertThat(result).containsExactly("p1");
        verify(favoriteService).addFavorite("u-1", "p1");
    }

    @Test
    void remove_delegatesWithPrincipalId() {
        when(favoriteService.removeFavorite("u-1", "p1")).thenReturn(List.of());

        List<String> result = controller.remove("p1", me);

        assertThat(result).isEmpty();
        verify(favoriteService).removeFavorite("u-1", "p1");
    }

    @Test
    void list_delegatesWithPrincipalId() {
        FavoriteItemResponse item = new FavoriteItemResponse("p1", "三文鱼", new BigDecimal("58.00"), "http://img", true);
        when(favoriteService.list("u-1")).thenReturn(List.of(item));

        List<FavoriteItemResponse> result = controller.list(me);

        assertThat(result).containsExactly(item);
    }
}
```

- [ ] **Step 7: 跑测试确认失败**

Run: `cd backend && ./gradlew test --tests "com.seafood.user.api.FavoriteControllerTest"`
Expected: 编译失败(`FavoriteController` 不存在)

- [ ] **Step 8: 实现 `FavoriteController.java`**

Create `backend/src/main/java/com/seafood/user/api/FavoriteController.java`:

```java
package com.seafood.user.api;

import com.seafood.shared.security.UserPrincipal;
import com.seafood.user.api.dto.FavoriteItemResponse;
import com.seafood.user.application.FavoriteService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收藏 API(self-scoped 门面,同 {@code AddressController}/{@code CartController}
 * 既有惯例——身份取自 JWT principal,不接受外部 userId 参数)。
 */
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favorites;

    public FavoriteController(FavoriteService favorites) {
        this.favorites = favorites;
    }

    @PostMapping("/{productId}")
    @PreAuthorize("isAuthenticated()")
    public List<String> add(@PathVariable String productId, @AuthenticationPrincipal UserPrincipal me) {
        return favorites.addFavorite(me.getId(), productId);
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("isAuthenticated()")
    public List<String> remove(@PathVariable String productId, @AuthenticationPrincipal UserPrincipal me) {
        return favorites.removeFavorite(me.getId(), productId);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<FavoriteItemResponse> list(@AuthenticationPrincipal UserPrincipal me) {
        return favorites.list(me.getId());
    }
}
```

- [ ] **Step 9: 跑测试确认通过**

Run: `cd backend && ./gradlew test --tests "com.seafood.user.api.FavoriteControllerTest"`
Expected: 3/3 PASS

- [ ] **Step 10: Commit**

```bash
cd backend
git add src/main/java/com/seafood/user/api/dto/FavoriteItemResponse.java \
        src/main/java/com/seafood/user/application/FavoriteService.java \
        src/main/java/com/seafood/user/api/FavoriteController.java \
        src/test/java/com/seafood/user/application/FavoriteServiceTest.java \
        src/test/java/com/seafood/user/api/FavoriteControllerTest.java
git commit -m "feat(user): 新增 FavoriteService + /api/favorites 端点(收藏/取消收藏/列表)"
```

---

## Task 4: `ProductViewService` + `ProductViewController`

**Files:**
- Create: `backend/src/main/java/com/seafood/user/api/dto/ProductViewResponse.java`
- Create: `backend/src/main/java/com/seafood/user/application/ProductViewService.java`
- Create: `backend/src/main/java/com/seafood/user/api/ProductViewController.java`
- Test: `backend/src/test/java/com/seafood/user/application/ProductViewServiceTest.java`
- Test: `backend/src/test/java/com/seafood/user/api/ProductViewControllerTest.java`

**Interfaces:**
- Consumes: `ProductViewRepository.findByUserIdAndProductId`/`findByUserIdOrderByViewedAtDesc`/`countByUserId`(Task 2);`ProductService.get`(既有)
- Produces: `ProductViewService.record(userId, productId): void`(upsert 语义:存在则刷新 `viewedAt`,不存在则插入,写入后裁剪超出 100 条的记录)、`list(userId): List<ProductViewResponse>`、`countForUser(userId): long`——Task 5 的 `UserService` 依赖 `countForUser` 这个确切方法名。端点:`POST /api/product-views/{productId}`(204)、`GET /api/product-views`。

- [ ] **Step 1: 写失败测试(ProductViewService)**

Create `backend/src/test/java/com/seafood/user/application/ProductViewServiceTest.java`:

```java
package com.seafood.user.application;

import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.application.ProductService;
import com.seafood.shared.error.NotFoundException;
import com.seafood.user.api.dto.ProductViewResponse;
import com.seafood.user.infra.ProductViewDocument;
import com.seafood.user.infra.ProductViewRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductViewServiceTest {

    private final ProductViewRepository views = mock(ProductViewRepository.class);
    private final ProductService productService = mock(ProductService.class);
    private final ProductViewService service = new ProductViewService(views, productService);

    private ProductViewDocument doc(String userId, String productId, Instant viewedAt) {
        ProductViewDocument d = new ProductViewDocument();
        d.setId(productId + "-doc");
        d.setUserId(userId);
        d.setProductId(productId);
        d.setViewedAt(viewedAt);
        return d;
    }

    @Test
    void record_newProduct_insertsDocument() {
        when(views.findByUserIdAndProductId("u1", "p1")).thenReturn(Optional.empty());
        when(views.findByUserIdOrderByViewedAtDesc("u1")).thenReturn(List.of());

        service.record("u1", "p1");

        ArgumentCaptor<ProductViewDocument> captor = ArgumentCaptor.forClass(ProductViewDocument.class);
        verify(views).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("u1");
        assertThat(captor.getValue().getProductId()).isEqualTo("p1");
    }

    @Test
    void record_existingProduct_refreshesViewedAt_doesNotDuplicate() {
        ProductViewDocument existing = doc("u1", "p1", Instant.parse("2026-07-01T00:00:00Z"));
        when(views.findByUserIdAndProductId("u1", "p1")).thenReturn(Optional.of(existing));
        when(views.findByUserIdOrderByViewedAtDesc("u1")).thenReturn(List.of(existing));

        service.record("u1", "p1");

        ArgumentCaptor<ProductViewDocument> captor = ArgumentCaptor.forClass(ProductViewDocument.class);
        verify(views).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(existing.getId());
        assertThat(captor.getValue().getViewedAt()).isAfter(Instant.parse("2026-07-01T00:00:00Z"));
        verify(views, times(1)).save(any());
    }

    @Test
    void record_prunesBeyond100_deletesOldest() {
        when(views.findByUserIdAndProductId("u1", "p101")).thenReturn(Optional.empty());
        List<ProductViewDocument> existing101 = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            existing101.add(doc("u1", "p" + i, Instant.parse("2026-07-01T00:00:00Z").plusSeconds(i)));
        }
        // findByUserIdOrderByViewedAtDesc 按倒序返回 —— 最新在前
        List<ProductViewDocument> sortedDesc = new ArrayList<>(existing101);
        java.util.Collections.reverse(sortedDesc);
        when(views.findByUserIdOrderByViewedAtDesc("u1")).thenReturn(sortedDesc);

        service.record("u1", "p101");

        verify(views).deleteAll(anyList());
    }

    @Test
    void list_enrichesWithProductInfo_sortedByViewedAtDesc() {
        ProductViewDocument d1 = doc("u1", "p1", Instant.parse("2026-07-02T00:00:00Z"));
        when(views.findByUserIdOrderByViewedAtDesc("u1")).thenReturn(List.of(d1));
        when(productService.get("p1")).thenReturn(
                new ProductResponse("p1", "龙虾", "desc", new BigDecimal("128.00"), 5,
                        "虾蟹", "http://img/p1.png", com.seafood.product.domain.ProductStatus.ACTIVE,
                        Instant.now(), Instant.now()));

        List<ProductViewResponse> result = service.list("u1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).productName()).isEqualTo("龙虾");
        assertThat(result.get(0).available()).isTrue();
    }

    @Test
    void list_unavailableProduct_degradesGracefully() {
        ProductViewDocument d1 = doc("u1", "p-gone", Instant.now());
        when(views.findByUserIdOrderByViewedAtDesc("u1")).thenReturn(List.of(d1));
        when(productService.get("p-gone")).thenThrow(new NotFoundException("商品不存在:p-gone"));

        List<ProductViewResponse> result = service.list("u1");

        assertThat(result.get(0).productName()).isEqualTo("商品已下架");
        assertThat(result.get(0).available()).isFalse();
    }

    @Test
    void countForUser_delegatesToRepository() {
        when(views.countByUserId("u1")).thenReturn(3L);

        assertThat(service.countForUser("u1")).isEqualTo(3L);
    }
}
```

（顶部 import 需补 `org.mockito.ArgumentCaptor` 与 `static org.mockito.ArgumentMatchers.anyList`;上面 `import static org.mockito.Mockito.*` 已覆盖 `mock/verify/when/times`,但 `ArgumentCaptor` 类本身需要显式 `import org.mockito.ArgumentCaptor;`。)

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && ./gradlew test --tests "com.seafood.user.application.ProductViewServiceTest"`
Expected: 编译失败(`ProductViewService`/`ProductViewResponse` 不存在)

- [ ] **Step 3: 实现 `ProductViewResponse.java`**

Create `backend/src/main/java/com/seafood/user/api/dto/ProductViewResponse.java`:

```java
package com.seafood.user.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 浏览足迹列表富化响应(收藏 + 浏览足迹,design.md D3)。字段/降级语义同
 * {@link FavoriteItemResponse},多一个 {@code viewedAt} 用于按时间倒序展示。
 */
public record ProductViewResponse(
        String productId,
        String productName,
        BigDecimal price,
        String imageUrl,
        boolean available,
        Instant viewedAt
) {
}
```

- [ ] **Step 4: 实现 `ProductViewService.java`**

Create `backend/src/main/java/com/seafood/user/application/ProductViewService.java`:

```java
package com.seafood.user.application;

import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.application.ProductService;
import com.seafood.shared.error.NotFoundException;
import com.seafood.user.api.dto.ProductViewResponse;
import com.seafood.user.infra.ProductViewDocument;
import com.seafood.user.infra.ProductViewRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 浏览足迹服务(收藏 + 浏览足迹,design.md D1/D2)。
 *
 * <p>{@link #record}:同一商品反复查看只保留最新一条(按 userId+productId 查
 * 命中则刷新 viewedAt,不存在才插入),写入后裁剪超出 {@link #MAX_RECENT} 条的
 * 记录 —— 惰性裁剪(每次写入后查一次当前用户的全部足迹,超出部分整批删除),
 * 不用 TTL index(TTL 是按绝对时间过期,这里要的是"每人最近 N 条"的相对裁剪,
 * 语义不同,design.md D2)。
 */
@Service
public class ProductViewService {

    private static final int MAX_RECENT = 100;
    private static final String UNAVAILABLE_PRODUCT_NAME = "商品已下架";

    private final ProductViewRepository views;
    private final ProductService productService;

    public ProductViewService(ProductViewRepository views, ProductService productService) {
        this.views = views;
        this.productService = productService;
    }

    public void record(String userId, String productId) {
        ProductViewDocument doc = views.findByUserIdAndProductId(userId, productId).orElse(null);
        if (doc == null) {
            doc = new ProductViewDocument();
            doc.setUserId(userId);
            doc.setProductId(productId);
        }
        doc.setViewedAt(Instant.now());
        views.save(doc);
        prune(userId);
    }

    public List<ProductViewResponse> list(String userId) {
        return views.findByUserIdOrderByViewedAtDesc(userId).stream().map(this::enrich).toList();
    }

    public long countForUser(String userId) {
        return views.countByUserId(userId);
    }

    /** 按 viewedAt 降序取第 {@link #MAX_RECENT}+1 条开始的全部删除。 */
    private void prune(String userId) {
        List<ProductViewDocument> all = views.findByUserIdOrderByViewedAtDesc(userId);
        if (all.size() > MAX_RECENT) {
            views.deleteAll(all.subList(MAX_RECENT, all.size()));
        }
    }

    private ProductViewResponse enrich(ProductViewDocument d) {
        try {
            ProductResponse p = productService.get(d.getProductId());
            return new ProductViewResponse(d.getProductId(), p.name(), p.price(), p.imageUrl(), true, d.getViewedAt());
        } catch (NotFoundException e) {
            return new ProductViewResponse(d.getProductId(), UNAVAILABLE_PRODUCT_NAME, BigDecimal.ZERO, "", false, d.getViewedAt());
        }
    }
}
```

- [ ] **Step 5: 跑测试确认通过**

Run: `cd backend && ./gradlew test --tests "com.seafood.user.application.ProductViewServiceTest"`
Expected: 6/6 PASS

- [ ] **Step 6: 写失败测试(ProductViewController)**

Create `backend/src/test/java/com/seafood/user/api/ProductViewControllerTest.java`:

```java
package com.seafood.user.api;

import com.seafood.shared.security.Role;
import com.seafood.shared.security.UserPrincipal;
import com.seafood.user.api.dto.ProductViewResponse;
import com.seafood.user.application.ProductViewService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductViewControllerTest {

    private final ProductViewService productViewService = mock(ProductViewService.class);
    private final ProductViewController controller = new ProductViewController(productViewService);
    private final UserPrincipal me = new UserPrincipal("u-1", Role.CUSTOMER);

    @Test
    void record_delegatesWithPrincipalId() {
        controller.record("p1", me);

        verify(productViewService).record("u-1", "p1");
    }

    @Test
    void list_delegatesWithPrincipalId() {
        ProductViewResponse item = new ProductViewResponse("p1", "龙虾", new BigDecimal("128.00"), "http://img", true, Instant.now());
        when(productViewService.list("u-1")).thenReturn(List.of(item));

        List<ProductViewResponse> result = controller.list(me);

        assertThat(result).containsExactly(item);
    }
}
```

- [ ] **Step 7: 跑测试确认失败**

Run: `cd backend && ./gradlew test --tests "com.seafood.user.api.ProductViewControllerTest"`
Expected: 编译失败(`ProductViewController` 不存在)

- [ ] **Step 8: 实现 `ProductViewController.java`**

Create `backend/src/main/java/com/seafood/user/api/ProductViewController.java`:

```java
package com.seafood.user.api;

import com.seafood.shared.security.UserPrincipal;
import com.seafood.user.api.dto.ProductViewResponse;
import com.seafood.user.application.ProductViewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 浏览足迹 API(self-scoped 门面,同 {@link FavoriteController} 惯例)。
 * {@link #record} 是 best-effort 记录(design.md D6:失败不影响商品详情页渲染),
 * 204 No Content——前端不关心返回体。
 */
@RestController
@RequestMapping("/api/product-views")
public class ProductViewController {

    private final ProductViewService productViews;

    public ProductViewController(ProductViewService productViews) {
        this.productViews = productViews;
    }

    @PostMapping("/{productId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> record(@PathVariable String productId, @AuthenticationPrincipal UserPrincipal me) {
        productViews.record(me.getId(), productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ProductViewResponse> list(@AuthenticationPrincipal UserPrincipal me) {
        return productViews.list(me.getId());
    }
}
```

- [ ] **Step 9: 跑测试确认通过**

Run: `cd backend && ./gradlew test --tests "com.seafood.user.api.ProductViewControllerTest"`
Expected: 2/2 PASS

- [ ] **Step 10: Commit**

```bash
cd backend
git add src/main/java/com/seafood/user/api/dto/ProductViewResponse.java \
        src/main/java/com/seafood/user/application/ProductViewService.java \
        src/main/java/com/seafood/user/api/ProductViewController.java \
        src/test/java/com/seafood/user/application/ProductViewServiceTest.java \
        src/test/java/com/seafood/user/api/ProductViewControllerTest.java
git commit -m "feat(user): 新增 ProductViewService + /api/product-views 端点(记录/列表)"
```

---

## Task 5: `GET /api/users/me` 响应体加 `favoriteCount`/`viewCount`

**Files:**
- Modify: `backend/src/main/java/com/seafood/user/api/dto/UserResponse.java`
- Modify: `backend/src/main/java/com/seafood/user/application/UserService.java`
- Test: `backend/src/test/java/com/seafood/user/application/UserServiceTest.java`

**Interfaces:**
- Consumes: `ProductViewService.countForUser(userId): long`(Task 4);`User.favoriteProductIds()`(Task 1)
- Produces: `UserResponse` 新增 `favoriteCount: int`、`viewCount: int` 字段——Task 9(profile.js)依赖响应体里这两个确切字段名。

- [ ] **Step 1: 写失败测试**

在 `backend/src/test/java/com/seafood/user/application/UserServiceTest.java` 里找到已有的 `get` 相关测试(读该文件确认具体位置和既有 mock 风格后)追加一条:

```java
    @Test
    void get_includesFavoriteAndViewCounts() {
        User u = com.seafood.testsupport.builders.UserBuilder.aUser()
                .withId("u1")
                .withFavoriteProductIds(java.util.List.of("p1", "p2"))
                .build();
        when(users.findById("u1")).thenReturn(java.util.Optional.of(UserMapper.toDocument(u)));
        when(productViewService.countForUser("u1")).thenReturn(5L);
        UserPrincipal me = new UserPrincipal("u1", Role.CUSTOMER);

        UserResponse result = userService.get("u1", me);

        assertThat(result.favoriteCount()).isEqualTo(2);
        assertThat(result.viewCount()).isEqualTo(5);
    }
```

（若既有测试类用不同的 mock/构造风格,以该文件已有的写法为准调整这条新增测试的写法,断言目标不变:`get()` 返回值的 `favoriteCount`/`viewCount` 分别等于收藏数组长度和 `ProductViewService.countForUser` 的返回值。若既有测试类直接 `new UserService(users)` 单参数构造,这一步的 Step 2 红会体现为"构造器参数数量不匹配"。）

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && ./gradlew test --tests "com.seafood.user.application.UserServiceTest"`
Expected: 编译失败(`UserResponse.favoriteCount()`/`viewCount()` 不存在,或 `UserService` 构造器参数不匹配)

- [ ] **Step 3: 修改 `UserResponse.java`**

完整替换 `backend/src/main/java/com/seafood/user/api/dto/UserResponse.java`:

```java
package com.seafood.user.api.dto;

import com.seafood.shared.security.Role;
import com.seafood.user.domain.Address;
import com.seafood.user.domain.User;

import java.time.Instant;
import java.util.List;

public record UserResponse(
        String id,
        String openId,
        String nickname,
        String avatarUrl,
        String role,
        String phone,
        List<Address> addresses,
        Instant createdAt,
        int favoriteCount,
        int viewCount
) {
    public static UserResponse from(User u, long viewCount) {
        return new UserResponse(
                u.id(), u.openId(), u.nickname(), u.avatarUrl(),
                u.role().name(), u.phone(), u.addresses(), u.createdAt(),
                u.favoriteProductIds().size(), (int) viewCount);
    }

    public static Role roleOf(UserResponse r) {
        return Role.valueOf(r.role());
    }
}
```

（`favoriteCount`/`viewCount` 放在 `createdAt` 之后——`UserResponse.from` 的签名从 `from(User)` 改成 `from(User, long viewCount)`,是本 Task 唯一的破坏性签名变更,下一步修 `UserService` 的两处调用点。）

- [ ] **Step 4: 修改 `UserService.java`**

完整替换 `backend/src/main/java/com/seafood/user/application/UserService.java`:

```java
package com.seafood.user.application;

import com.seafood.shared.error.DomainException;
import com.seafood.shared.error.NotFoundException;
import com.seafood.shared.security.Role;
import com.seafood.shared.security.UserPrincipal;
import com.seafood.user.api.dto.AddAddressRequest;
import com.seafood.user.api.dto.UpdateAddressRequest;
import com.seafood.user.api.dto.UserResponse;
import com.seafood.user.domain.Address;
import com.seafood.user.domain.User;
import com.seafood.user.infra.UserDocument;
import com.seafood.user.infra.UserMapper;
import com.seafood.user.infra.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户服务(参见 specs/backend-api §未直接列出 / specs/auth §End-to-end 用户生命周期)。
 *
 * <p>权限:所有写操作要求操作者是自己(principal.id == userId)或 ADMIN。
 */
@Service
public class UserService {

    private final UserRepository users;
    private final ProductViewService productViews;

    public UserService(UserRepository users, ProductViewService productViews) {
        this.users = users;
        this.productViews = productViews;
    }

    // ----- 读 -----

    public UserResponse get(String userId, UserPrincipal caller) {
        authorize(caller, userId, true);
        User u = loadOrThrow(userId);
        return UserResponse.from(u, productViews.countForUser(userId));
    }

    public Page<UserResponse> list(Pageable pageable, UserPrincipal caller) {
        requireAdmin(caller);
        Page<UserDocument> page = users.findAll(pageable);
        List<UserResponse> mapped = page.getContent().stream()
                .map(UserMapper::toDomain)
                .map(u -> UserResponse.from(u, productViews.countForUser(u.id())))
                .toList();
        return new PageImpl<>(mapped, pageable, page.getTotalElements());
    }

    // ----- 写(自己或 ADMIN)-----

    public UserResponse addAddress(String userId, AddAddressRequest req, UserPrincipal caller) {
        authorize(caller, userId, false);
        User u = loadOrThrow(userId);
        Address newAddr = new Address(null, req.name(), req.phone(),
                req.province(), req.city(), req.district(), req.detail(), req.isDefault());
        return persistAndReturn(u.addAddress(newAddr));
    }

    public UserResponse updateAddress(String userId, String addressId,
                                      UpdateAddressRequest req, UserPrincipal caller) {
        authorize(caller, userId, false);
        User u = loadOrThrow(userId);
        Address patch = new Address(addressId, req.name(), req.phone(),
                req.province(), req.city(), req.district(), req.detail(), req.isDefault());
        return persistAndReturn(u.updateAddress(addressId, patch));
    }

    public UserResponse removeAddress(String userId, String addressId, UserPrincipal caller) {
        authorize(caller, userId, false);
        User u = loadOrThrow(userId);
        return persistAndReturn(u.removeAddress(addressId));
    }

    public UserResponse setDefaultAddress(String userId, String addressId, UserPrincipal caller) {
        authorize(caller, userId, false);
        User u = loadOrThrow(userId);
        return persistAndReturn(u.setDefaultAddress(addressId));
    }

    // ----- helpers -----

    private User loadOrThrow(String userId) {
        return users.findById(userId)
                .map(UserMapper::toDomain)
                .orElseThrow(() -> new NotFoundException("用户不存在:" + userId));
    }

    private UserResponse persistAndReturn(User u) {
        UserDocument saved = users.save(UserMapper.toDocument(u));
        User reloaded = UserMapper.toDomain(saved);
        return UserResponse.from(reloaded, productViews.countForUser(reloaded.id()));
    }

    private static void authorize(UserPrincipal caller, String targetUserId, boolean readOnly) {
        if (caller == null) {
            throw new DomainException("未登录");
        }
        if (caller.getRole() == Role.ADMIN) {
            return;
        }
        if (!caller.getId().equals(targetUserId)) {
            throw new DomainException(readOnly ? "无权查看该用户" : "无权操作该用户");
        }
    }

    private static void requireAdmin(UserPrincipal caller) {
        if (caller == null || caller.getRole() != Role.ADMIN) {
            throw new DomainException("仅管理员可访问");
        }
    }
}
```

- [ ] **Step 5: 跑测试确认通过**

Run: `cd backend && ./gradlew test --tests "com.seafood.user.application.UserServiceTest" --tests "com.seafood.user.application.UserServiceSliceTest" --tests "com.seafood.user.api.AddressControllerTest"`
Expected: 全绿(`UserServiceTest`/`UserServiceSliceTest` 因为 `UserService` 构造器新增了 `ProductViewService` 参数,需要确认这两个既有测试文件的实例化方式——若直接 `new UserService(users)`,要改成 `new UserService(users, productViewService)` 并 mock 这个新依赖;`AddressControllerTest` 不直接受影响,只是回归确认)

- [ ] **Step 6: 全量跑 backend**

Run: `cd backend && ./gradlew test -PexcludeTags=docker`
Expected: 全绿,无回归(这一步会暴露任何其它直接构造 `UserService`/依赖 `UserResponse.from(User)` 单参数签名的遗漏调用点——若有,按 Step 3/4 同样方式修复)

- [ ] **Step 7: Commit**

```bash
cd backend
git add src/main/java/com/seafood/user/api/dto/UserResponse.java \
        src/main/java/com/seafood/user/application/UserService.java \
        src/test/java/com/seafood/user/application/UserServiceTest.java
git commit -m "feat(user): GET /api/users/me 响应体加 favoriteCount/viewCount"
```

---

## Task 6: 前端 `favorite` feature API 客户端

**Files:**
- Create: `frontend/src/features/favorite/api.ts`
- Create: `frontend/src/features/favorite/api.js`
- Create: `frontend/src/features/favorite/api.test.js`

**Interfaces:**
- Produces: `FavoriteAPI.add(productId: string): Promise<string[]>`、`FavoriteAPI.remove(productId: string): Promise<string[]>`、`FavoriteAPI.list(): Promise<FavoriteItem[]>`(`FavoriteItem = {productId, productName, price, imageUrl, available}`)——Task 8(product-detail.js)、Task 10(favorites-list.js)依赖这三个方法名和参数形状。

- [ ] **Step 1: 写失败测试(直接 require .js shim,不用 `require('./api')`——防止 ts/js drift,同 `user/api.test.js` 既有惯例)**

Create `frontend/src/features/favorite/api.test.js`:

```javascript
/**
 * favorite/api.js(mp 运行时真实执行的 shim)单测。
 *
 * 收藏 + 浏览足迹:直接 require('./api.js')(显式扩展名),不用
 * require('./api'),避免 Jest moduleFileExtensions(ts 排在 js 前面)把测试
 * 悄悄绕回 api.ts —— 同 order/api-shim-contract.test.js / user/api.test.js
 * 记录过的"测 ts 不测 js"坑。
 */
jest.mock('../../shared/api/request', () => ({
  get: jest.fn(),
  post: jest.fn(),
  del: jest.fn(),
}));

const { FavoriteAPI } = require('./api.js');
const { get, post, del } = require('../../shared/api/request');

describe('favorite/api.js shim', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('导出 add/remove/list 三个方法(函数)', () => {
    expect(typeof FavoriteAPI.add).toBe('function');
    expect(typeof FavoriteAPI.remove).toBe('function');
    expect(typeof FavoriteAPI.list).toBe('function');
  });

  it('add(productId) 调 POST /favorites/{id} 且带 needAuth: true', async () => {
    post.mockResolvedValue(['p1']);

    const result = await FavoriteAPI.add('p1');

    expect(post).toHaveBeenCalledWith('/favorites/p1', undefined, { needAuth: true });
    expect(result).toEqual(['p1']);
  });

  it('remove(productId) 调 DELETE /favorites/{id} 且带 needAuth: true', async () => {
    del.mockResolvedValue([]);

    const result = await FavoriteAPI.remove('p1');

    expect(del).toHaveBeenCalledWith('/favorites/p1', { needAuth: true });
    expect(result).toEqual([]);
  });

  it('list() 调 GET /favorites 且带 needAuth: true', async () => {
    const items = [{ productId: 'p1', productName: '三文鱼', price: 58, imageUrl: 'http://img', available: true }];
    get.mockResolvedValue(items);

    const result = await FavoriteAPI.list();

    expect(get).toHaveBeenCalledWith('/favorites', { needAuth: true });
    expect(result).toEqual(items);
  });

  it('add 对商品 id 做 encodeURIComponent(防止 id 含特殊字符拼坏 URL)', async () => {
    post.mockResolvedValue([]);

    await FavoriteAPI.add('p/1');

    expect(post).toHaveBeenCalledWith('/favorites/p%2F1', undefined, { needAuth: true });
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && npm test -- favorite/api`
Expected: 失败(`frontend/src/features/favorite/api.js` 不存在)

- [ ] **Step 3: 实现 `api.ts`(类型契约,mp 运行时不执行)**

Create `frontend/src/features/favorite/api.ts`:

```typescript
/**
 * Favorite feature: API client.
 *
 * 收藏 + 浏览足迹(design.md):self-scoped 门面,同 /api/addresses、/api/cart
 * 既有惯例——身份取自后端 JWT principal,不在 URL 里带 userId。
 *
 *   POST   /api/favorites/{productId}  — 收藏(幂等)
 *   DELETE /api/favorites/{productId}  — 取消收藏(幂等)
 *   GET    /api/favorites              — 收藏列表(富化)
 */
import { del, get, post } from '../../shared/api/request';

export interface FavoriteItem {
  productId: string;
  productName: string;
  price: number;
  imageUrl: string;
  available: boolean;
}

export const FavoriteAPI = {
  add(productId: string): Promise<string[]> {
    return post<string[]>(`/favorites/${encodeURIComponent(productId)}`, undefined, { needAuth: true });
  },
  remove(productId: string): Promise<string[]> {
    return del<string[]>(`/favorites/${encodeURIComponent(productId)}`, { needAuth: true });
  },
  list(): Promise<FavoriteItem[]> {
    return get<FavoriteItem[]>('/favorites', { needAuth: true });
  },
};
```

- [ ] **Step 4: 实现 `api.js`(mp 运行时真实加载的 shim)**

Create `frontend/src/features/favorite/api.js`:

```javascript
/**
 * Runtime shim for features/favorite/api.ts.
 *
 * 收藏 + 浏览足迹:self-scoped 门面,同 /api/addresses、/api/cart 既有惯例。
 */
const { get, post, del } = require('../../shared/api/request');

const FavoriteAPI = {
  add(productId) {
    return post(`/favorites/${encodeURIComponent(productId)}`, undefined, { needAuth: true });
  },
  remove(productId) {
    return del(`/favorites/${encodeURIComponent(productId)}`, { needAuth: true });
  },
  list() {
    return get('/favorites', { needAuth: true });
  },
};

module.exports = { FavoriteAPI };
```

- [ ] **Step 5: 跑测试确认通过**

Run: `cd frontend && npm test -- favorite/api`
Expected: 5/5 PASS

- [ ] **Step 6: Commit**

```bash
cd frontend
git add src/features/favorite/api.ts src/features/favorite/api.js src/features/favorite/api.test.js
git commit -m "feat(mp): 新增 favorite feature API 客户端(ts + js shim 成对)"
```

---

## Task 7: 前端 `productView` feature API 客户端

**Files:**
- Create: `frontend/src/features/productView/api.ts`
- Create: `frontend/src/features/productView/api.js`
- Create: `frontend/src/features/productView/api.test.js`

**Interfaces:**
- Produces: `ProductViewAPI.record(productId: string): Promise<void>`、`ProductViewAPI.list(): Promise<ProductViewItem[]>`(`ProductViewItem = {productId, productName, price, imageUrl, available, viewedAt}`)——Task 8(product-detail.js)、Task 11(footprints-list.js)依赖。

- [ ] **Step 1: 写失败测试**

Create `frontend/src/features/productView/api.test.js`:

```javascript
/**
 * productView/api.js(mp 运行时真实执行的 shim)单测。同 favorite/api.test.js
 * 惯例:直接 require('./api.js'),不走 './api',防 ts/js drift。
 */
jest.mock('../../shared/api/request', () => ({
  get: jest.fn(),
  post: jest.fn(),
}));

const { ProductViewAPI } = require('./api.js');
const { get, post } = require('../../shared/api/request');

describe('productView/api.js shim', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('导出 record/list 两个方法(函数)', () => {
    expect(typeof ProductViewAPI.record).toBe('function');
    expect(typeof ProductViewAPI.list).toBe('function');
  });

  it('record(productId) 调 POST /product-views/{id} 且带 needAuth: true', async () => {
    post.mockResolvedValue(undefined);

    await ProductViewAPI.record('p1');

    expect(post).toHaveBeenCalledWith('/product-views/p1', undefined, { needAuth: true });
  });

  it('list() 调 GET /product-views 且带 needAuth: true', async () => {
    const items = [{ productId: 'p1', productName: '龙虾', price: 128, imageUrl: 'http://img', available: true, viewedAt: '2026-07-06T00:00:00Z' }];
    get.mockResolvedValue(items);

    const result = await ProductViewAPI.list();

    expect(get).toHaveBeenCalledWith('/product-views', { needAuth: true });
    expect(result).toEqual(items);
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && npm test -- productView/api`
Expected: 失败(`frontend/src/features/productView/api.js` 不存在)

- [ ] **Step 3: 实现 `api.ts`**

Create `frontend/src/features/productView/api.ts`:

```typescript
/**
 * ProductView feature: API client.
 *
 * 收藏 + 浏览足迹(design.md D6):record() 是 best-effort 调用方(product-detail.js
 * onLoad)负责吞掉异常,这里不做额外重试/降级。
 *
 *   POST /api/product-views/{productId} — 记一条足迹(静默,upsert)
 *   GET  /api/product-views             — 足迹列表(富化,按 viewedAt 降序)
 */
import { get, post } from '../../shared/api/request';

export interface ProductViewItem {
  productId: string;
  productName: string;
  price: number;
  imageUrl: string;
  available: boolean;
  viewedAt: string;
}

export const ProductViewAPI = {
  record(productId: string): Promise<void> {
    return post<void>(`/product-views/${encodeURIComponent(productId)}`, undefined, { needAuth: true });
  },
  list(): Promise<ProductViewItem[]> {
    return get<ProductViewItem[]>('/product-views', { needAuth: true });
  },
};
```

- [ ] **Step 4: 实现 `api.js`**

Create `frontend/src/features/productView/api.js`:

```javascript
/**
 * Runtime shim for features/productView/api.ts.
 */
const { get, post } = require('../../shared/api/request');

const ProductViewAPI = {
  record(productId) {
    return post(`/product-views/${encodeURIComponent(productId)}`, undefined, { needAuth: true });
  },
  list() {
    return get('/product-views', { needAuth: true });
  },
};

module.exports = { ProductViewAPI };
```

- [ ] **Step 5: 跑测试确认通过**

Run: `cd frontend && npm test -- productView/api`
Expected: 3/3 PASS

- [ ] **Step 6: Commit**

```bash
cd frontend
git add src/features/productView/api.ts src/features/productView/api.js src/features/productView/api.test.js
git commit -m "feat(mp): 新增 productView feature API 客户端(ts + js shim 成对)"
```

---

## Task 8: `product-detail.js`/`.wxml` 接真实收藏 + 静默记足迹

**Files:**
- Modify: `frontend/pages-sub/product/product-detail/product-detail.js`
- Modify: `frontend/pages-sub/product/product-detail/product-detail.wxml`
- Modify: `frontend/pages-sub/product/product-detail/__tests__/product-detail.test.js`

**Interfaces:**
- Consumes: `FavoriteAPI.add/remove/list`(Task 6)、`ProductViewAPI.record`(Task 7)

- [ ] **Step 1: 读现有测试文件,确认 `onLoad`/`onToggleFavorite`/`onFavoriteTap` 现有测试的 mock 风格**

Run: `cd frontend && grep -n "onLoad\|onToggleFavorite\|onFavoriteTap\|describe(" pages-sub/product/product-detail/__tests__/product-detail.test.js`

（这一步是纯读取,确认现有 `describe`/`mock` 的确切写法后再决定新增测试怎么接入现有文件结构——若现有文件已经 mock 了 `OrderAPI`/`CartAPI` 之类同级依赖,新增的 `FavoriteAPI`/`ProductViewAPI` mock 照抄同样写法即可。）

- [ ] **Step 2: 写失败测试**

在现有测试文件里找到 `onToggleFavorite`/`onFavoriteTap`(或 `describe('收藏', ...)` 之类)相关的既有测试块,替换/追加(具体插入位置以 Step 1 读到的实际结构为准,以下是要覆盖的行为,断言写法按现有文件的 mock 命名习惯调整):

（`onLoad`/`fetchProductDetail` 走真实的 `ProductAPI.getById` 请求链——现有测试文件必然已经 mock 了这个方法才能测已有的 `fetchProductDetail` 行为,以下例子里的 `mockGetById` 是占位变量名,替换成 Step 1 读到的现有测试文件里那个 mock 变量的真实名字,不要凭空另起一个名字导致两份 mock 打架。）

```javascript
describe('收藏(收藏 + 浏览足迹接线)', () => {
  it('onLoad 时若已收藏该商品,favorited 初始为 true', async () => {
    mockGetById.mockResolvedValueOnce({ id: 'p1', name: 'x', stock: 5 });
    mockFavoriteList.mockResolvedValueOnce([{ productId: 'p1', productName: 'x', price: 1, imageUrl: '', available: true }]);

    await ctx.onLoad({ id: 'p1' });

    expect(ctx.data.favorited).toBe(true);
  });

  it('onToggleFavorite:未收藏时调用 FavoriteAPI.add,favorited 变 true', async () => {
    ctx.setData({ favorited: false, product: { id: 'p1' } });
    mockFavoriteAdd.mockResolvedValueOnce(['p1']);

    await ctx.onToggleFavorite();

    expect(mockFavoriteAdd).toHaveBeenCalledWith('p1');
    expect(ctx.data.favorited).toBe(true);
  });

  it('onToggleFavorite:已收藏时调用 FavoriteAPI.remove,favorited 变 false', async () => {
    ctx.setData({ favorited: true, product: { id: 'p1' } });
    mockFavoriteRemove.mockResolvedValueOnce([]);

    await ctx.onToggleFavorite();

    expect(mockFavoriteRemove).toHaveBeenCalledWith('p1');
    expect(ctx.data.favorited).toBe(false);
  });

  it('onFavoriteTap 和 onToggleFavorite 驱动同一个真实状态(design.md D5,不再各自独立)', async () => {
    ctx.setData({ favorited: false, product: { id: 'p1' } });
    mockFavoriteAdd.mockResolvedValueOnce(['p1']);

    await ctx.onFavoriteTap();

    expect(mockFavoriteAdd).toHaveBeenCalledWith('p1');
    expect(ctx.data.favorited).toBe(true);
  });

  it('收藏失败时 toast 提示,favorited 状态不变', async () => {
    ctx.setData({ favorited: false, product: { id: 'p1' } });
    mockFavoriteAdd.mockRejectedValueOnce(new Error('network'));

    await ctx.onToggleFavorite();

    expect(ctx.data.favorited).toBe(false);
    expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ icon: 'none' }));
  });
});

describe('浏览足迹静默记录(design.md D6)', () => {
  it('onLoad 成功加载商品后静默调用 ProductViewAPI.record,不 toast', async () => {
    mockGetById.mockResolvedValueOnce({ id: 'p1', name: 'x', stock: 5 });
    mockFavoriteList.mockResolvedValueOnce([]);
    mockRecordView.mockResolvedValueOnce(undefined);

    await ctx.onLoad({ id: 'p1' });

    expect(mockRecordView).toHaveBeenCalledWith('p1');
  });

  it('记录足迹失败不影响页面渲染、不 toast(best-effort)', async () => {
    mockGetById.mockResolvedValueOnce({ id: 'p1', name: 'x', stock: 5 });
    mockFavoriteList.mockResolvedValueOnce([]);
    mockRecordView.mockRejectedValueOnce(new Error('network'));

    await ctx.onLoad({ id: 'p1' });

    expect(ctx.data.isError).toBeFalsy();
    expect(wx.showToast).not.toHaveBeenCalledWith(expect.objectContaining({ title: expect.stringContaining('足迹') }));
  });
});
```

在测试文件顶部(其它 `jest.mock` 旁边)加:

```javascript
const mockFavoriteAdd = jest.fn();
const mockFavoriteRemove = jest.fn();
const mockFavoriteList = jest.fn();
jest.mock('../../../src/features/favorite/api', () => ({
  FavoriteAPI: {
    add: (...a) => mockFavoriteAdd(...a),
    remove: (...a) => mockFavoriteRemove(...a),
    list: (...a) => mockFavoriteList(...a),
  },
}));

const mockRecordView = jest.fn();
jest.mock('../../../src/features/productView/api', () => ({
  ProductViewAPI: {
    record: (...a) => mockRecordView(...a),
    list: jest.fn(),
  },
}));
```

- [ ] **Step 3: 跑测试确认失败**

Run: `cd frontend && npm test -- product-detail`
Expected: 新增测试 FAIL(`onToggleFavorite`/`onFavoriteTap`/`onLoad` 还是旧的纯本地 toggle / 无 record 调用)

- [ ] **Step 4: 修改 `product-detail.js`**

现有 `onLoad`/`fetchProductDetail` 的确切结构(已读取全文确认,注意 `onLoad` 目前不 `return this.fetchProductDetail(...)`,`fetchProductDetail` 也不 `return` 它内部的 promise 链——两处都要补 `return`,否则 Step 1 里 `await ctx.onLoad(...)` 测不到异步结果,这是纯新增 `return`,不改变任何现有调用方行为,真机 mp 生命周期从不 await `onLoad` 返回值,向后兼容):

```javascript
  onLoad: function (options) {
    if (options && options.id) {
      this.fetchProductDetail(options.id);
    }
  },

  fetchProductDetail: function (id) {
    this.setData({ isLoading: true, isError: false });
    ProductAPI.getById(id)
      .then((product) => {
        this.setData({ product, isLoading: false });
        this.fetchRecommendations(product);
      })
      .catch((err) => {
        this.setData({
          isLoading: false,
          isError: true,
          errorMessage: (err && err.message) || '加载商品失败',
        });
        if (!err || err.statusCode !== 401) {
          wx.showToast({ title: '加载商品失败', icon: 'none' });
        }
      });
  },
```

改成:

```javascript
  onLoad: function (options) {
    if (options && options.id) {
      return this.fetchProductDetail(options.id);
    }
  },

  fetchProductDetail: function (id) {
    this.setData({ isLoading: true, isError: false });
    return ProductAPI.getById(id)
      .then((product) => {
        this.setData({ product, isLoading: false });
        this.fetchRecommendations(product);
        // 收藏 + 浏览足迹:静默记一条足迹(design.md D6,best-effort,失败不
        // 影响页面渲染、不 toast——记录浏览足迹不是用户当前任务的关键路径)。
        ProductViewAPI.record(id).catch(() => {});
        // 查当前商品是否已收藏,初始化 favorited(不是本地纯 toggle 的假状态)。
        return FavoriteAPI.list()
          .then((items) => {
            const favorited = (items || []).some((it) => it.productId === id);
            this.setData({ favorited });
          })
          .catch(() => {});
      })
      .catch((err) => {
        this.setData({
          isLoading: false,
          isError: true,
          errorMessage: (err && err.message) || '加载商品失败',
        });
        if (!err || err.statusCode !== 401) {
          wx.showToast({ title: '加载商品失败', icon: 'none' });
        }
      });
  },
```

在文件顶部既有 `require` 语句旁边(`const { recommendationModule } = ...` 之后)加:

```javascript
const { FavoriteAPI } = require('../../../src/features/favorite/api');
const { ProductViewAPI } = require('../../../src/features/productView/api');
```

同时把 `data.favorited` 字段上那句已经过时的注释(`/** 收藏状态(本地,无后端)— 占位 */`)删掉或改成:

```javascript
    /** 收藏状态,来自 FavoriteAPI 真实数据(fetchProductDetail 加载成功后初始化)。 */
    favorited: false,
```

`onToggleFavorite`/`onFavoriteTap` 都改成调同一个真实的私有方法(替换掉原来纯 `setData` toggle 的实现):

```javascript
  onToggleFavorite: function () {
    this._toggleFavorite();
  },

  /**
   * mp-03 悬浮顶栏收藏(brief §1)。收藏 + 浏览足迹改造前是纯装饰 toast、
   * 和底部 onToggleFavorite 刻意解耦("两个独立入口,互不影响")——收藏能力
   * 变真实后继续解耦会是真实的 UX 矛盾(点一个显示"已收藏",点另一个显示
   * "功能开发中"),design.md D5:两个入口统一驱动同一个真实状态。
   */
  onFavoriteTap: function () {
    this._toggleFavorite();
  },

  _toggleFavorite: function () {
    const product = this.data.product;
    if (!product || !product.id) return;
    const wasFavorited = this.data.favorited;
    const call = wasFavorited ? FavoriteAPI.remove(product.id) : FavoriteAPI.add(product.id);
    call
      .then(() => {
        this.setData({ favorited: !wasFavorited });
        wx.showToast({ title: wasFavorited ? '已取消收藏' : '已收藏', icon: 'success' });
      })
      .catch(() => {
        wx.showToast({ title: '操作失败,请重试', icon: 'none' });
      });
  },
```

- [ ] **Step 5: 跑测试确认通过**

Run: `cd frontend && npm test -- product-detail`
Expected: 全绿

- [ ] **Step 6: 修改 `product-detail.wxml`(悬浮顶栏心形绑定 favorited 状态,同底部一致)**

第 44 行(`<text class="detail-topbar__icon">♡</text>`)改成:

```xml
          <text class="detail-topbar__icon">{{favorited ? '♥' : '♡'}}</text>
```

- [ ] **Step 7: 跑 wxml-contract 测试(若已存在)+ 全量前端测试**

Run: `cd frontend && npm test -- product-detail && npm test`
Expected: 全绿,无回归

- [ ] **Step 8: Commit**

```bash
cd frontend
git add pages-sub/product/product-detail/product-detail.js \
        pages-sub/product/product-detail/product-detail.wxml \
        pages-sub/product/product-detail/__tests__/product-detail.test.js
git commit -m "feat(mp): 商品详情页收藏接真后端(两个入口统一状态)+ 静默记浏览足迹"
```

---

## Task 9: `profile.js`/`.wxml` 显示真实收藏/足迹数

**Files:**
- Modify: `frontend/pages/profile/profile.js`
- Modify: `frontend/pages/profile/profile.wxml`
- Modify: `frontend/pages/profile/__tests__/profile.test.js`

**Interfaces:**
- Consumes: `frontend/src/features/user/api.js` 的 `UserAPI.me()`(既有,已在 `api.ts`/`api.js` 里,响应体现在含 `favoriteCount`/`viewCount`,Task 5 已在后端补上这两个字段)

- [ ] **Step 1: 写失败测试**

在 `frontend/pages/profile/__tests__/profile.test.js` 顶部现有 `jest.mock('../../../src/features/auth/store', ...)` 之后加:

```javascript
const mockUserApiMe = jest.fn();
jest.mock('../../../src/features/user/api', () => ({
  UserAPI: { me: (...a) => mockUserApiMe(...a) },
}));
```

在 `describe('onShow / refreshUserInfo', ...)` 块内追加(其它既有测试不变):

```javascript
    it('已登录时额外拉 UserAPI.me() 刷新 favoriteCount/viewCount', async () => {
      const user = { nickname: '林一帆', avatarUrl: 'https://x/a.png', role: 'CUSTOMER' };
      mockGetState.mockReturnValue({ user, isAuthenticated: true });
      mockUserApiMe.mockResolvedValueOnce({ ...user, favoriteCount: 12, viewCount: 38 });

      ctx.onShow();
      await Promise.resolve();
      await Promise.resolve();

      expect(ctx.data.favoriteCount).toBe(12);
      expect(ctx.data.viewCount).toBe(38);
    });

    it('未登录时不调用 UserAPI.me()', () => {
      ctx.onShow();
      expect(mockUserApiMe).not.toHaveBeenCalled();
    });

    it('UserAPI.me() 失败时静默降级,不 toast、不影响页面其它渲染', async () => {
      mockGetState.mockReturnValue({ user: { nickname: 'x' }, isAuthenticated: true });
      mockUserApiMe.mockRejectedValueOnce(new Error('network'));

      ctx.onShow();
      await Promise.resolve();
      await Promise.resolve();

      expect(ctx.data.favoriteCount).toBe(0);
      expect(ctx.data.viewCount).toBe(0);
      expect(wx.showToast).not.toHaveBeenCalled();
    });
```

追加两个新 `describe`(导航到新列表页):

```javascript
  describe('onGoFavorites / onGoFootprints', () => {
    it('未登录时提示先登录,不跳转', () => {
      ctx.onGoFavorites();
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '请先登录' }));
      expect(wx.navigateTo).not.toHaveBeenCalled();
    });

    it('已登录时跳收藏列表页', () => {
      mockGetState.mockReturnValue({ user: { nickname: 'x' }, isAuthenticated: true });
      ctx.onGoFavorites();
      expect(wx.navigateTo).toHaveBeenCalledWith(
        expect.objectContaining({ url: '/pages-sub/user/favorites/favorites-list' }),
      );
    });

    it('已登录时跳足迹列表页', () => {
      mockGetState.mockReturnValue({ user: { nickname: 'x' }, isAuthenticated: true });
      ctx.onGoFootprints();
      expect(wx.navigateTo).toHaveBeenCalledWith(
        expect.objectContaining({ url: '/pages-sub/user/footprints/footprints-list' }),
      );
    });
  });
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && npm test -- profile`
Expected: 新增测试 FAIL(`onGoFavorites`/`onGoFootprints` 不存在,`favoriteCount`/`viewCount` 不在 data 里)

- [ ] **Step 3: 修改 `profile.js`**

完整替换 `frontend/pages/profile/profile.js`:

```javascript
/**
 * Profile page — uses the new `features/auth` store for logout
 * (OpenSpec §8.4)。收藏 + 浏览足迹:onShow 额外拉 UserAPI.me() 刷新
 * favoriteCount/viewCount(authStore 缓存的 user 只在登录时更新一次,收藏/
 * 取消收藏后返回本页不会自动变新,需要真实网络请求刷新)。
 */
const { authStore } = require('../../src/features/auth/store');
const { UserAPI } = require('../../src/features/user/api');

Page({
  data: {
    userInfo: null,
    favoriteCount: 0,
    viewCount: 0,
  },

  onShow: function () {
    this.refreshUserInfo();
  },

  refreshUserInfo: function () {
    const state = authStore.getState();
    this.setData({ userInfo: state.user });
    if (!state.isAuthenticated) return;
    UserAPI.me()
      .then((u) => {
        this.setData({ favoriteCount: (u && u.favoriteCount) || 0, viewCount: (u && u.viewCount) || 0 });
      })
      .catch(() => {
        // best-effort:附加请求失败不阻断页面其它渲染,静默降级为 0
      });
  },

  goToOrderList: function () {
    if (!authStore.getState().isAuthenticated) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    wx.navigateTo({ url: '/pages-sub/order/order-list/order-list' });
  },

  onGoFavorites: function () {
    if (!authStore.getState().isAuthenticated) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    wx.navigateTo({ url: '/pages-sub/user/favorites/favorites-list' });
  },

  onGoFootprints: function () {
    if (!authStore.getState().isAuthenticated) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    wx.navigateTo({ url: '/pages-sub/user/footprints/footprints-list' });
  },

  onLogin: function () {
    if (authStore.getState().isAuthenticated) {
      wx.showToast({ title: '您已登录', icon: 'none' });
      return;
    }
    wx.navigateTo({ url: '/pages-sub/user/login/login' });
  },

  onContactService: function () {
    wx.showToast({ title: '联系客服开发中', icon: 'none' });
  },

  onAboutUs: function () {
    wx.showToast({ title: '关于我们开发中', icon: 'none' });
  },

  onLogout: function () {
    if (!authStore.getState().isAuthenticated) {
      wx.showToast({ title: '您尚未登录', icon: 'none' });
      return;
    }
    wx.showModal({
      title: '确认登出',
      content: '确定要退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          authStore
            .logout()
            .then(() => {
              this.refreshUserInfo();
              wx.showToast({ title: '已退出登录', icon: 'success' });
            })
            .catch(() => {
              wx.showToast({ title: '登出失败', icon: 'none' });
            });
        }
      },
    });
  },
});
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd frontend && npm test -- profile`
Expected: 全绿

- [ ] **Step 5: 修改 `profile.wxml`(用户卡下方加收藏/足迹数字行)**

在 `</view>`(user-card 结束,第 17 行)之后、`<!-- 4 状态卡 -->`(第 19 行)之前插入:

```xml
  <!-- 收藏 / 足迹 数字行(收藏 + 浏览足迹,OD 原型仪表盘惯例;优惠券数字位暂不接入,遗留问题清单已记) -->
  <view class="stats-row">
    <view class="stats-row__item" bindtap="onGoFavorites" hover-class="is-clicked" hover-stay-time="100">
      <text class="stats-row__num">{{favoriteCount}}</text>
      <text class="stats-row__label">收藏</text>
    </view>
    <view class="stats-row__item" bindtap="onGoFootprints" hover-class="is-clicked" hover-stay-time="100">
      <text class="stats-row__num">{{viewCount}}</text>
      <text class="stats-row__label">足迹</text>
    </view>
  </view>
```

在 `frontend/pages/profile/profile.wxss` 末尾追加:

```css
/* 收藏 / 足迹 数字行(收藏 + 浏览足迹) */
.stats-row {
  display: flex;
  background: var(--surface, #fff);
  border-radius: var(--radius-xl, 14px);
  margin: 0 24rpx 20rpx;
  box-shadow: 0 2rpx 10rpx var(--shadow-sm, rgba(35, 24, 20, 0.05));
}

.stats-row__item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24rpx 0;
}

.stats-row__num {
  font-family: var(--font-display, serif);
  font-size: 36rpx;
  font-weight: 600;
  color: var(--fg, #231814);
}

.stats-row__label {
  font-size: 24rpx;
  color: var(--muted, #8a7a70);
  margin-top: 4rpx;
}
```

- [ ] **Step 6: 跑全量前端测试**

Run: `cd frontend && npm test`
Expected: 全绿,无回归

- [ ] **Step 7: Commit**

```bash
cd frontend
git add pages/profile/profile.js pages/profile/profile.wxml pages/profile/profile.wxss \
        pages/profile/__tests__/profile.test.js
git commit -m "feat(mp): 我的页显示真实收藏/足迹数,可点击跳转"
```

---

## Task 10: 新增收藏网格列表页

**Files:**
- Create: `frontend/pages-sub/user/favorites/favorites-list.js`
- Create: `frontend/pages-sub/user/favorites/favorites-list.wxml`
- Create: `frontend/pages-sub/user/favorites/favorites-list.wxss`
- Create: `frontend/pages-sub/user/favorites/favorites-list.json`
- Create: `frontend/pages-sub/user/favorites/__tests__/favorites-list.test.js`
- Create: `frontend/pages-sub/user/favorites/__tests__/favorites-list-wxml-contract.test.js`

**Interfaces:**
- Consumes: `FavoriteAPI.list()`/`FavoriteAPI.remove(productId)`(Task 6)

- [ ] **Step 1: 写失败测试(page + wxml-contract)**

Create `frontend/pages-sub/user/favorites/__tests__/favorites-list.test.js`:

```javascript
global.wx = {
  showToast: jest.fn(),
  navigateTo: jest.fn(),
  stopPullDownRefresh: jest.fn(),
};

const mockFavoriteList = jest.fn();
const mockFavoriteRemove = jest.fn();
jest.mock('../../../../src/features/favorite/api', () => ({
  FavoriteAPI: {
    list: (...a) => mockFavoriteList(...a),
    remove: (...a) => mockFavoriteRemove(...a),
    add: jest.fn(),
  },
}));

let pageConfig;
global.Page = (config) => {
  pageConfig = config;
};
require('../favorites-list.js');

describe('favorites-list', () => {
  let ctx;

  beforeEach(() => {
    jest.clearAllMocks();
    ctx = {
      data: JSON.parse(JSON.stringify(pageConfig.data)),
      setData: jest.fn(function (patch) {
        Object.assign(this.data, patch);
      }),
    };
    ctx.setData = ctx.setData.bind(ctx);
    for (const key of Object.keys(pageConfig)) {
      if (typeof pageConfig[key] === 'function') ctx[key] = pageConfig[key].bind(ctx);
    }
  });

  describe('onLoad / loadFavorites', () => {
    it('成功拉取收藏列表,写入 data.items', async () => {
      const items = [{ productId: 'p1', productName: '三文鱼', price: 58, imageUrl: 'http://img', available: true }];
      mockFavoriteList.mockResolvedValueOnce(items);

      await ctx.onLoad();

      expect(ctx.data.items).toEqual(items);
      expect(ctx.data.isEmpty).toBe(false);
    });

    it('空列表时 isEmpty 为 true', async () => {
      mockFavoriteList.mockResolvedValueOnce([]);

      await ctx.onLoad();

      expect(ctx.data.isEmpty).toBe(true);
    });

    it('拉取失败时 toast 提示', async () => {
      mockFavoriteList.mockRejectedValueOnce(new Error('network'));

      await ctx.onLoad();

      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ icon: 'none' }));
    });
  });

  describe('onRemoveFavorite', () => {
    it('调用 FavoriteAPI.remove 后重新拉取列表', async () => {
      mockFavoriteList.mockResolvedValueOnce([]);
      mockFavoriteRemove.mockResolvedValueOnce([]);
      const e = { currentTarget: { dataset: { id: 'p1' } } };

      await ctx.onRemoveFavorite(e);

      expect(mockFavoriteRemove).toHaveBeenCalledWith('p1');
      expect(mockFavoriteList).toHaveBeenCalled();
    });
  });

  describe('onItemTap', () => {
    it('可用商品:跳转商品详情页', () => {
      const e = { currentTarget: { dataset: { id: 'p1', available: true } } };
      ctx.onItemTap(e);
      expect(wx.navigateTo).toHaveBeenCalledWith(
        expect.objectContaining({ url: '/pages-sub/product/product-detail/product-detail?id=p1' }),
      );
    });

    it('已下架商品:不跳转,toast 提示', () => {
      const e = { currentTarget: { dataset: { id: 'p-gone', available: false } } };
      ctx.onItemTap(e);
      expect(wx.navigateTo).not.toHaveBeenCalled();
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ icon: 'none' }));
    });
  });

  describe('onPullDownRefresh', () => {
    it('重新拉取后停止下拉动画', async () => {
      mockFavoriteList.mockResolvedValueOnce([]);
      await ctx.onPullDownRefresh();
      expect(wx.stopPullDownRefresh).toHaveBeenCalled();
    });
  });
});
```

Create `frontend/pages-sub/user/favorites/__tests__/favorites-list-wxml-contract.test.js`:

```javascript
/**
 * favorites-list.wxml ↔ favorites-list.js 契约测试(同 address-list-wxml-contract.test.js
 * 惯例)——扫 wxml 里所有 bindtap/catchtap,断言目标方法在真实 Page(config) 上存在。
 */
const fs = require('fs');
const path = require('path');

global.wx = { showToast: jest.fn(), navigateTo: jest.fn(), stopPullDownRefresh: jest.fn() };
jest.mock('../../../../src/features/favorite/api', () => ({
  FavoriteAPI: { list: jest.fn().mockResolvedValue([]), remove: jest.fn(), add: jest.fn() },
}));

let pageConfig;
global.Page = (config) => {
  pageConfig = config;
};
require('../favorites-list.js');

describe('favorites-list.wxml ↔ .js bindtap 契约', () => {
  it('每个 (bind|catch)tap 目标在 Page config 上都是真实存在的函数', () => {
    const wxml = fs.readFileSync(
      path.join(__dirname, '../favorites-list.wxml'), 'utf8',
    );
    const matches = [...wxml.matchAll(/(?:bind|catch)tap="([^"]+)"/g)].map((m) => m[1]);
    expect(matches.length).toBeGreaterThan(0);
    for (const name of matches) {
      expect(typeof pageConfig[name]).toBe('function');
    }
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && npm test -- favorites-list`
Expected: 失败(`favorites-list.js` 不存在)

- [ ] **Step 3: 实现 `favorites-list.js`**

Create `frontend/pages-sub/user/favorites/favorites-list.js`:

```javascript
/**
 * 收藏网格列表页(收藏 + 浏览足迹)。OD 原型仪表盘只显数字,这里是"点数字进来"
 * 的完整列表——网格布局对齐 pages/index 首页商品 2 列 grid 惯例。
 */
const { FavoriteAPI } = require('../../../src/features/favorite/api');

Page({
  data: {
    items: [],
    isLoading: false,
    isEmpty: false,
  },

  onLoad: function () {
    return this.loadFavorites();
  },

  onPullDownRefresh: function () {
    return this.loadFavorites().finally(() => wx.stopPullDownRefresh());
  },

  loadFavorites: function () {
    this.setData({ isLoading: true });
    return FavoriteAPI.list()
      .then((items) => {
        this.setData({ items: items || [], isEmpty: !items || items.length === 0 });
      })
      .catch(() => {
        wx.showToast({ title: '加载收藏失败', icon: 'none' });
      })
      .finally(() => {
        this.setData({ isLoading: false });
      });
  },

  onRemoveFavorite: function (e) {
    const productId = e.currentTarget.dataset.id;
    FavoriteAPI.remove(productId)
      .then(() => this.loadFavorites())
      .catch(() => {
        wx.showToast({ title: '取消收藏失败', icon: 'none' });
      });
  },

  onItemTap: function (e) {
    const { id, available } = e.currentTarget.dataset;
    if (!available) {
      wx.showToast({ title: '该商品已下架', icon: 'none' });
      return;
    }
    wx.navigateTo({ url: `/pages-sub/product/product-detail/product-detail?id=${id}` });
  },

  onBack: function () {
    wx.navigateBack();
  },
});
```

- [ ] **Step 4: 实现 `favorites-list.wxml`**

Create `frontend/pages-sub/user/favorites/favorites-list.wxml`:

```xml
<view class="favorites-container">
  <view class="favorites-topbar safe-area-top">
    <view class="favorites-topbar__back" bindtap="onBack" hover-class="is-clicked" hover-stay-time="100">
      <text class="favorites-topbar__back-icon">‹</text>
    </view>
    <text class="favorites-topbar__title">我的收藏</text>
    <view class="favorites-topbar__placeholder"></view>
  </view>

  <view class="favorites-grid" wx:if="{{items.length > 0}}">
    <view class="favorites-grid__cell" wx:for="{{items}}" wx:key="productId">
      <view
        class="favorites-grid__nav"
        bindtap="onItemTap"
        data-id="{{item.productId}}"
        data-available="{{item.available}}"
      >
        <image class="favorites-grid__img" src="{{item.imageUrl}}" mode="aspectFill"></image>
        <view class="favorites-grid__body">
          <text class="favorites-grid__name">{{item.productName}}</text>
          <text class="favorites-grid__price" wx:if="{{item.available}}">¥ {{item.price}}</text>
          <text class="favorites-grid__unavailable" wx:else>已下架</text>
        </view>
      </view>
      <view
        class="favorites-grid__remove"
        bindtap="onRemoveFavorite"
        data-id="{{item.productId}}"
        hover-class="is-clicked"
        hover-stay-time="100"
      >
        <text>♥</text>
      </view>
    </view>
  </view>

  <view class="empty-state" wx:if="{{items.length === 0 && !isLoading}}">
    <text class="empty-state__icon">🤍</text>
    <text class="empty-state__title">还没有收藏商品哦</text>
    <text class="empty-state__sub">看到喜欢的商品点个心形收藏吧</text>
  </view>
</view>
```

- [ ] **Step 5: 实现 `favorites-list.wxss`**

Create `frontend/pages-sub/user/favorites/favorites-list.wxss`:

```css
.favorites-container {
  min-height: 100vh;
  background: var(--bg, #fffbf8);
}

.favorites-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 24rpx;
  background: var(--surface, #fff);
}

.favorites-topbar__back {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.favorites-topbar__back-icon {
  font-size: 44rpx;
  color: var(--fg, #231814);
}

.favorites-topbar__title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--fg, #231814);
}

.favorites-topbar__placeholder {
  width: 64rpx;
}

/* 2 列 grid(同 pages/index 首页 home-grid 惯例:WeChat mp display:grid 不生效,
   用 flex wrap 退化实现 2 列瀑布)。 */
.favorites-grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  padding: 16rpx;
}

.favorites-grid__cell {
  position: relative;
  width: calc(50% - 8rpx);
  margin-bottom: 16rpx;
  background: var(--surface, #fff);
  border-radius: var(--radius-xl, 14px);
  overflow: hidden;
  box-shadow: 0 2rpx 10rpx var(--shadow-sm, rgba(35, 24, 20, 0.05));
}

.favorites-grid__nav {
  display: flex;
  flex-direction: column;
}

.favorites-grid__img {
  width: 100%;
  height: 320rpx;
  background: var(--bg, #fffbf8);
}

.favorites-grid__body {
  padding: 16rpx 20rpx 20rpx;
}

.favorites-grid__name {
  font-size: 28rpx;
  color: var(--fg, #231814);
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.favorites-grid__price {
  font-family: var(--font-display, serif);
  font-size: 32rpx;
  font-weight: 600;
  color: var(--accent, #db633c);
  margin-top: 8rpx;
  display: block;
}

.favorites-grid__unavailable {
  font-size: 24rpx;
  color: var(--muted, #8a7a70);
  margin-top: 8rpx;
  display: block;
}

.favorites-grid__remove {
  position: absolute;
  top: 12rpx;
  right: 12rpx;
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent, #db633c);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120rpx 0;
}

.empty-state__icon {
  font-size: 80rpx;
}

.empty-state__title {
  font-size: 30rpx;
  color: var(--fg, #231814);
  margin-top: 24rpx;
}

.empty-state__sub {
  font-size: 26rpx;
  color: var(--muted, #8a7a70);
  margin-top: 8rpx;
}
```

- [ ] **Step 6: 实现 `favorites-list.json`**

Create `frontend/pages-sub/user/favorites/favorites-list.json`:

```json
{
  "navigationBarTitleText": "我的收藏",
  "navigationStyle": "custom",
  "enablePullDownRefresh": true
}
```

- [ ] **Step 7: 跑测试确认通过**

Run: `cd frontend && npm test -- favorites-list`
Expected: 全绿(page 测试 + wxml-contract 测试)

- [ ] **Step 8: 注册页面(app.json)**

在 `frontend/app.json` 的 `"root": "pages-sub/user"` 块的 `"pages"` 数组里加一项:

```json
    {
      "root": "pages-sub/user",
      "pages": [
        "login/login",
        "address/address-list",
        "address/address-edit",
        "favorites/favorites-list"
      ]
    }
```

- [ ] **Step 9: 跑全量前端测试**

Run: `cd frontend && npm test`
Expected: 全绿,无回归

- [ ] **Step 10: Commit**

```bash
cd frontend
git add pages-sub/user/favorites/ app.json
git commit -m "feat(mp): 新增收藏网格列表页"
```

---

## Task 11: 新增足迹列表页

**Files:**
- Create: `frontend/pages-sub/user/footprints/footprints-list.js`
- Create: `frontend/pages-sub/user/footprints/footprints-list.wxml`
- Create: `frontend/pages-sub/user/footprints/footprints-list.wxss`
- Create: `frontend/pages-sub/user/footprints/footprints-list.json`
- Create: `frontend/pages-sub/user/footprints/__tests__/footprints-list.test.js`
- Create: `frontend/pages-sub/user/footprints/__tests__/footprints-list-wxml-contract.test.js`

**Interfaces:**
- Consumes: `ProductViewAPI.list()`(Task 7)

- [ ] **Step 1: 写失败测试**

Create `frontend/pages-sub/user/footprints/__tests__/footprints-list.test.js`:

```javascript
global.wx = {
  showToast: jest.fn(),
  navigateTo: jest.fn(),
  stopPullDownRefresh: jest.fn(),
};

const mockProductViewList = jest.fn();
jest.mock('../../../../src/features/productView/api', () => ({
  ProductViewAPI: { list: (...a) => mockProductViewList(...a), record: jest.fn() },
}));

let pageConfig;
global.Page = (config) => {
  pageConfig = config;
};
require('../footprints-list.js');

describe('footprints-list', () => {
  let ctx;

  beforeEach(() => {
    jest.clearAllMocks();
    ctx = {
      data: JSON.parse(JSON.stringify(pageConfig.data)),
      setData: jest.fn(function (patch) {
        Object.assign(this.data, patch);
      }),
    };
    ctx.setData = ctx.setData.bind(ctx);
    for (const key of Object.keys(pageConfig)) {
      if (typeof pageConfig[key] === 'function') ctx[key] = pageConfig[key].bind(ctx);
    }
  });

  describe('onLoad / loadFootprints', () => {
    it('成功拉取足迹列表,写入 data.items(后端已按 viewedAt 降序返回,不再本地重排)', async () => {
      const items = [{ productId: 'p1', productName: '龙虾', price: 128, imageUrl: 'http://img', available: true, viewedAt: '2026-07-06T00:00:00Z' }];
      mockProductViewList.mockResolvedValueOnce(items);

      await ctx.onLoad();

      expect(ctx.data.items).toEqual(items);
      expect(ctx.data.isEmpty).toBe(false);
    });

    it('空列表时 isEmpty 为 true', async () => {
      mockProductViewList.mockResolvedValueOnce([]);

      await ctx.onLoad();

      expect(ctx.data.isEmpty).toBe(true);
    });

    it('拉取失败时 toast 提示', async () => {
      mockProductViewList.mockRejectedValueOnce(new Error('network'));

      await ctx.onLoad();

      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ icon: 'none' }));
    });
  });

  describe('onItemTap', () => {
    it('可用商品:跳转商品详情页', () => {
      const e = { currentTarget: { dataset: { id: 'p1', available: true } } };
      ctx.onItemTap(e);
      expect(wx.navigateTo).toHaveBeenCalledWith(
        expect.objectContaining({ url: '/pages-sub/product/product-detail/product-detail?id=p1' }),
      );
    });

    it('已下架商品:不跳转,toast 提示', () => {
      const e = { currentTarget: { dataset: { id: 'p-gone', available: false } } };
      ctx.onItemTap(e);
      expect(wx.navigateTo).not.toHaveBeenCalled();
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ icon: 'none' }));
    });
  });

  describe('onPullDownRefresh', () => {
    it('重新拉取后停止下拉动画', async () => {
      mockProductViewList.mockResolvedValueOnce([]);
      await ctx.onPullDownRefresh();
      expect(wx.stopPullDownRefresh).toHaveBeenCalled();
    });
  });
});
```

Create `frontend/pages-sub/user/footprints/__tests__/footprints-list-wxml-contract.test.js`:

```javascript
const fs = require('fs');
const path = require('path');

global.wx = { showToast: jest.fn(), navigateTo: jest.fn(), stopPullDownRefresh: jest.fn() };
jest.mock('../../../../src/features/productView/api', () => ({
  ProductViewAPI: { list: jest.fn().mockResolvedValue([]), record: jest.fn() },
}));

let pageConfig;
global.Page = (config) => {
  pageConfig = config;
};
require('../footprints-list.js');

describe('footprints-list.wxml ↔ .js bindtap 契约', () => {
  it('每个 (bind|catch)tap 目标在 Page config 上都是真实存在的函数', () => {
    const wxml = fs.readFileSync(
      path.join(__dirname, '../footprints-list.wxml'), 'utf8',
    );
    const matches = [...wxml.matchAll(/(?:bind|catch)tap="([^"]+)"/g)].map((m) => m[1]);
    expect(matches.length).toBeGreaterThan(0);
    for (const name of matches) {
      expect(typeof pageConfig[name]).toBe('function');
    }
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && npm test -- footprints-list`
Expected: 失败(`footprints-list.js` 不存在)

- [ ] **Step 3: 实现 `footprints-list.js`**

Create `frontend/pages-sub/user/footprints/footprints-list.js`:

```javascript
/**
 * 浏览足迹列表页(收藏 + 浏览足迹)。纯浏览记录,无操作按钮(design.md:
 * 只看,不可编辑)——列表布局,后端已按 viewedAt 降序返回,不再本地重排。
 */
const { ProductViewAPI } = require('../../../src/features/productView/api');

Page({
  data: {
    items: [],
    isLoading: false,
    isEmpty: false,
  },

  onLoad: function () {
    return this.loadFootprints();
  },

  onPullDownRefresh: function () {
    return this.loadFootprints().finally(() => wx.stopPullDownRefresh());
  },

  loadFootprints: function () {
    this.setData({ isLoading: true });
    return ProductViewAPI.list()
      .then((items) => {
        this.setData({ items: items || [], isEmpty: !items || items.length === 0 });
      })
      .catch(() => {
        wx.showToast({ title: '加载足迹失败', icon: 'none' });
      })
      .finally(() => {
        this.setData({ isLoading: false });
      });
  },

  onItemTap: function (e) {
    const { id, available } = e.currentTarget.dataset;
    if (!available) {
      wx.showToast({ title: '该商品已下架', icon: 'none' });
      return;
    }
    wx.navigateTo({ url: `/pages-sub/product/product-detail/product-detail?id=${id}` });
  },

  onBack: function () {
    wx.navigateBack();
  },
});
```

- [ ] **Step 4: 实现 `footprints-list.wxml`**

Create `frontend/pages-sub/user/footprints/footprints-list.wxml`:

```xml
<view class="footprints-container">
  <view class="footprints-topbar safe-area-top">
    <view class="footprints-topbar__back" bindtap="onBack" hover-class="is-clicked" hover-stay-time="100">
      <text class="footprints-topbar__back-icon">‹</text>
    </view>
    <text class="footprints-topbar__title">浏览足迹</text>
    <view class="footprints-topbar__placeholder"></view>
  </view>

  <view class="footprints-list" wx:if="{{items.length > 0}}">
    <view
      class="footprints-item"
      wx:for="{{items}}"
      wx:key="productId"
      bindtap="onItemTap"
      data-id="{{item.productId}}"
      data-available="{{item.available}}"
      hover-class="is-clicked"
      hover-stay-time="100"
    >
      <image class="footprints-item__img" src="{{item.imageUrl}}" mode="aspectFill"></image>
      <view class="footprints-item__body">
        <text class="footprints-item__name">{{item.productName}}</text>
        <text class="footprints-item__price" wx:if="{{item.available}}">¥ {{item.price}}</text>
        <text class="footprints-item__unavailable" wx:else>已下架</text>
      </view>
    </view>
  </view>

  <view class="empty-state" wx:if="{{items.length === 0 && !isLoading}}">
    <text class="empty-state__icon">👣</text>
    <text class="empty-state__title">还没有浏览记录哦</text>
    <text class="empty-state__sub">去逛逛感兴趣的商品吧</text>
  </view>
</view>
```

- [ ] **Step 5: 实现 `footprints-list.wxss`**

Create `frontend/pages-sub/user/footprints/footprints-list.wxss`:

```css
.footprints-container {
  min-height: 100vh;
  background: var(--bg, #fffbf8);
}

.footprints-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 24rpx;
  background: var(--surface, #fff);
}

.footprints-topbar__back {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.footprints-topbar__back-icon {
  font-size: 44rpx;
  color: var(--fg, #231814);
}

.footprints-topbar__title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--fg, #231814);
}

.footprints-topbar__placeholder {
  width: 64rpx;
}

.footprints-list {
  padding: 16rpx 24rpx;
}

.footprints-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 16rpx;
  margin-bottom: 12rpx;
  background: var(--surface, #fff);
  border-radius: var(--radius-lg, 12px);
  box-shadow: 0 2rpx 10rpx var(--shadow-sm, rgba(35, 24, 20, 0.05));
}

.footprints-item__img {
  width: 120rpx;
  height: 120rpx;
  border-radius: var(--radius-md, 8px);
  background: var(--bg, #fffbf8);
  flex-shrink: 0;
}

.footprints-item__body {
  flex: 1;
  min-width: 0;
}

.footprints-item__name {
  font-size: 28rpx;
  color: var(--fg, #231814);
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.footprints-item__price {
  font-family: var(--font-display, serif);
  font-size: 30rpx;
  font-weight: 600;
  color: var(--accent, #db633c);
  margin-top: 8rpx;
  display: block;
}

.footprints-item__unavailable {
  font-size: 24rpx;
  color: var(--muted, #8a7a70);
  margin-top: 8rpx;
  display: block;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120rpx 0;
}

.empty-state__icon {
  font-size: 80rpx;
}

.empty-state__title {
  font-size: 30rpx;
  color: var(--fg, #231814);
  margin-top: 24rpx;
}

.empty-state__sub {
  font-size: 26rpx;
  color: var(--muted, #8a7a70);
  margin-top: 8rpx;
}
```

- [ ] **Step 6: 实现 `footprints-list.json`**

Create `frontend/pages-sub/user/footprints/footprints-list.json`:

```json
{
  "navigationBarTitleText": "浏览足迹",
  "navigationStyle": "custom",
  "enablePullDownRefresh": true
}
```

- [ ] **Step 7: 跑测试确认通过**

Run: `cd frontend && npm test -- footprints-list`
Expected: 全绿

- [ ] **Step 8: 注册页面(app.json)**

在 Task 10 Step 8 已经改过的同一个 `"pages-sub/user"` 块的 `"pages"` 数组里再加一项:

```json
    {
      "root": "pages-sub/user",
      "pages": [
        "login/login",
        "address/address-list",
        "address/address-edit",
        "favorites/favorites-list",
        "footprints/footprints-list"
      ]
    }
```

- [ ] **Step 9: Commit**

```bash
cd frontend
git add pages-sub/user/footprints/ app.json
git commit -m "feat(mp): 新增浏览足迹列表页"
```

---

## Task 12: 全量验证 + 收尾

**Files:** 无新文件,纯验证

- [ ] **Step 1: 后端全量验证**

Run: `cd backend && ./gradlew check -PexcludeTags=docker`
Expected: BUILD SUCCESSFUL(含 ArchUnit + jacoco 覆盖率校验 + `checkNoRefreshScope`)

- [ ] **Step 2: 前端全量验证**

Run: `cd frontend && npm test`
Expected: 全绿,无回归(套件数/用例数应比 Task 1 开始前多——12 个 Task 各自新增的测试文件都应计入)

- [ ] **Step 3: 若本机 Docker 可用,补跑 native/IT 相关切片(可选,若 CLAUDE.md 描述的 GraalVM 环境不具备可跳过并如实说明)**

Run: `cd backend && ./gradlew test --tests "*ProductViewRepositorySliceTest*"`
Expected: 若 Task 2 Step 2 因无 Docker 跳过了,这里补跑一次确认

- [ ] **Step 4: Commit(若有遗漏的收尾改动)**

```bash
cd backend && cd ../frontend
git status
# 若有任何未提交的收尾性小改动(如 lint 修复),单独 commit
```
