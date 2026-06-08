## ADDED Requirements

### Requirement: JSON output in production profile
When the active Spring profile is `prod` (or any profile that activates `logging.structured.format.console=logstash`), the application SHALL emit each log event as a single-line JSON object on stdout, conforming to the Logstash schema (`@timestamp`, `@version`, `message`, `logger_name`, `thread_name`, `level`, `level_value`, plus MDC fields as top-level keys).

#### Scenario: Production log line is single-line JSON
- **WHEN** the application is started with `SPRING_PROFILES_ACTIVE=prod`
- **AND** any logger emits an INFO event with message `"order created"`
- **THEN** the corresponding stdout line MUST be valid JSON parseable by `Jackson.readTree`
- **AND** MUST contain exactly one newline character (`\n`) at the end of the line
- **AND** MUST contain a top-level `message` field equal to `"order created"`
- **AND** MUST contain a top-level `level` field equal to `"INFO"`
- **AND** MUST contain a top-level `@timestamp` field parseable as ISO-8601

#### Scenario: Stack traces serialize as a single field
- **WHEN** a logger emits an ERROR event with a stack trace
- **THEN** the log line MUST still be a single line of JSON
- **AND** the stack trace MUST appear in a single string field (e.g., `stack_trace`), with embedded newlines escaped (`\n`)

### Requirement: Console pattern in development profile
When the active Spring profile is `dev` (or no profile is set), the application SHALL emit human-readable pattern-formatted logs that include the `requestId` MDC field. A `LOG_FORMAT=json` environment variable override SHALL switch dev output to JSON without code change.

#### Scenario: Dev profile preserves readable pattern
- **WHEN** the application is started with `SPRING_PROFILES_ACTIVE=dev`
- **AND** a logger emits INFO `"order created"`
- **THEN** the stdout line MUST match a regex similar to `\d{2}:\d{2}:\d{2}\.\d{3} INFO \[[0-9a-f-]{36}\] .* - order created`

#### Scenario: LOG_FORMAT env overrides dev format
- **WHEN** the application is started with `SPRING_PROFILES_ACTIVE=dev` and `LOG_FORMAT=json`
- **THEN** stdout MUST emit JSON (same shape as the production profile scenario above)

### Requirement: Request identifier MDC field
Every log event emitted while a HTTP request is being processed SHALL include the `requestId` MDC field. The value SHALL be the same identifier that appears in the response `X-Request-Id` header for the same request.

#### Scenario: Log emitted inside a request includes requestId
- **WHEN** a request arrives at `GET /api/products` with `X-Request-Id: 01931a45-7c80-7000-9b3e-3f8a1c5e4d20`
- **AND** the controller emits a log event during processing
- **THEN** the JSON log line MUST contain `"requestId":"01931a45-7c80-7000-9b3e-3f8a1c5e4d20"`
- **AND** the HTTP response MUST contain header `X-Request-Id: 01931a45-7c80-7000-9b3e-3f8a1c5e4d20`

#### Scenario: Log emitted outside a request omits requestId
- **WHEN** a scheduled task or startup hook emits a log event with no active HTTP request
- **THEN** the JSON log line MUST NOT contain a `requestId` field, OR MUST contain an explicit `requestId: null`
- **AND** MUST NOT contain a leaked value from a previously-processed request

### Requirement: Request identifier passthrough and generation
For every inbound HTTP request, the system SHALL determine the request identifier as follows:
1. If the request contains an `X-Request-Id` header AND the header value matches the standard UUID format (`/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i`) AND its length is ≤ 64 characters, the value SHALL be used verbatim.
2. Otherwise, a new UUID v7 SHALL be generated.
The resulting value SHALL be set on the response `X-Request-Id` header before the response is committed.

#### Scenario: Valid inbound X-Request-Id is preserved
- **WHEN** an inbound request carries `X-Request-Id: 01931a45-7c80-7000-9b3e-3f8a1c5e4d20`
- **THEN** the response MUST contain the same `X-Request-Id` value

#### Scenario: Missing X-Request-Id triggers generation
- **WHEN** an inbound request carries no `X-Request-Id` header
- **THEN** the response MUST contain an `X-Request-Id` header
- **AND** its value MUST match the UUID regex above
- **AND** parsing the value as UUID and reading version field MUST return `7`

#### Scenario: Malformed X-Request-Id is replaced
- **WHEN** an inbound request carries `X-Request-Id: "<script>alert(1)</script>"`
- **THEN** the malformed value MUST NOT appear in any log line or response header
- **AND** the response `X-Request-Id` MUST be a freshly generated UUID v7
- **AND** the application MUST log a single WARN event noting `X-Request-Id rejected` without echoing the malformed value verbatim into the message field

#### Scenario: Oversized X-Request-Id is rejected
- **WHEN** an inbound request carries `X-Request-Id` with a value longer than `64` characters
- **THEN** the value MUST be rejected and a fresh UUID v7 MUST be used

### Requirement: MDC lifecycle isolation
The system SHALL guarantee that MDC values set during a request are cleared before the worker thread is returned to the pool (or before the virtual thread terminates). MDC values from a previous request MUST NOT be observable in a subsequent unrelated request handled by the same thread.

#### Scenario: MDC is empty after request completes
- **WHEN** a request completes normally on a given thread
- **THEN** `MDC.get("requestId")` invoked on that thread immediately after the request MUST return `null`

#### Scenario: MDC is cleared on exception path
- **WHEN** the controller throws an exception that propagates beyond the `RequestIdFilter`
- **THEN** the `X-Request-Id` response header MUST still be written
- **AND** `MDC.get("requestId")` on that thread after the exception MUST return `null`

#### Scenario: Two consecutive requests on the same virtual thread are isolated
- **WHEN** two requests are processed sequentially on the same virtual thread, the first carrying `X-Request-Id: 0193...4d20` and the second arriving without a header
- **THEN** the second request's response `X-Request-Id` MUST differ from the first
- **AND** the second request's log lines MUST NOT contain the first request's `requestId`

### Requirement: Filter ordering
The `RequestIdFilter` SHALL execute before any other filter that may emit log events or generate HTTP responses, including `JwtAuthenticationFilter`, exception-mapping handlers, and any rate-limiting filters. Concretely, its order SHALL be `Ordered.HIGHEST_PRECEDENCE + 100` (lower than the framework's outermost cross-cutting filters but higher than all application filters).

#### Scenario: Unauthenticated request still gets requestId
- **WHEN** a request arrives without a valid JWT and is rejected with `401`
- **THEN** the `401` response MUST contain an `X-Request-Id` header
- **AND** the `JwtAuthenticationFilter` rejection log line MUST contain a `requestId` field

#### Scenario: 500 error path preserves requestId
- **WHEN** a downstream component throws an unhandled exception and the global error handler converts it to a `500` response
- **THEN** the `500` response MUST contain an `X-Request-Id` header
- **AND** the error log entry MUST contain a `requestId` field that matches the response header value

### Requirement: Native compilation compatibility
Structured logging configuration, JSON serialization, and `RequestIdFilter` SHALL compile and run under GraalVM Native. The Spring Boot 4 native-image hints for the configured `logging.structured.format.console` value MUST be sufficient — no hand-written `reflect-config.json` entries for Logback or Jackson are permitted; any required metadata SHALL be collected by the `nativeTest` agent.

#### Scenario: nativeTest validates JSON output
- **WHEN** `./gradlew nativeTest` runs the integration test `StructuredLoggingIT` (annotated with `@Tag("native")`)
- **THEN** the test MUST start the application with `prod` profile active and invoke a logger
- **AND** MUST assert that the captured stdout line parses as JSON with at least the fields `@timestamp`, `level`, `message`, `requestId`

#### Scenario: nativeCompile succeeds
- **WHEN** `./gradlew nativeCompile` runs after committing the metadata collected above
- **THEN** the build MUST succeed without warnings about missing reflection metadata for `ch.qos.logback.*`, `org.springframework.boot.logging.structured.*`, or `com.fasterxml.jackson.*`

### Requirement: Performance budget
The combined overhead of `RequestIdFilter` + JSON encoding SHALL add no more than `2 ms` median latency to a baseline `GET /api/products` request, measured under the load profile used by `native-smoke.sh`.

#### Scenario: Latency overhead is bounded
- **WHEN** `native-smoke.sh` measures `p50` latency for `GET /api/products` before and after this change
- **THEN** the post-change `p50` MUST be no more than `2 ms` higher than the pre-change baseline
- **AND** post-change `p99` MUST remain under the project-wide budget of `500 ms`
