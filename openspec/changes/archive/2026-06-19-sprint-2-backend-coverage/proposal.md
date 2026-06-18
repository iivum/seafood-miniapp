## Why

Sprint 1 closure 后 backend 已有 77 测试,但 controller slice 只 2/8、repository slice = 0、BFF integration 只 1,改 controller / repository 时无 safety net,refactor 风险高。父路线图(`test-suite-roadmap/design.md` §3.2)明确 Sprint 2 第二个 sub-change 启动 A(后端覆盖率回填),D1(test data builders)已先期完成,本 change 是它的直接消费者。

## What Changes

- 新增 15 个测试类,共 ~30+ 测试 case,只动 `backend/src/test/`,不动 main 源码:
  - 6 个 controller slice test(覆盖 `ProductController` / `OrderController` / `CartController` + 3 个 admin controller)
  - 4 个 repository slice test(`OrderRepository` / `ProductRepository` / `UserRepository` / `RefundRepository`)
  - 5 个 BFF integration test(`/api/admin/dashboard` / `/api/admin/orders/{id}/detail` / `/api/admin/orders` / `/api/admin/products/{id}/duplicate` / `/api/admin/orders/batch-ship`)
- 所有 fixture 使用 D1 builders(`OrderBuilder` / `ProductBuilder` / `UserBuilder` / `CartBuilder` / `RefundBuilder`)
- 全部 test 通过后 `./gradlew check` PASS、Jacoco global ≥80%、零回归
- 可能向 `backend/build.gradle` 加 1 个 test 依赖(`spring-boot-test-autoconfigure` 或 `spring-test` 显式版本),前提是 Spring Boot 4.0.6 starter-test 未 bundle `@MockitoBean` / `MockMvcTester`(由 Task 1 验证)

## Capabilities

### New Capabilities
- `backend-test-coverage`: 后端 controller / repository / BFF 三类切片测试覆盖补齐,Jacoco global ≥80%,作为后续 refactor 与新功能开发的 safety net

### Modified Capabilities
无(纯新增 test,不动既有 spec 行为)

## Impact

- **代码**: 仅 `backend/src/test/java/com/seafood/` 下新增 15 个 .java 文件
- **构建配置**: 可能加 1 行 `testImplementation`(仅在 MockMvcTester 缺失时),可能加 `spring-security-test` 显式版本(若 transitive 不够)
- **依赖 D1**: 本 change 大量使用 `sprint-2-test-data-builders` 提供的 5 个 builder,作为 fixture 地基
- **下游**:
  - Sprint 3 B(CI 速度)用本 change 跑出的覆盖率基线
  - Sprint 4 D3(coverage dashboard)接本 change 跑出的 Jacoco XML
  - Sprint 4 C1(PIT mutation)以本 change 提供的测试集为 mutation 评估对象
- **性能预算**: 预计新增 ~30 case,`./gradlew test` 总时间增加 30-60s(后端 ~30s 基线 + 新增)
- **GraalVM native**: 新增 slice test 全部 `@Tag("docker")`(repo)或 JVM(test 启动快),不影响 `nativeCompile` 路径