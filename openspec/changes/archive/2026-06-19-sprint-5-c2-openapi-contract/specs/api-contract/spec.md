## ADDED Requirements

### Requirement: 从 Controller 注解生成 OpenAPI 3 契约且不污染 native 运行时

后端 SHALL 用 springdoc-openapi 从现有 Controller + DTO 生成 OpenAPI 3 spec;生成 MUST 限定在 test/build 作用域,业务端口 8080 MUST NOT 暴露 `/v3/api-docs`,且 GraalVM native image MUST NOT 因 springdoc 而改变(`nativeCompile` 产物不含 springdoc 运行时类)。

#### Scenario: 测试期生成 OpenAPI spec 成功

- **WHEN** 契约生成测试运行
- **THEN** 产出一份覆盖核心端点(product / order / cart / admin BFF)的 OpenAPI 3 JSON,paths 与 components.schemas 非空

#### Scenario: 业务运行时不暴露 api-docs

- **WHEN** 应用以业务 profile 启动并请求 `:8080/v3/api-docs`
- **THEN** 该路径不可达(404 / 未注册),契约仅作测试期产物存在

#### Scenario: native 编译不受 springdoc 影响

- **WHEN** 执行 `./gradlew nativeCompile`
- **THEN** 编译成功且 springdoc 不进入 native image(springdoc 为 test/build 依赖,不在 runtimeClasspath)

### Requirement: committed OpenAPI 契约作为 SoT 且有漂移门

仓库 SHALL 提交一份 `openapi.json` 作为 API 契约 SoT;契约漂移门测试 MUST 重生成 spec 并与 committed 版比对,二者不一致时测试 MUST 失败并提示提交更新——使 API 变更必须被有意识地提交,前端消费方不被悄悄破坏。

#### Scenario: 契约与实现一致时漂移门通过

- **WHEN** committed `openapi.json` 与当前 Controller 生成的 spec 一致
- **THEN** 漂移门测试通过

#### Scenario: API 改动未更新 committed 契约时漂移门失败

- **WHEN** 开发者改了某端点的响应结构但未重新生成提交 `openapi.json`
- **THEN** 漂移门测试失败,错误信息指向需重生成并提交契约

### Requirement: 响应一致校验证明实现符合声明

现有 Controller slice 测试的 HTTP 响应 SHALL 用 OpenAPI schema validator 校验真正符合契约声明的 schema(字段、类型、必填、状态码);响应与声明 schema 不符时测试 MUST 失败。

#### Scenario: 响应符合 schema 时校验通过

- **WHEN** 某端点 slice 测试发起请求,响应体结构符合 OpenAPI 中该端点声明的 schema
- **THEN** schema 校验通过

#### Scenario: 响应偏离声明 schema 时校验失败

- **WHEN** 实现返回了 schema 未声明的结构(缺必填字段 / 类型不符 / 多余必填项)
- **THEN** schema 校验失败,定位到偏离的字段/端点
