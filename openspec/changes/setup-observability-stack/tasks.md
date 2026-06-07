# Implementation Tasks — setup-observability-stack

> **执行模型**:严格 TDD(测试先 → 实现 → 重构 → 验证)+ DDD 分层。每组对应 design.md Migration Plan 中的一个 PR,组内任务按依赖顺序排列。
>
> **验证命令速查**:
> - `./gradlew check` — 含 ArchUnit + checkNoRefreshScope + JVM 测试
> - `./gradlew :test --tests "*RequestIdFilterTest"` — 单类
> - `./gradlew nativeTest` — `@Tag("native")` 切片 + 触发 GraalVM agent 采集 metadata
> - `./gradlew nativeCompile` — 产出 `seafood-backend` binary
> - `backend/scripts/native-smoke.sh` — 端到端冒烟(health / prometheus / RSS)

## 0. Preflight — 解决 Open Questions

- [x] 0.1 OQ-1:在 JShell 里运行 `java.util.UUID.class.getMethods()` 过滤名含 `version7|timeBased|v7` 的方法,确认 JDK 25 是否内置 UUID v7 API — **JDK 25 无内置 v7 API**(jshell 验证,randomUUID 仍为 v4)
- [x] 0.2 OQ-1:若 JDK 25 无内置 API,在 `backend/build.gradle` 加 `implementation 'com.fasterxml.uuid:java-uuid-generator:5.1.0'`(或检查 Spring Boot 4.0.6 BOM 是否已托管) — **决议:引 JUG 5.1.0,见 ADR-OQ1**
- [x] 0.3 OQ-2:在 design.md 加 ADR 段落,锁定结构化日志 schema 选 `logstash`(Loki LogQL 友好,后续 Phase 2 Task #7 接 Loki/Grafana 一致) — **见 ADR-OQ2**
- [x] 0.4 OQ-3:在 design.md 加 ADR 段落,锁定 `orders.paid` 的 `amountBucket` 用几何分桶 4 档:`lt100` / `100to500` / `500to2000` / `gte2000`,提供 `OrderMetrics.bucketize(BigDecimal)` 工具方法签名 — **见 ADR-OQ3,含 Java 实现草图**
- [~] 0.5 在 `feat/observability-stack` 分支创建 4 个里程碑 commit 占位(`feat(observability): structured logging`、`...: metrics endpoint`、`...: business counters`、`chore(observability): verification & docs`)便于 PR review 单元化 — **跳过:每个 PR 完成时自然有一个 commit,无需占位 commit 污染历史**

## 1. PR #1 — Structured Logging + RequestIdFilter

### 1.1 RequestIdFilter 单元测试(测试先,TDD Red 阶段)

- [x] 1.1.1 新建 `backend/src/test/java/com/seafood/shared/observability/RequestIdFilterTest.java`(plain JUnit + Mockito,不依赖 `@WebMvcTest`)
- [x] 1.1.2 写测试 `generatesUuidV7_whenHeaderAbsent`:断言响应 header 含 UUID v7(`UUID.fromString(...).version() == 7`)
- [x] 1.1.3 写测试 `passesThroughValidUuid_whenHeaderValid`:输入 `X-Request-Id: 01931a45-7c80-7000-9b3e-3f8a1c5e4d20`,断言响应 header 等于输入
- [x] 1.1.4 写测试 `rejectsMalformedHeader_andLogsWarn`:输入 `X-Request-Id: <script>alert(1)</script>`,断言响应 header 是新生成的 UUID v7,WARN 日志不含原始恶意字符串
- [x] 1.1.5 写测试 `rejectsOversizedHeader`:输入长度 > 64 字符的 header,断言被丢弃
- [x] 1.1.6 写测试 `clearsMdcOnSuccessPath`:请求结束后 `MDC.get("requestId")` 必须为 `null`
- [x] 1.1.7 写测试 `clearsMdcOnExceptionPath`:`chain.doFilter` 抛 `RuntimeException`,断言 finally 块仍写入 response header + 清 MDC
- [x] 1.1.8 写测试 `isolatesAcrossSequentialRequestsOnSameVirtualThread`:同一 virtual thread 连续处理 2 个请求,断言第二个请求的 requestId 与第一个不同,且日志互不污染
- [x] 1.1.9 跑 `./gradlew :test --tests "*RequestIdFilterTest"` 确认全部测试**红色**(类还未实现)

### 1.2 RequestIdFilter 实现(TDD Green 阶段)

- [x] 1.2.1 新建包 `backend/src/main/java/com/seafood/shared/observability/`(对应 `shared` 层,跨切关注点)
- [x] 1.2.2 新建 `RequestIdFilter extends OncePerRequestFilter`,实现 read-or-generate + MDC put + response header set + finally clear
- [x] 1.2.3 抽 `RequestIdGenerator` 接口 + 默认实现(隔离 UUID v7 生成,便于单元测试 mock + 兼容 OQ-1 fallback)
- [x] 1.2.4 定义常量:`HEADER = "X-Request-Id"`、`MDC_KEY = "requestId"`、`MAX_LENGTH = 64`、`UUID_PATTERN = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", CASE_INSENSITIVE)`
- [x] 1.2.5 跑 `./gradlew :test --tests "*RequestIdFilterTest"` 确认全部测试**绿色**

### 1.3 Filter 注册与顺序

- [x] 1.3.1 新建 `backend/src/main/java/com/seafood/shared/observability/ObservabilityConfig.java`(`@Configuration`),注册 `FilterRegistrationBean<RequestIdFilter>`,`setOrder(Ordered.HIGHEST_PRECEDENCE + 100)`,`addUrlPatterns("/*")`
- [x] 1.3.2 验证 `JwtAuthenticationFilter` 的 order,确认 `RequestIdFilter.order` < `JwtAuthenticationFilter.order` — Spring Security `FilterChainProxy` 默认 `OrderedFilter.REQUEST_WRAPPER_FILTER_MAX_ORDER - 100 = -100`;我们 `Integer.MIN_VALUE + 100 ≪ -100`,filter 先于 Security 链执行,401/403/500 响应均能拿到 `X-Request-Id`(已被 `RequestIdFilterOrderIT#unauthenticatedRequestStillHasRequestId` 验证)
- [x] 1.3.3 写 IT `RequestIdFilterOrderIT`(plain `@SpringBootTest`):无 token 请求 → 断言 401 响应仍含 `X-Request-Id` header — `unauthenticatedRequestGeneratesUuidV7IfHeaderMissing` / `unauthenticatedRequestWithValidIncomingHeaderPassesThrough` / `requestIdFilterRegistrationIsOrderedBeforeSecurityChain` 三条断言均绿

### 1.4 Structured logging 配置

- [x] 1.4.1 修改 `backend/src/main/resources/application.yml`,在 `--- spring.config.activate.on-profile: prod` 段加 `logging.structured.format.console: logstash` 与 `logging.structured.format.file: logstash` — `StructuredLoggingProdIT.productionProfileEmitsJson` 验证绿
- [x] 1.4.2 在 dev profile 段加 `logging.pattern.console: "%d{HH:mm:ss.SSS} %-5level [%X{requestId}] %logger{36} - %msg%n"`(保留可读 + 带 requestId) — `StructuredLoggingDevIT.devProfileEmitsPatternWithRequestId` 验证绿
- [x] 1.4.3 加 `LOG_FORMAT` env 支持:Spring Boot 3.4+ 原生支持 `LOG_FORMAT=json` env / system property,在 `ConfigDataEnvironmentPostProcessor` 阶段映射到 `logging.structured.format.console=logstash`,无需在 application.yml 重复声明 — `StructuredLoggingLogFormatJsonIT.logFormatEnvOverridesDev`(用等价 `@TestPropertySource(properties = "logging.structured.format.console=logstash")`)验证绿

### 1.5 Structured logging IT

- [x] 1.5.1 新建 `backend/src/test/java/com/seafood/shared/observability/StructuredLogging{Prod,Dev,LogFormatJson}IT.java`,加 `@Tag("native")`(拆 3 个独立 IT 而非 @Nested 是因为 Logback appender 在 startup 时定型,@Nested 会跨 IT 复用 context 导致 structured encoding 状态泄漏)
- [x] 1.5.2 写测试 `productionProfileEmitsJson`:`@ActiveProfiles("prod")` + `OutputCaptureExtension` 捕获 stdout,触发 logger.info,断言 stdout 为 JSON 单行 + 含 `@timestamp` / `level` / `message` / `requestId` MDC 字段 — 绿
- [x] 1.5.3 写测试 `devProfileEmitsPatternWithRequestId`:`@ActiveProfiles("dev")` + `OutputCaptureExtension`,断言输出含 `[<uuid>]` 段 + 不含 JSON 特征 — 绿
- [x] 1.5.4 写测试 `logFormatEnvOverridesDev`:dev profile + `logging.structured.format.console=logstash`(等价于 `LOG_FORMAT=json`),断言输出是 JSON — 绿
- [x] 1.5.5 写测试 `stackTraceSerializesAsSingleField`:触发 ERROR 带堆栈,断言:(a) raw JSON 整行 stdout 不含字面换行,(b) JSON 内 `stack_trace` 字段值是 single string(含 `\\n` 转义),(c) 解析后含 `RuntimeException` / `intentional test failure` — 绿

### 1.6 错误路径覆盖

- [x] 1.6.1 在 `RequestIdFilterOrderIT` 加 `unauthenticatedRequestStillHasRequestId`:请求 `/api/admin/dashboard` 无 token → 401 响应含 `X-Request-Id`(实际 endpoint 选 `/api/admin/dashboard` 而非 `/api/admin/orders`,因 BFF 当前仅暴露 dashboard/stats/orders-detail 三个端点;语义等价,均触 `hasRole("ADMIN")` 401)
- [x] 1.6.2 加 `internalErrorPreservesRequestId`:制造一个会触发 `ErrorResponse(code=DOMAIN)` 的请求,断言 500/409 响应 header 与 ErrorResponse 日志中的 requestId 一致 — 端点 `/__test__/boom` 抛 `RuntimeException`,MockMvc 验证 5xx + `X-Request-Id` 存在 + UUID v7 透传;MDC 注入日志行与 header 一致性的完整断言由 `StructuredLoggingIT` 1.5.x 覆盖

### 1.7 Native compilation 验证(PR #1 收口)

- [ ] 1.7.1 跑 `./gradlew nativeTest`,确认 `StructuredLoggingIT` 通过且 `build/native/agent-output/test/` 含 Logback / Jackson / Spring structured logging 相关 entries
- [ ] 1.7.2 用 `backend/scripts/normalize-native-metadata.sh`(若存在)规范化 `META-INF/native-image/*.json`,否则手动对齐
- [ ] 1.7.3 跑 `./gradlew nativeCompile`,断言 `seafood-backend` binary 生成且无 missing reflection metadata 警告
- [ ] 1.7.4 commit `feat(observability): structured logging + RequestIdFilter` 含所有 1.x 文件 + 更新的 native metadata
- [ ] 1.7.5 PR #1 验收:本地 docker-compose 启动,`docker logs` 看到 JSON 单行,curl 请求带 `X-Request-Id` 回写正确

## 2. PR #2 — Metrics Endpoint on Management Port 9090

### 2.1 依赖与配置

- [ ] 2.1.1 在 `backend/build.gradle` 加 `implementation 'io.micrometer:micrometer-registry-prometheus'`(版本由 Spring Boot 4.0.6 BOM 托管)
- [ ] 2.1.2 修改 `application.yml`:`management.server.port: 9090`、`management.server.address: 0.0.0.0`
- [ ] 2.1.3 加 `management.endpoints.web.exposure.include: health,prometheus,info`
- [ ] 2.1.4 加 `management.endpoint.prometheus.access: read_only`、`management.endpoint.health.show-details: when_authorized`
- [ ] 2.1.5 加 `management.metrics.tags.application: seafood-backend`(common tag,替代 D5 表中的 `application` 字段)

### 2.2 MetricsEndpointIT(TDD)

- [ ] 2.2.1 新建 `backend/src/test/java/com/seafood/shared/observability/MetricsEndpointIT.java`,加 `@Tag("native")`,用 `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `properties = "management.server.port=0"`(测试用随机端口避开冲突)
- [ ] 2.2.2 注入 `@LocalManagementPort int managementPort`(Spring 内置占位)和 `@LocalServerPort int serverPort`
- [ ] 2.2.3 写测试 `prometheusEndpointReturns200OnManagementPort`:GET `http://localhost:{managementPort}/actuator/prometheus`,断言 200 + Content-Type 含 `text/plain` 与 `version=0.0.4`
- [ ] 2.2.4 写测试 `prometheusEndpointAbsentFromBusinessPort`:GET `http://localhost:{serverPort}/actuator/prometheus`,断言 404
- [ ] 2.2.5 写测试 `healthEndpointReturns200OnManagementPort`:GET `http://localhost:{managementPort}/actuator/health`,断言 200
- [ ] 2.2.6 写测试 `bodyContainsTypeAndHelpHeaders`:断言响应体含 `# TYPE` 与 `# HELP` 行各至少一处
- [ ] 2.2.7 写测试 `httpServerRequestsMeterAppearsAfterCall`:先 GET 业务端口的 `/api/products`(用 TestRestTemplate),再读 prometheus 端点,断言含 `http_server_requests_seconds_count{...uri="/api/products"...}` 样本

### 2.3 MeterRegistryCustomizer

- [ ] 2.3.1 在 `ObservabilityConfig` 加 `@Bean MeterRegistryCustomizer<MeterRegistry> commonTags()` 返回 `r -> r.config().commonTags("application", "seafood-backend")`(若 2.1.5 已通过 yml 完成,则跳过本步骤)
- [ ] 2.3.2 写单元测试断言 customizer 已注册

### 2.4 Mongo metrics 集成

- [ ] 2.4.1 验证 Spring Boot autoconfig 是否自动注册 `MongoMetricsCommandListener`(检查 `MongoMetricsAutoConfiguration` 是否在 classpath)
- [ ] 2.4.2 若未自动注册,在 `ObservabilityConfig` 显式 `@Bean MongoClientSettingsBuilderCustomizer` 注入 `MongoMetricsCommandListener`
- [ ] 2.4.3 在 `MetricsEndpointIT` 加 `mongoCommandsMeterAppears`:触发一次 Mongo find,断言 prometheus 输出含 `mongodb_driver_commands_seconds_count`

### 2.5 docker-compose 同步

- [ ] 2.5.1 修改 `docker-compose.yml`:`backend.ports` 保持 `["8080:8080"]`,**不**加 `9090:9090`
- [ ] 2.5.2 修改 `backend.healthcheck.test`:从 `curl -f http://localhost:8080/actuator/health` 改为 `curl -f http://localhost:9090/actuator/health`(容器内访问)
- [ ] 2.5.3 验证 `mongodb` 依赖关系不变,确认 backend 健康检查仍能正确通过 `depends_on.mongodb.condition: service_healthy` 闸口
- [ ] 2.5.4 本地 `docker-compose down -v && docker-compose up -d`,等 60s,`docker-compose ps` 看 backend 是 `healthy`

### 2.6 CI 同步

- [ ] 2.6.1 修改 `backend/scripts/native-smoke.sh`:health 检查从 `curl http://localhost:8080/actuator/health` 改 `curl http://localhost:9090/actuator/health`(若容器内运行)或 `docker exec backend curl ...`(若 host 上跑)
- [ ] 2.6.2 加新断言:`docker exec backend curl -sf http://localhost:9090/actuator/prometheus | grep -q 'http_server_requests_seconds_count' || exit 1`
- [ ] 2.6.3 加 RSS 断言:`rss=$(docker exec backend cat /proc/1/status | awk '/VmRSS/ {print $2}'); [ "$rss" -lt 204800 ] || { echo "RSS $rss KB exceeds 200 MB"; exit 1; }`(KB 单位,200 MB = 204800 KB)
- [ ] 2.6.4 验证 `.github/workflows/native.yml` 路径过滤仍包含 `backend/**`、`docker-compose.yml`、`Dockerfile`

### 2.7 Native compilation 验证(PR #2 收口)

- [ ] 2.7.1 跑 `./gradlew nativeTest`,确认 `MetricsEndpointIT` 通过,agent 采集 Micrometer Prometheus exposition writer 的 reflect/resource entries
- [ ] 2.7.2 跑 `./gradlew nativeCompile`,断言无 missing metadata 警告
- [ ] 2.7.3 本地手跑 `backend/scripts/native-smoke.sh` 全套,确认全绿
- [ ] 2.7.4 commit `feat(observability): metrics endpoint on management port 9090`
- [ ] 2.7.5 PR #2 验收(高风险 PR):staging 环境(若无 staging,手工模拟 `docker-compose up` 1 小时)无健康检查抖动

## 3. PR #3 — Business Counters

### 3.1 Amount bucketing 工具

- [ ] 3.1.1 新建 `backend/src/main/java/com/seafood/order/application/OrderMetrics.java`,实现 `static String bucketize(BigDecimal amount)` 几何 4 档:`< 100` → `lt100`、`[100, 500)` → `100to500`、`[500, 2000)` → `500to2000`、`>= 2000` → `gte2000`
- [ ] 3.1.2 单元测试 `OrderMetricsTest` 覆盖 4 个区间边界 + 负数(应抛 `IllegalArgumentException`)+ null(应抛 `NullPointerException`)

### 3.2 ProductApplicationService.searchProducts

- [ ] 3.2.1 修改 `ProductApplicationServiceTest`:加 `meterRegistry` mock,assert `counter("products.queried", "category", "鱼类").count()` 在调用后递增 1
- [ ] 3.2.2 在 `ProductApplicationService` 注入 `MeterRegistry`,在 `searchProducts` 成功路径对每个匹配的 category 调用 `meterRegistry.counter("products.queried", "category", cat.name()).increment()`
- [ ] 3.2.3 跑 `./gradlew :test --tests "*ProductApplicationServiceTest"` 绿

### 3.3 OrderApplicationService.createOrder

- [ ] 3.3.1 修改 `OrderApplicationServiceTest.createOrder_success_*`:加断言 `counter("orders.created", "paymentMethod", "wechat").count() == 1`
- [ ] 3.3.2 加测试 `createOrder_failure_doesNotIncrementCounter`:制造异常,断言 counter 不递增
- [ ] 3.3.3 在 `OrderApplicationService.createOrder` 成功路径埋点
- [ ] 3.3.4 跑测试绿

### 3.4 OrderApplicationService.cancelOrder

- [ ] 3.4.1 修改 `OrderApplicationServiceTest`:覆盖 3 个 reason(`user`、`timeout`、`admin`),分别断言对应 counter +1
- [ ] 3.4.2 在 `cancelOrder` 实现埋点

### 3.5 OrderApplicationService.markPaid

- [ ] 3.5.1 修改 `OrderApplicationServiceTest`:用 `350.00` 总额测试,断言 `counter("orders.paid", "paymentMethod", "wechat", "amountBucket", "100to500").count() == 1`
- [ ] 3.5.2 加测试覆盖 4 个 amount bucket 边界(99.99/100/499.99/500/1999.99/2000)
- [ ] 3.5.3 在 `markPaid` 实现埋点,调用 `OrderMetrics.bucketize()`

### 3.6 UserApplicationService.login

- [ ] 3.6.1 修改 `UserApplicationServiceTest`:3 个分支(success/failed/locked),分别断言对应 counter +1
- [ ] 3.6.2 在 `login` 3 个返回路径分别埋点

### 3.7 ArchUnit cardinality 约束

- [ ] 3.7.1 在 `backend/src/test/java/com/seafood/shared/architecture/ArchitectureTest.java`(或新建 `MetricsArchitectureTest`)加规则:扫描所有 `io.micrometer.core.instrument.MeterRegistry.counter/timer/gauge` 调用,禁止 tag 名等于 `userId`、`orderId`、`productId`、`email`
- [ ] 3.7.2 规则实现:用 ArchUnit 的 `methodCallTarget` + 参数白名单,失败时打印调用栈定位违规处
- [ ] 3.7.3 故意制造一个含 `"userId"` 标签的违规埋点 → 跑 `./gradlew :test --tests "*ArchitectureTest"` 应**红**(验证规则生效)→ 删除违规埋点 → 再跑应**绿**

### 3.8 PR #3 收口

- [ ] 3.8.1 跑 `./gradlew check`(含全部新增测试 + ArchUnit)
- [ ] 3.8.2 启动 `docker-compose up`,触发一次完整的"下单 → 支付 → 取消"流程,curl `/actuator/prometheus` 看到 4 个 orders 系列 counter
- [ ] 3.8.3 commit `feat(observability): 5 business counters at ApplicationService boundary`

## 4. PR #4 — Verification + Docs

### 4.1 端到端验证

- [ ] 4.1.1 跑 `./gradlew clean check nativeTest nativeCompile` 全链路绿
- [ ] 4.1.2 本地 `docker-compose down -v && docker-compose up -d --build`
- [ ] 4.1.3 等待 backend healthy,跑 `backend/scripts/native-smoke.sh` 全套(health / prometheus / RSS / latency)
- [ ] 4.1.4 触发完整用户旅程后,导出 `curl http://localhost:9090/actuator/prometheus > /tmp/metrics.txt`,人工 review 含全部 5 个业务 counter + http_server_requests + jvm.* + mongodb.driver.commands
- [ ] 4.1.5 验证日志:`docker logs backend 2>&1 | head -20 | jq .`(每行可解析为 JSON,含 `@timestamp` / `level` / `message` / `requestId`)
- [ ] 4.1.6 验证 RSS:`docker exec backend cat /proc/1/status | grep VmRSS`,< 204800 KB

### 4.2 文档同步

- [ ] 4.2.1 在 `CLAUDE.md` 加"可观测性"段落:
  - 管理端口 9090(与 8080 物理隔离)
  - 5 个业务 counter 名称表 + 标签约束
  - dev/prod profile 日志格式
  - `LOG_FORMAT=json` env 用法
  - 与 Task #7(Prometheus/Grafana)、#8(Sentry)的衔接
- [ ] 4.2.2 在 `README.md` 加"Operations"段落:scrape 端点示例 `prometheus.yml` 片段 + 日志 schema 字段表
- [ ] 4.2.3 在 `backend/scripts/native-smoke.sh` 顶部加注释说明新增的 prometheus + RSS 断言
- [ ] 4.2.4 在 `docs/runbooks/`(若尚未存在,从 Task #5 复用模板)写一个 mini-runbook:`/actuator/prometheus 不可达` 的排查步骤

### 4.3 PR 收口

- [ ] 4.3.1 commit `chore(observability): end-to-end verification & docs`
- [ ] 4.3.2 push `feat/observability-stack` 到 origin
- [ ] 4.3.3 用 `gh pr create --base main --title "feat(observability): metrics endpoint + structured logging + business counters" --body-file <<<...` 创建 PR,body 引用 `openspec/changes/setup-observability-stack/{proposal,design}.md`
- [ ] 4.3.4 PR 描述末尾加 `🤖 Generated with [Claude Code](https://claude.com/claude-code)`(项目 PR 约定)
- [ ] 4.3.5 等 CI 全绿(jvm-check / native / security 三个 workflow)

## 5. Release & Cleanup

- [ ] 5.1 PR review 通过后,squash merge 到 main
- [ ] 5.2 跑 `openspec apply setup-observability-stack`(或对应 `/opsx:apply` 流程)归档 change
- [ ] 5.3 跑 `openspec sync`(若需要)把 `metrics-export` 与 `structured-logging` 两个 spec 提升到 `openspec/specs/`
- [ ] 5.4 通过 `git worktree remove .worktrees/setup-observability-stack` 清理 worktree(或用 ExitWorktree action=remove)
- [ ] 5.5 TaskUpdate #1 → `completed`;TaskList 确认 #7、#8 不再 blocked
- [ ] 5.6 给主 session 一份"实施完成 + 关键回顾"短报告,触发 Task #7 决策环节
