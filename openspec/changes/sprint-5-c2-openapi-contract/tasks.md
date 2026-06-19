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

- [ ] 3.1 加 `testImplementation 'com.atlassian.oai:swagger-request-validator-core'`(版本 spike 定)
- [ ] 3.2 spike:试 `swagger-request-validator-mockmvc` 是否兼容 MockMvcTester;不兼容则用 core `OpenApiInteractionValidator` 手接(从 MockMvcTester 结果取 status/path/body 构 `SimpleResponse`)
- [ ] 3.3 封装 `OpenApiContractAssert` 测试辅助:一行校验「某响应符合 committed OpenAPI 中该端点 schema」
- [ ] 3.4 接 1 个 slice 测试(如 `ProductControllerSliceTest`)验证打通:符合 schema 通过、故意造偏离 → 失败,然后还原

## 4. 铺开 + 守护(对应 spec:不污染 native)

- [ ] 4.1 把响应校验接入其余现有 slice 测试(order / cart / admin product/order/refund),共 7 个端点族
- [ ] 4.2 `./gradlew nativeCompile` 后确认 springdoc **不在** runtimeClasspath / native 产物(grep 依赖树)
- [ ] 4.3 (可选守护)ArchUnit:禁 `com.seafood.**` 生产代码 import `org.springdoc.*`
- [ ] 4.4 commit:`test(contract): C2 OpenAPI 契约 — 漂移门 + 响应一致校验`

## 5. 收尾

- [ ] 5.1 README 加一段:OpenAPI 契约 SoT 位置 + 漂移门 + 前端可 `openapi-typescript` 消费
- [ ] 5.2 回填 `openspec/changes/test-suite-roadmap/tasks.md`:C2 由备选改为 done(形态 = OpenAPI 而非 SCC,记原因)
- [ ] 5.3 `/opsx:archive sprint-5-c2-openapi-contract` 归档并 sync `api-contract` spec 到 `openspec/specs/`
