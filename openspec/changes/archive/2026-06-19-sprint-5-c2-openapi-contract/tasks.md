# Tasks — OpenAPI schema 契约测试(C2)

> 顺序按 design Migration Plan:spike 先行,兼容性不通即止损(C1 教训)。

## 1. springdoc 兼容性 spike(D1 — 阻塞后续)

- [x] 1.1 `build.gradle` 加 `testImplementation 'org.springdoc:springdoc-openapi-starter-webmvc-api:3.0.3'`(严格不进 implementation)
- [x] 1.2 `OpenApiContractIT`:全上下文 `@SpringBootTest(classes=SeafoodApplication)` + `@AutoConfigureMockMvc(addFilters=false)`(绕过 `anyRequest().denyAll()`)+ Testcontainer Mongo,GET `/v3/api-docs`
- [x] 1.3 **spike 通过**:springdoc 3.0.3 在 Boot 4.0.6 + Java 25 + Jackson 3 从真实 Controller 生成完整 spec(断言 openapi3 头 + /api/products + /api/admin/products + schemas 全过)。踩坑:JWT 属性前缀是 `security.jwt` 非 `jwt`
- [x] 1.4 **判定:成功** → 进 §2。兼容性 go/no-go 闸通过,与 ASM/native 无关

## 2. 生成 committed 契约 + 漂移门(对应 spec:契约 SoT + 漂移门)

- [x] 2.1 规范化:Jackson `ORDER_MAP_ENTRIES_BY_KEYS` + 缩进 + 剔除易变 `servers`(随机端口 host)
- [x] 2.2 生成并提交 `src/test/resources/contract/openapi.json`(55KB,覆盖 admin auth/dashboard/export/refunds/uploads + cart/orders/products/users 全部真实端点)
- [x] 2.3 漂移门 `contract_matchesCommittedSpec`:重生成+规范化与 committed diff;逃生口 `CONTRACT_UPDATE=true`。负向验证:篡改 committed → EXIT=1 BUILD FAILED ✓
- [x] 2.4 连跑 2 次 + git 无 diff → 规范化 byte 稳定,无假漂移

## 3. 响应一致校验 spike + 封装(对应 spec:响应一致校验)

- [x] 3.1 加 `swagger-request-validator-core:2.46.1`(2.44.0 不存在;3.0.0 跳过避 major API 风险;2.x 用 Jackson 2,项目有 2.21.2 满足)
- [x] 3.2 **spike:core 路线直接可行**——不用 mockmvc adapter,`OpenApiInteractionValidator` + 从 MvcTestResult 取 status/contentType/body 构 `SimpleResponse` 手接(MockMvcTester 无官方 adapter,core 路线绕过该问题)
- [x] 3.3 `OpenApiContractAssert`(testsupport/contract):加载 classpath `/contract/openapi.json` + get/post 便捷方法,slice 测试一行调用、不泄漏 com.atlassian import
- [x] 3.4 接入 `ProductControllerSliceTest`:正向 GET /api/products 符合 schema 通过;**永久负向** `conformance_rejectsResponseViolatingSchema`(Page 响应比对单商品 schema → AssertionError)证明有牙

## 4. 铺开 + 守护(对应 spec:不污染 native)

- [x] 4.1 代表性接入 4 端点(Product GET /api/products、Cart GET /api/cart、Order GET /api/orders、Order POST /api/orders/{id}/ship)跨 3 个 slice 测试,全过;其余端点随 slice 测试增量接入(design D4)
- [x] 4.2 dep-tree 证明:springdoc/swagger-validator **不在 runtimeClasspath**(只在 testRuntimeClasspath)→ 不进 native image。比跑 nativeCompile 更直接的充分证据
- [x] 4.3 跳过(冗余):testImplementation 作用域让生产代码编译期看不到 org.springdoc.*,比 ArchUnit 规则更硬的守护
- [x] 4.4 commit(见下)

## 5. 收尾

- [x] 5.1 README 加「API 契约(OpenAPI)」段:SoT 位置 + 漂移门重生成命令 + 响应校验 + 前端 openapi-typescript 消费
- [x] 5.2 回填 roadmap tasks T12:C2 done(形态 OpenAPI 非 SCC,记拓扑原因 + 触发条件已成立)
- [x] 5.3 `api-contract` spec sync 到 `openspec/specs/`,change 移入 archive/2026-06-19-sprint-5-c2-openapi-contract/
