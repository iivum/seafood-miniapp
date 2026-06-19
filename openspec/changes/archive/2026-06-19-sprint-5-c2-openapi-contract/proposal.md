## Why

后端有 52 个 HTTP 端点(其中 BFF 已成长到 7 Controller / 15 端点),被两个 **非 JVM** 消费者依赖:微信小程序(TS)和 admin-ui。当后端改了响应结构、字段名或状态码,前端只能在运行期踩坑发现——没有任何机制保证「后端实际返回的」和「前端期望的」一致。roadmap 的 C2 名义是 Spring Cloud Contract,但 SCC 是给 JVM 服务间消费者驱动契约用的,生成的 stub JAR 这两个 TS 前端消费不了——对本拓扑是错工具。对「1 JVM producer + 2 TS consumer」,真正的契约就是 **HTTP API 的 OpenAPI schema 本身**。本 change 把 OpenAPI 3 spec 立为契约 SoT,用两道闸保证「实现 == 声明」,且产物可被前端直接消费生成 client。

## What Changes

- 引入 **springdoc-openapi 3.0.3**(兼容 Spring Boot 4.0.x)从现有 Controller + DTO 注解生成 OpenAPI 3 spec
- springdoc 严格限定 **test/build 作用域**,**不进 GraalVM native 运行时**、不在业务端口 8080 暴露 `/v3/api-docs`(守 design §D2 端口物理隔离精神)
- 新增 **committed 契约** `openapi.json` 作为 SoT,提交进仓
- **闸 ① 漂移门**:测试重生成 spec 与已提交版 diff,不一致即 fail——逼 API 变更被有意识提交,前端不被悄悄改坏
- **闸 ② 响应一致校验**:现有 ControllerSliceTest 的响应用 OpenAPI schema validator 校验真符合声明,接住「实现偏离 schema」
- 前端消费(openapi-typescript 生成 client)**不在本 change 内**,仅由 committed spec 启用

## Capabilities

### New Capabilities
- `api-contract`: OpenAPI 3 spec 作为后端 HTTP API 契约的生成方式、committed SoT、漂移门 gate、响应一致校验、作用域(test-scope 不污染 native)、以及前端消费启用

### Modified Capabilities
<!-- 无:不改现有端点行为;只新增契约生成 + 两道校验闸 -->

## Impact

- `backend/build.gradle`:新增 springdoc-openapi-starter-webmvc-api(test/build scope)+ swagger-request-validator(testImplementation)
- 新增契约生成 + 漂移门测试;现有 7 个 ControllerSliceTest 接入响应 schema 校验
- 新增 committed `openapi.json`(SoT)
- **兼容性风险(C1 教训,spike 先行)**:① springdoc 3.0.3 vs Boot 4.0.6(差一 patch)② Spring Boot 4 用 Jackson 3 ③ swagger-request-validator 对 spring-test 7 的 MockMvcTester 适配 ④ springdoc 必须不漏进 native image
- 无生产端点行为变更,无 API 破坏,无 BREAKING
