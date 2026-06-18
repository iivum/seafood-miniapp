## Why

目前 backend 只有 `/actuator/health` 一个观测端点 — 任何生产事故都只能靠 `docker logs` 翻文本日志 RCA,且日志是非结构化的 console pattern,跨请求无法串联(没有 TraceID)。在引入 Prometheus / Grafana / Sentry / OTel 等任何监控平台之前,先把"可被观测"的基础设施铺好:暴露 metrics 端点 + 结构化日志 + 请求级 TraceID。这一期不绑死任何收集器或 SaaS,但完成后任何监控平台可以 1 小时内接上。

## What Changes

- **集成 Micrometer + Prometheus registry**:`micrometer-registry-prometheus` 依赖,Spring Boot 自动配置注册 `/actuator/prometheus` 端点(Prometheus exposition 格式)
- **暴露 metrics 端点**:`management.endpoints.web.exposure.include` 增加 `prometheus`;新端点默认仅限内网(通过 `SecurityHeadersIT` 风格的 ADMIN 角色保护,或 `management.server.port` 拆分到独立端口)
- **关键业务指标埋点**:HTTP 请求时延(`http.server.requests` 自动)+ MongoDB 操作时延(`MongoCommandTagsProvider`)+ JVM/Native 内存(自动)+ 自定义计数器(订单创建 / 支付 / 商品查询热路径)
- **结构化日志**:引入 `logstash-logback-encoder`,新建 `logback-spring.xml`,生产环境输出 JSON 单行(便于后续 Loki/ELK/SLS 直接消费),`dev` profile 保留可读 pattern
- **请求级 TraceID 注入**:新建 `shared/observability/` 包,加 `RequestIdFilter`(Servlet Filter)— 入口生成 UUID v7(或读 `X-Request-Id` header 透传),写入 MDC + response header,所有日志行自动带 `requestId` 字段
- **GraalVM Native 兼容性**:Micrometer / Logback JSON 编码器在 native-image 下需要 reflect/resource metadata。**遵循 CLAUDE.md §nativeTest 切片约定** — 在现有 3 个 IT 上加观测断言触发 agent 采集,提交 `META-INF/native-image/` 更新
- **BREAKING**(本地开发体验):生产 profile 日志格式从 console pattern 变 JSON;`dev` profile 保留旧格式 + `LOG_FORMAT=json` 环境变量 override

明确**不做**的(后续 todo 单独拆):
- ❌ 不部署 Prometheus / Grafana / Loki / Jaeger / Sentry 任何收集器或 SaaS
- ❌ 不接入小程序前端 / Admin UI 监控
- ❌ 不引入 OpenTelemetry SDK(后续如需 traces,再单独提 change)

## Capabilities

### New Capabilities
- `metrics-export`:通过 `/actuator/prometheus` 暴露 Prometheus 文本格式指标(HTTP / JVM / Mongo / 自定义业务计数器),含端点鉴权与 native-image 兼容性要求
- `structured-logging`:Logback JSON 结构化输出 + MDC `requestId` / `userId` 字段串联单次请求的全部日志行,含 `dev` / `prod` profile 切换契约

### Modified Capabilities
- 无 — `backend-api` capability 描述的是业务 HTTP 接口契约,本次变更只在 `/actuator/**` 命名空间下增端点 + 跨切日志格式调整,不修改既有 spec 行为。

## Impact

**代码 / 配置变更**:
- `backend/build.gradle` — 新增 `io.micrometer:micrometer-registry-prometheus`、`net.logstash.logback:logstash-logback-encoder`(版本对齐 Spring Boot 4.0.6 BOM)
- `backend/src/main/resources/application.yml` — `management.endpoints.web.exposure.include: health,prometheus`;`management.endpoint.prometheus.access: read_only`;`management.metrics.tags.application: seafood-backend`
- `backend/src/main/resources/logback-spring.xml` — 新建,`<springProfile>` 区分 dev/prod
- `backend/src/main/java/com/seafood/shared/observability/RequestIdFilter.java` — 新建 Servlet Filter,优先级早于 `JwtAuthenticationFilter`
- `backend/src/main/java/com/seafood/shared/observability/ObservabilityConfig.java` — `MeterRegistryCustomizer` 配 common tags
- `backend/src/main/resources/META-INF/native-image/` — `reflect-config.json` / `resource-config.json` 由 `nativeTest` agent 更新
- `backend/src/test/java/.../observability/` — `MetricsEndpointIT`、`RequestIdFilterTest`、`StructuredLoggingIT`(其中 metrics 端点 IT 打 `@Tag("native")`)

**契约 / 安全**:
- `/actuator/prometheus` 默认 401(无 token);需要决策(留给 design):是 ADMIN role 鉴权,还是绑 `management.server.port: 9090` 仅内网监听
- HTTP response 多 `X-Request-Id` header(下游 / 前端可用作问题追溯凭证)
- 错误响应 `ErrorResponse` 后续可考虑加 `requestId` 字段(本期可选,在 design 决策)

**依赖 / 部署**:
- Docker 镜像层多约 2 MB(Logback JSON encoder + Micrometer prometheus registry)
- Native binary 大小预计 +5~8 MB(reflection metadata)
- docker-compose 不变;`mongodb` healthcheck 不受影响
- RSS 预算:仍需 < 200 MB(design §3.1) — Micrometer + Prometheus registry 预期 +5~10 MB,需在 nativeTest 阶段验证

**测试 / CI**:
- `./gradlew check` 新增 IT;`nativeTest` 需要更新 metadata 并 commit
- `.github/workflows/native.yml` 中 `native-smoke.sh` 加 `curl /actuator/prometheus | grep http_server_requests` 断言

**风险**:
- Logback JSON encoder 在 GraalVM Native 下可能需要额外 reflect-config(Jackson 子模块)— 通过 nativeTest agent 验证,如踩坑则在 design 中记录解决方案
- 暴露 metrics 端点若鉴权疏漏会泄漏内部指标(JVM 内存、数据库连接数等)— 必须在 IT 中断言未授权访问 401
