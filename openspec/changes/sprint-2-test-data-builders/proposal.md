# Sprint 2 / D1 — Test Data Builders Proposal

> 子 change:`test-suite-roadmap` §3.2 Sprint 2 子项目 ①
> 父 change:`openspec/changes/test-suite-roadmap/design.md`

## Why

Sprint 1 closure 后 backend 77 测试覆盖 domain/application/infra,但**测试 fixture 散落在各测试类的私有 `sample()` 方法里**(`OrderTest.sample()` / `CartTest.anEmptyCart()` 等),没有任何共享工厂。后果:

- 跨测试类复制 setup 代码(Sprint 2 加 controller slice test 时复制量翻倍)
- 改 `Order` record 加字段时,所有 `sample()` 编译失败散落(`estimatedDelivery` 加字段时 6+ 文件同时编译错)
- 11-arg `new Order(...)` 调用读不到测试意图("an order for user u1 with paid status" → 5 行才能看明白)

**为什么现在做**:Sprint 3 即将做 backend coverage backfill(6+ controller slice + 4+ repo slice + 5+ BFF integration = 15+ 新测试),每个测试都要造 fixture。不开 D1,Sprint 3 后 fixture 散落 +15 处,后续维护成本指数增长。父路线图 §3.3 明确 "D1 排第一:不开这条路,A/B/C1 都会写重复 fixture"。

## What Changes

### New Capabilities

- **backend-test-fixtures**: 提供 5 个 test data builder(`OrderBuilder` / `ProductBuilder` / `UserBuilder` / `CartBuilder` / `RefundBuilder`),覆盖 backend 全 5 个聚合根。每个 builder 暴露 `anXxx()` 静态工厂 + `withXxx()` 链式修改 + `build()` 终态构造。零运行时依赖,无 Spring / Lombok。

### Modified Capabilities

无 — 本 change 不改任何已发布 API,纯测试基础设施。

## Capabilities

### New Capabilities

- `backend-test-fixtures`: 5 个 builder + OrderTest sample() 改写示范,后续测试默认使用 builder 而非 inline `new Xxx(...)`

### Modified Capabilities

(无)

## Impact

### 受影响代码

- **新文件**(`backend/src/test/java/com/seafood/testsupport/builders/`):
  - `OrderBuilder.java` + `OrderBuilderTest.java`
  - `ProductBuilder.java` + `ProductBuilderTest.java`
  - `UserBuilder.java` + `UserBuilderTest.java`
  - `CartBuilder.java` + `CartBuilderTest.java`
  - `RefundBuilder.java` + `RefundBuilderTest.java`
- **改文件**:`backend/src/test/java/com/seafood/order/domain/OrderTest.java`(sample() 改用 OrderBuilder,8 个测试 fixture 写法升级,行为不变)
- **不改文件**:任何 main src 文件(builder 是 test-only,不进运行时)

### 不影响

- API surface(零 controller / service 改动)
- main src 任何文件(builder 不进运行时)
- 生产配置(Spring / Mongo / JWT 等不动)

### 风险

- 低:本 change 纯 test fixture 改造,不碰生产代码
- 5 builder 共 ~300 行新代码,OrderTest 改 1 处 sample(),commit 切 6 个小 commit 易回滚

### 验收

- 5 builder test 全 GREEN(20 cases)
- OrderTest 改写后 15 cases 全 GREEN(行为零变化)
- 全 backend `./gradlew test` 仍 100% pass(无 regression)
- 后续 Sprint 3 A 后端 coverage backfill 测试代码引用 5 builder,不写 inline `new Xxx(...)`
