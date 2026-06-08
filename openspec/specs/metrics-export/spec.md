# metrics-export

## Purpose

通过 `/actuator/prometheus` 暴露 Prometheus 文本格式指标(HTTP / JVM / Mongo / 自定义业务计数器),含端点鉴权与 native-image 兼容性要求。

## Requirements

### Requirement: Prometheus exposition endpoint
The system SHALL expose a Prometheus text-format exposition endpoint at the absolute path `/actuator/prometheus`, encoded as `text/plain;version=0.0.4;charset=utf-8`, returning the current snapshot of all registered metrics on every request.

#### Scenario: Endpoint returns Prometheus exposition format
- **WHEN** an authenticated client issues `GET /actuator/prometheus` against the management port
- **THEN** the response status MUST be `200`
- **AND** the `Content-Type` header MUST start with `text/plain` and contain `version=0.0.4`
- **AND** the response body MUST contain at least one `# TYPE` declaration and one `# HELP` line

#### Scenario: Endpoint is absent from business port
- **WHEN** a client issues `GET /actuator/prometheus` against the business port (`server.port`, default `8080`)
- **THEN** the response MUST be `404` (no `/actuator/**` route is registered on the business port)

### Requirement: Management port isolation
The system SHALL bind all `/actuator/**` endpoints to a dedicated management port (`management.server.port`), distinct from the business port. The default management port SHALL be `9090`, bound to `0.0.0.0` inside the container. The business port MUST NOT expose any actuator routes.

#### Scenario: Management port serves health and prometheus
- **WHEN** the application is started with default configuration
- **THEN** `GET http://localhost:9090/actuator/health` MUST return `200`
- **AND** `GET http://localhost:9090/actuator/prometheus` MUST return `200`
- **AND** `GET http://localhost:9090/api/products` MUST return `404` (business routes not on management port)

#### Scenario: Business port rejects actuator paths
- **WHEN** the application is started with default configuration
- **AND** a client issues `GET http://localhost:8080/actuator/health`
- **THEN** the response MUST be `404`

#### Scenario: Test profile shares ports
- **WHEN** an integration test annotates `@SpringBootTest` with property `management.server.port=`
- **THEN** the actuator endpoints MUST be served on the random test port alongside business routes (to avoid port conflicts in parallel test execution)

### Requirement: Network exposure boundary
The Docker container SHALL NOT map the management port (`9090`) to the host. Only the business port (`8080`) is published outside the Docker network. Prometheus or other scrapers running inside the Docker network MAY reach `backend:9090`.

#### Scenario: docker-compose does not publish management port
- **WHEN** `docker-compose.yml` is inspected
- **THEN** the `backend.ports` mapping MUST contain `8080:8080`
- **AND** the `backend.ports` mapping MUST NOT contain any entry matching `9090`

#### Scenario: Container internal access works
- **WHEN** another container in the same Docker network executes `curl http://backend:9090/actuator/health`
- **THEN** the response MUST be `200`

### Requirement: Default technical metrics
The system SHALL register the following Micrometer meter families on startup, with names matching the Spring Boot 4 / Micrometer defaults:
- `http.server.requests` (Timer) — per-request latency with `uri`, `method`, `status`, `outcome` tags
- `jvm.memory.used` / `jvm.memory.committed` / `jvm.memory.max` (Gauge) — for `heap` and `nonheap` areas (or native equivalents under GraalVM Native)
- `process.cpu.usage` / `system.cpu.usage` (Gauge)
- `mongodb.driver.commands` (Timer) — per Mongo command, tagged with `command` and `collection`
- `tomcat.threads.busy` / `tomcat.threads.config.max` (Gauge) — or virtual-thread equivalent for the chosen connector

#### Scenario: HTTP request meter appears after first request
- **WHEN** the application has served at least one `GET /api/products` request
- **THEN** the body of `GET /actuator/prometheus` MUST contain a sample line matching `http_server_requests_seconds_count{...method="GET",uri="/api/products",...} <number>`

#### Scenario: Mongo command meter appears after first repository call
- **WHEN** the application has executed at least one Mongo `find` against the `products` collection
- **THEN** the body of `GET /actuator/prometheus` MUST contain a sample line matching `mongodb_driver_commands_seconds_count{...command="find",collection="products",...}`

### Requirement: Business counters
The system SHALL register the following business counters and increment them at the corresponding `ApplicationService` boundary. Each counter MUST be incremented exactly once per successful domain operation (failures MUST NOT increment success counters).

| Counter name | Type | Tags | Increment site |
|---|---|---|---|
| `orders.created` | Counter | `paymentMethod` ∈ {`wechat`,`cash`,`transfer`} | `OrderApplicationService.createOrder()` after successful persistence |
| `orders.cancelled` | Counter | `reason` ∈ {`user`,`timeout`,`admin`} | `OrderApplicationService.cancelOrder()` after successful state transition |
| `orders.paid` | Counter | `paymentMethod`, `amountBucket` ∈ {`lt100`,`100to500`,`500to2000`,`gte2000`} | `OrderApplicationService.markPaid()` after successful state transition |
| `products.queried` | Counter | `category` ∈ {`鱼类`,`虾蟹`,`贝类`,`软体`,`海藻`} | `ProductApplicationService.searchProducts()` per filter category (multi-category search increments once per matched category) |
| `users.login.attempts` | Counter | `result` ∈ {`success`,`failed`,`locked`} | `UserApplicationService.login()` on every terminal outcome |

Application name `seafood-backend` MUST be applied as a common tag (`application=seafood-backend`) to all metrics via `MeterRegistryCustomizer`.

#### Scenario: orders.created counter increments on successful order
- **WHEN** `OrderApplicationService.createOrder()` returns successfully with `paymentMethod=wechat`
- **THEN** `meterRegistry.counter("orders.created", "paymentMethod", "wechat").count()` MUST increase by exactly `1`
- **AND** the increment MUST NOT happen if the method throws

#### Scenario: orders.paid amountBucket bucketing
- **WHEN** `OrderApplicationService.markPaid()` succeeds with order total `350.00` and `paymentMethod=wechat`
- **THEN** `meterRegistry.counter("orders.paid", "paymentMethod", "wechat", "amountBucket", "100to500").count()` MUST increase by exactly `1`

#### Scenario: users.login.attempts captures all branches
- **WHEN** `UserApplicationService.login()` returns success, throws `BadCredentialsException`, and returns locked across three calls
- **THEN** counters with `result` tags `success`, `failed`, and `locked` MUST each have count `1`

### Requirement: Tag cardinality constraints
The system MUST NOT include high-cardinality fields (user IDs, order IDs, product IDs, raw amounts, free-text search terms) as Micrometer meter tags. Per-meter tag values SHALL come from one of:
- A `sealed interface` enum
- A bucketing function with ≤8 buckets
- Spring's auto-derived URI template (already bounded by route definitions)
- An HTTP status code or method name (bounded sets)

#### Scenario: amount is bucketed, not raw
- **WHEN** code is reviewed for `orders.paid` counter registration
- **THEN** the registration MUST NOT include a tag whose value is the raw amount or any field with cardinality > 8

#### Scenario: ArchUnit forbids userId tag
- **WHEN** the `ArchitectureTest` suite runs
- **THEN** a rule MUST fail any call to `MeterRegistry.counter`, `MeterRegistry.timer`, or `MeterRegistry.gauge` that passes a tag named `userId`, `orderId`, `productId`, or `email`

### Requirement: Native compilation compatibility
All metric registration, exposition endpoint serialization, and Spring Boot actuator wiring SHALL compile and run under GraalVM Native (`./gradlew nativeCompile`). Native-image metadata SHALL be obtained via the `nativeTest` GraalVM tracing agent on a dedicated integration test, not hand-written.

#### Scenario: nativeTest agent collects metadata
- **WHEN** `./gradlew nativeTest` runs the integration test `MetricsEndpointIT` (annotated with `@Tag("native")`)
- **THEN** the test MUST start the application, issue `GET /actuator/prometheus`, and assert response body contains `http_server_requests_seconds`
- **AND** files under `build/native/agent-output/test/` MUST contain reflection / resource entries covering Micrometer's Prometheus exposition writer

#### Scenario: nativeCompile produces working binary
- **WHEN** `./gradlew nativeCompile` runs after committing the metadata collected above
- **THEN** the build MUST succeed without warnings about missing reflection metadata for `io.micrometer.prometheusmetrics.*` or `io.micrometer.core.instrument.*`

#### Scenario: native-smoke.sh asserts the endpoint
- **WHEN** `.github/workflows/native.yml` invokes `backend/scripts/native-smoke.sh` against the running `seafood-backend:native` container
- **THEN** the script MUST execute `curl http://localhost:9090/actuator/prometheus | grep http_server_requests_seconds_count` and exit non-zero if the grep finds no match

### Requirement: Resource budget preservation
After enabling metrics, the application's resident set size (RSS) SHALL remain below `200 MB` under the smoke-test workload defined by `backend/scripts/native-smoke.sh`. A reserve of `+15 MB` is permitted relative to the pre-change baseline.

#### Scenario: native-smoke asserts RSS
- **WHEN** `native-smoke.sh` measures the container RSS after warmup
- **THEN** the measured RSS MUST be strictly less than `200 MB`
- **AND** the script MUST fail the build if the threshold is exceeded
