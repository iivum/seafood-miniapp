## Context

当前 backend 只有 `/actuator/health` 单一观测端点;无 metrics 暴露,日志为非结构化 console pattern,跨请求无 TraceID 串联。Proposal 已确认本期 scope 收敛为"铺基础设施"(不部署任何收集器/SaaS),Phase 2 通过 Task #7(Prometheus/Grafana)与 #8(Sentry/GlitchTip)分别接入。

四项关键决策已由用户拍板:
- JSON 日志方案:Spring Boot 4 原生 structured logging
- Metrics 鉴权:独立 management port 9090(端口隔离)
- RequestID:UUID v7(时间有序)
- 指标范围:技术指标(自动) + 5 个核心业务计数器

约束:
- Stack:Java 25 + Spring Boot 4.0.6 + GraalVM Native + Gradle 9.x
- 性能预算:RSS < 200 MB(design §3.1)
- 测试覆盖率:backend ≥80%,核心模块 ≥90%
- `@RefreshScope` 禁用(GraalVM 不兼容)
- `@WebMvcTest` + `@MockBean` 在 Spring Boot 4 不可用 → plain JUnit + Mockito
- `nativeTest` 切片:加新代码路径需 `@Tag("native")` 触发 agent 采集 metadata

## Goals / Non-Goals

**Goals:**
- `/actuator/prometheus` 暴露 Prometheus exposition 格式
- 生产 profile 日志为 JSON 单行,字段标准化(Logstash schema)
- 每条日志带 `requestId` 字段,与 HTTP `X-Request-Id` header 双向同步
- 5 个业务计数器在 ApplicationService 边界自动埋点
- GraalVM Native 编译通过,`./gradlew check && ./gradlew nativeCompile` 绿
- RSS 仍 < 200 MB(允许新增 5-10 MB 余量)

**Non-Goals:**
- 不部署 Prometheus / Grafana / Loki / Jaeger / 任何收集器或 SaaS
- 不接入 OpenTelemetry SDK 或任何 traces 系统
- 不接入异常聚合(Sentry / GlitchTip)
- 不监控小程序前端 / Admin UI
- 不改造 `ErrorResponse` 业务字段(本期可选 `requestId` 透传 response header 已足够,字段加入留待 Task #8 决策)
- 不做日志告警 / 容量规划

## Decisions

### D1 — Logback 改造方式:Spring Boot 4 原生 structured logging

**决策**:启用 `logging.structured.format.console: logstash`,弃用 console pattern。

**理由**:
- Spring Boot 3.4+ 内置,Boot 4.0.6 已 GA,零外部依赖
- GraalVM Native 官方验证(无需手写 reflect-config)
- 配置在 `application.yml` 一行切换,profile 化天然支持
- 字段命名遵循 Logstash schema,与 Loki/Kibana/Grafana 兼容性最广

**替代方案**:
- ❌ `logstash-logback-encoder` v8.x:成熟但引入 Jackson 反射依赖,native-image 需额外 reflect-config;留作 OQ-1 fallback 方案
- ❌ `logback-json-classic`:社区活跃度低,被 Spring 原生支持完全替代

**配置示意**(留 design 层,精确 YAML 在 tasks):
```yaml
logging:
  structured:
    format:
      console: logstash    # prod profile
      file: logstash
spring:
  config:
    activate.on-profile: dev
logging:
  pattern.console: "%d{HH:mm:ss.SSS} %-5level [%X{requestId}] %logger{36} - %msg%n"   # dev profile 保留可读 pattern
```

### D2 — Metrics 端点:独立 management port 9090

**决策**:
- `management.server.port: 9090`
- `management.server.address: 0.0.0.0`(容器内监听)
- 业务 8080 端口剥离所有 `/actuator/**` 路由
- 9090 端口在 docker-compose 不映射到 host(仅 docker network 内访问)

**理由**:
- K8s/云原生标准做法,业务端口与管理端口物理隔离
- Prometheus 抓取无需 token,部署简单
- 鉴权代码不污染业务过滤器链,`JwtAuthenticationFilter` 仍只服务于 8080
- 防 misuse:`/actuator/prometheus` 误暴露公网即触发限流/防火墙告警

**替代方案**:
- ❌ 复用 8080 + ADMIN role:鉴权栈复杂化,Prometheus 抓取需注入 admin token,密钥管理面变大
- ❌ permitAll() 不鉴权:生产风险高,只适合 dev

**影响面**:
- `application.yml`:`management.server.port: 9090` + `management.endpoints.web.exposure.include: health,prometheus,info`
- `Dockerfile`:可选 `EXPOSE 9090`(主要是约定文档化)
- `docker-compose.yml`:backend 仅映射 `8080:8080` 到 host;`9090` 不外露
- `docker-compose.yml` healthcheck:从 `curl http://localhost:8080/actuator/health` 改 `curl http://localhost:9090/actuator/health`(容器内访问)
- `.github/workflows/native.yml` 中 `native-smoke.sh`:health 检查地址改 9090;新增 `curl http://localhost:9090/actuator/prometheus | grep http_server_requests` 断言
- IT 测试:`@SpringBootTest` 启动需将 management port 设为随机端口或与 server port 共用(测试 profile 用 `management.server.port: ""`)

### D3 — RequestID 格式:UUID v7

**决策**:`X-Request-Id` header 优先透传(若 header 不为空且符合 UUID 格式),否则生成 UUID v7;写入 MDC `requestId` 字段。

**理由**:
- 时间有序 → 日志按 ID 二分查找区间更快,Loki/ES 索引更友好
- 36 字符标准 UUID,下游所有 UUID 兼容客户端可读
- 比 v4 多 12 字节时间前缀,但仍是 UUID 全集子集
- 后续若接 OTel,可在 traceparent 之外保留 requestId 作为业务级关联键

**实现策略**(OQ-1 待 tasks 阶段定):
- 优先尝试 JDK 25 内置(若 `UUID` 类提供 v7 工厂方法)
- Fallback:`com.fasterxml.uuid:java-uuid-generator`(成熟、native-image 友好、Jackson 同源,~50KB)
- 弃用:`com.github.f4b6a3:uuid-creator`(更新但生态小)

**替代方案**:
- ❌ W3C TraceContext `traceparent`:OTel 标准,但本期不做 traces,引入会让 spec 多记一份"trace 子段提取"逻辑
- ❌ Nano ID:省 44% 体积,但不是 UUID,下游兼容性弱

### D4 — Filter Order 与生命周期

**决策**:`RequestIdFilter` 注册顺序 `Ordered.HIGHEST_PRECEDENCE + 100`,早于:
- `JwtAuthenticationFilter`(401 响应也要有 requestId 写日志)
- `ErrorHandler`(`ErrorResponse` 后续若加 requestId 字段需 MDC 可用)
- 任何业务过滤器

**实现要点**:
- `OncePerRequestFilter` 子类,保证异步派发不重复生成
- `finally` 块清 MDC,避免线程池复用泄漏(虚拟线程仍需清,因为 Spring 实例化方式)
- response header `X-Request-Id` 在 commit 前写入(`HttpServletResponse.setHeader`)
- 异常路径:即使 `chain.doFilter` 抛异常,`finally` 仍保证 MDC 清理 + header 写入

### D5 — 5 个核心业务计数器

**决策**:
| 指标 | 类型 | 标签 | 埋点位置 |
|---|---|---|---|
| `orders.created` | Counter | `paymentMethod`(wechat/cash/transfer) | `OrderApplicationService.createOrder()` 成功路径 |
| `orders.cancelled` | Counter | `reason`(user/timeout/admin) | `OrderApplicationService.cancelOrder()` 成功路径 |
| `orders.paid` | Counter | `paymentMethod`、`amount.bucket`(<100 / 100-500 / 500-2000 / >2000) | `OrderApplicationService.markPaid()` |
| `products.queried` | Counter | `category`(鱼类/虾蟹/贝类/软体/海藻) | `ProductApplicationService.searchProducts()` |
| `users.login.attempts` | Counter | `result`(success/failed/locked) | `UserApplicationService.login()` 全分支 |

**埋点方式**:`MeterRegistry.counter(name, tags).increment()` 直接调用,**不用 `@Timed` / `@Counted` 注解**。

**理由**:
- AOP 注解在 GraalVM Native 下需要 AspectJ runtime + reflect-config,踩坑成本高
- 直接调用代码意图清晰,埋点点 grep 可见
- 跨模块只走 ApplicationService 与项目分层约束一致(`design §1.3`)
- Tags cardinality 受控:`amount.bucket` 分桶避免每个金额产生新 series

**替代方案**:
- ❌ AOP 注解:见上
- ❌ Domain Event Listener 埋点:解耦更好但本期成本高,且 5 个指标只有 2 个模块,直接调清晰
- ❌ Filter 层统一埋点:无法区分业务语义(只能区分 endpoint)

### D6 — Native-image metadata 采集

**决策**:严格遵循 CLAUDE.md `nativeTest` 切片约定,不手写任何 `META-INF/native-image/*.json`。

**实现**:
- 新建 `MetricsEndpointIT`(打 `@Tag("native")`)— 启动 context 后 GET `/actuator/prometheus`,断言响应含 `http_server_requests_seconds`
- 新建 `StructuredLoggingIT`(打 `@Tag("native")`)— 配置 capture appender,触发一个 logger.info,断言输出为 JSON 且含 `requestId` 字段
- 新建 `RequestIdFilterTest`(plain JUnit,不打 native tag)— 单元测 generate / passthrough / MDC cleanup
- `./gradlew nativeTest` 阶段 agent 自动写入 `build/native/agent-output/test/`,人工 review 后 commit 到 `src/main/resources/META-INF/native-image/`

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
|---|---|---|
| **Spring Boot 4 structured logging 在 native-image 下未知坑** | nativeCompile 失败或 JSON 输出残缺 | tasks 阶段第一个 PR 仅做 logging 改造,先 `./gradlew nativeTest && nativeCompile` 验证;若有坑回退到 `logstash-logback-encoder`(已留 OQ-1 fallback) |
| **RSS 预算 200 MB 紧张** | native-smoke.sh 红 | 渐进交付,每个 PR 后跑 RSS 断言;若超标先去掉 1-2 个业务计数器(可在 D5 取舍) |
| **management port 9090 测试隔离复杂** | IT 启动失败或端口冲突 | test profile 用 `management.server.port: -1`(共用 server port)或 `management.server.port: 0`(随机);production profile 用 9090 |
| **docker-compose healthcheck 切端口期间集群启动失败** | backend 永远 unhealthy → 串行启动卡死 | tasks 阶段拆 PR:先改 healthcheck 在端口切换的同一 PR 内 atomic 改;CI 加 docker-compose smoke 验证 |
| **`X-Request-Id` 上游来源不可信** | 攻击者注入伪造 ID 污染日志 | header 校验:必须符合 UUID 正则,否则忽略并新生成;长度上限 64 防 log injection |
| **业务计数器 cardinality 爆炸** | Prometheus 内存炸 | tags 设计阶段约束:无 userId/orderId 等高基数字段;`amount.bucket` 分桶 4 档;`category` 来自 sealed interface 枚举 |
| **MDC 在虚拟线程下泄漏** | requestId 串到无关请求 | `OncePerRequestFilter.finally` 块统一 `MDC.clear()`;单元测试覆盖虚拟线程场景 |
| **JDK 25 无 UUID v7 内置 API** | 需新引一个依赖 | 见 OQ-1;`java-uuid-generator` 是低风险选择 |

## Migration Plan

**PR 拆分**(每 PR 独立可 revert):

1. **PR #1 — `feat(observability): structured logging + RequestIdFilter`**
   - 加 `logging.structured.format.console: logstash`(prod profile)
   - 新建 `shared/observability/RequestIdFilter`
   - 新建 `StructuredLoggingIT`、`RequestIdFilterTest`(打 native tag 的那个)
   - 跑 `./gradlew check && ./gradlew nativeTest`,提交 native metadata
   - **验收**:`docker-compose up` 后日志为 JSON,带 requestId

2. **PR #2 — `feat(observability): metrics endpoint on management port 9090`**
   - 加 `micrometer-registry-prometheus` 依赖
   - `application.yml` 加 `management.server.port: 9090` + `endpoints.exposure.include: health,prometheus,info`
   - 新建 `MetricsEndpointIT`(打 native tag)
   - 改 `docker-compose.yml` healthcheck 到 9090
   - 改 `.github/workflows/native.yml` 的 `native-smoke.sh` health URL
   - 跑 `./gradlew nativeTest`,提交 metadata
   - **验收**:`curl localhost:9090/actuator/prometheus` 返回 Prometheus 格式

3. **PR #3 — `feat(observability): 5 business counters in ApplicationService`**
   - 在 5 个 ApplicationService 方法埋点 MeterRegistry.counter
   - 每个埋点伴随单元测试断言 counter 增量
   - **验收**:跑订单流单元测试后,`MeterRegistry.find("orders.created").counter()` 计数符合预期

4. **PR #4 — `chore(observability): RSS budget verification & docs`**
   - `native-smoke.sh` 加 RSS 断言(< 200 MB)
   - 更新 CLAUDE.md "可观测性"段落
   - 更新 README "运维"段落

**回滚策略**:
- PR #1 / #3 / #4 独立可 revert,无依赖
- PR #2 风险最高(端口拆分)— 合入前在本地 docker-compose 完整跑一遍 + staging 灰度 1 小时
- 若 PR #2 在生产出问题:回退后 PR #3 仍可用(metrics 接口不可达但业务不受影响)

## Open Questions

- **OQ-1**:JDK 25 是否内置 UUID v7 API?(查 `java.util.UUID` 是否有 `timeBased()` / `randomUUIDv7()` 或类似工厂)
  - **如有**:零依赖,直接用
  - **如无**:加 `com.fasterxml.uuid:java-uuid-generator:5.x` 依赖
  - **解决时机**:tasks 阶段第 1 个任务,15 分钟可定
- **OQ-2**:Structured logging schema 选 `logstash` 还是 `ecs`?
  - `logstash`:扁平字段(`@timestamp` / `level` / `thread`),Grafana Loki LogQL 友好
  - `ecs`:Elastic Common Schema(`@timestamp` / `log.level` / `process.thread.name`),与 ELK 系紧密
  - 当前倾向 `logstash`(后续接 Loki 概率更高),但若计划 Phase 2 接 Sentry + ELK,`ecs` 字段更标准
  - **解决时机**:tasks 阶段决定
- **OQ-3**:`orders.paid` 的 `amount.bucket` 分桶策略?
  - 等距:0-100 / 100-200 / 200-300 / ...(buckets 多,易爆炸)
  - 几何:< 100 / 100-500 / 500-2000 / 2000+(4 档,推荐)
  - SLA bucket:对齐业务客单价分布(需先做数据分析)
  - **倾向**:几何分桶 4 档,后续接入 Grafana 再调整
  - **解决时机**:specs 阶段定义指标契约时确定
