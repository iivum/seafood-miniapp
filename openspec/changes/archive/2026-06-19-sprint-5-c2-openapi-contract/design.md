# Design — OpenAPI schema 契约测试(C2)

## Context

roadmap C2 名义是 Spring Cloud Contract,触发条件「BFF 复杂」现已成立(BFF 7 Controller / 15 端点,全后端 52 端点)。但 SCC 是 JVM 服务间消费者驱动契约工具,生成的 stub JAR 本项目两个 **TS 前端**(微信小程序、admin-ui)消费不了;producer 侧单跑又与现有 7 个 ControllerSliceTest 大量重叠 —— **SCC 对「1 JVM producer + 2 TS consumer」拓扑是错工具**。brainstorm 定:契约 = HTTP API 的 OpenAPI 3 spec 本身,力度 = 漂移门 + 响应一致校验。

工具链:Java 25 + Spring Boot 4.0.6 + GraalVM Native + Gradle 9 + spring-test 7(MockMvcTester)。已知:springdoc-openapi 3.0.3 构建在 Boot 4.0.5 上(v3.0.1 已升 4.0.1),Boot 4 用 Jackson 3(springdoc 3.0.x 已解早期 HAL module 坑)。

## Goals / Non-Goals

**Goals**
- 用 springdoc 从现有注解生成 OpenAPI 3 spec,**严格 test/build 作用域**,不进 native 运行时、不在 8080 暴露
- committed `openapi.json` 作 SoT + 漂移门(重生成 diff,不一致 fail)
- 现有 slice 测试响应用 schema validator 校验符合声明
- 产物可被前端 `openapi-typescript` 消费(仅启用)

**Non-Goals**
- 不做 Spring Cloud Contract(拓扑不匹配)
- 不做 Pact / broker 基建
- 前端生成 client 不在本 change 内
- 不强求 52 端点全部响应校验(YAGNI;先接现有 7 slice 测试覆盖的端点)
- 不改任何现有端点行为

## Decisions

### D1:springdoc 严格 test/build 作用域,不进 runtime

springdoc 依赖只放 `testImplementation`(或专用 build-time configuration),**不进 `implementation`/runtimeClasspath**。生成 spec 走一个全上下文测试(`@SpringBootTest` 或 `@WebMvcTest` 聚合)请求 `/v3/api-docs` 拿 JSON,而非运行期暴露端点。
- **理由**:① 守 design §D2「业务端口 8080 不挂额外端点」② GraalVM native image 不含 springdoc(反射重、native 元数据风险大)③ 契约是开发期产物,不是运行期能力。
- **Alternative**:用 `org.springdoc.openapi-gradle-plugin` 启动 app dump spec → 否决,需要 MongoDB 起全栈,CI 重且和 native 隔离更难。

### D2:漂移门 = 重生成 vs committed diff

测试生成当前 spec,与 `src/test/resources/contract/openapi.json`(committed SoT)做 **规范化 JSON 比对**(排序 key / 忽略易变元数据如 server url),不一致 fail 并提示 `./gradlew <regenTask>` 重生成提交。
- **理由**:把「API 变了」变成一次显式提交动作,review 时可见 diff,前端方能从 git 历史看契约演进。
- **规范化**:springdoc 输出顺序/时间戳可能不稳定 → 比对前规范化,避免假阳性 flaky(spike 验证稳定性)。

### D3:响应校验 = atlassian swagger-request-validator(core API)

用 `com.atlassian.oai:swagger-request-validator-core` 加载 committed OpenAPI,对 slice 测试的响应(status + body)做 schema 校验。
- **适配**:官方 `swagger-request-validator-mockmvc` 针对 **classic MockMvc**;本项目用 **MockMvcTester**(spring-test 7)。spike 验证:优先尝试 mockmvc adapter;不兼容则用 core 的 `OpenApiInteractionValidator` 手接(从 MockMvcTester 结果取 status/body/path 构造 `SimpleResponse` 校验)。封装成一个 `OpenApiContractAssert` 测试辅助,slice 测试一行调用。
- **Alternative**:纯 JSON-schema 断言(networknt json-schema-validator)→ 否决,要手抽每端点 schema,不如直接喂整份 OpenAPI。

### D4:作用域 = 核心端点优先

漂移门覆盖**全部**生成端点(spec 是整份);响应校验先接现有 7 个 ControllerSliceTest 的端点(product / order / cart 公共 + admin BFF product/order/refund)。其余端点的响应校验随其 slice 测试补齐时增量接入。

## Risks / Trade-offs

- **[springdoc 3.0.3 vs Boot 4.0.6 差一 patch]** → Mitigation:Task 1 spike 起 `@SpringBootTest` 拿 `/v3/api-docs` 验证能生成;不通先试 springdoc 最新 3.0.x,仍不通则记录确切错误止损(C1 模式)。
- **[Jackson 3 / HAL module 早期坑]** → Mitigation:项目无 HATEOAS,风险低;spike 覆盖。
- **[swagger-request-validator 不识别 MockMvcTester]** → Mitigation:D3 的 core API 手接兜底,不依赖 mockmvc adapter。
- **[springdoc 漏进 native runtime]** → Mitigation:依赖严格 testImplementation;Task 加一条 `nativeCompile` 后 grep 产物/依赖树确认 springdoc 不在 runtimeClasspath。ArchUnit 可选守:禁 `com.seafood.**` 生产代码 import `org.springdoc.*`。
- **[漂移门 flaky(spec 输出不稳定)]** → Mitigation:D2 规范化比对;spike 跑 3 次确认 byte 稳定。

## Migration Plan

1. spike:加 springdoc test 依赖,起测试拿 `/v3/api-docs`,验证生成 + 不进 native(Task 1)
2. 生成并提交 `openapi.json` SoT + 漂移门测试(Task 2)
3. 接 swagger-request-validator,封装 `OpenApiContractAssert`,接 1-2 个 slice 测试验证打通(Task 3)
4. 铺开到 7 个 slice 测试 + 守护(ArchUnit 禁生产 import springdoc)(Task 4)
5. README + 归档

**Rollback**:全部 test-scope,出问题摘 test 依赖 + 删契约测试即可,零生产/运行时影响。

## Open Questions

- swagger-request-validator 是否有现成 MockMvcTester adapter,还是必须 core 手接?→ Task 3 spike 实测。
- springdoc 生成的 spec 在重复运行间是否 byte 稳定(影响漂移门)?→ Task 2 spike 跑多次确认,必要时规范化。

## Sources

- [springdoc-openapi releases](https://github.com/springdoc/springdoc-openapi/releases) — 3.0.3 built on Spring Boot 4.0.5,v3.0.1 升 4.0.1
- [springdoc Boot 4 support issue #3095](https://github.com/springdoc/springdoc-openapi/issues/3095)
- [atlassian swagger-request-validator](https://bitbucket.org/atlassian/swagger-request-validator)
