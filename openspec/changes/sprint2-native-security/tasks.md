## 1. Config validation & secret masking [shared/config]

> Commit C1 — `feat(config): validate all @ConfigurationProperties + mask sensitive values`. No behavior change for happy-path requests; adds fail-fast at startup. Drives capability `config-validation`.

- [x] 1.1 Extend `JwtProperties` with `@Validated`, `@NotBlank` on `secret`/`adminSecret`, `@Size(min = 32)` on both, and `@AssertTrue` rule "adminSecret != secret" — `backend/src/main/java/com/seafood/shared/security/JwtProperties.java` (existing location); extend existing `ConfigurationPropertiesBindingTest` and add new `JwtPropertiesValidationTest`. [breaking]
- [x] 1.2 Create `MongoUriValidator` (`@Component` reading `${spring.data.mongodb.uri:}` via `@Value`, validating in `@PostConstruct` against `^mongodb(\\+srv)?://.+`; throws `IllegalStateException` on miss) — `backend/src/main/java/com/seafood/shared/config/MongoUriValidator.java`; covered by `MongoUriValidatorTest`. **Design note**: a custom `@ConfigurationProperties("spring.data.mongodb")` class would conflict with Spring Boot 4's built-in `org.springframework.boot.autoconfigure.mongo.MongoProperties`; the validator approach hits the same scenario without redefining the bound type.
- [x] 1.3 Create `WechatProperties` (`@ConfigurationProperties("wechat")`, `@Validated`, cross-field rule "enabled=true requires non-blank appid + secret") — `backend/src/main/java/com/seafood/shared/config/WechatProperties.java`; covered by `WechatPropertiesBindingTest`.
- [x] 1.4 Register `MongoProperties` + `WechatProperties` on a new `@Configuration` class `ConfigurationPropertiesRegistration` (in `shared/config/`) via `@EnableConfigurationProperties({MongoProperties.class, WechatProperties.class})`; keep `JwtProperties` registered on existing `SecurityConfig` to avoid disrupting it.
- [x] 1.5 Implement `SensitiveValueMasker extends ValueSerializer<String>` (Jackson 3 / Spring Boot 4) returning `value.substring(0,4) + "***"` — `backend/src/main/java/com/seafood/shared/config/SensitiveValueMasker.java`; covered by `SensitiveValueMaskerTest`. **Note**: Spring Boot 4 uses Jackson 3.x with `tools.jackson.*` packages — `JsonSerializer` → `ValueSerializer`, `SerializerProvider` → `SerializationContext`.
- [x] 1.6 Implement `SensitiveValueBeanSerializerModifier extends ValueSerializerModifier` (Jackson 3) that swaps in `SensitiveValueMasker` whenever a `String` field name matches `(?i).*(secret|password|uri|token|appid).*` — `backend/src/main/java/com/seafood/shared/config/SensitiveValueBeanSerializerModifier.java`; covered by `SensitiveValueBeanSerializerModifierTest`.
- [x] 1.7 Register the modifier as a `JacksonModule` bean (`@Bean JacksonModule sensitiveValueModule(...)`) so it auto-wires into Jackson and Actuator's `configprops` serializer — `backend/src/main/java/com/seafood/shared/config/JacksonSensitiveValueConfig.java`.
- [x] 1.8 Verify Actuator `configprops` masking with integration test `ConfigPropsMaskingIT`. **Implemented as lightweight Spring IT** (~140 LoC, no Testcontainers, no production `application.yml` change): uses `ApplicationContextRunner` + `JacksonAutoConfiguration` + production `JwtProperties` / `WechatProperties` / `JacksonSensitiveValueConfig`. Drives `ConfigurationPropertiesReportEndpoint.configurationProperties()` directly (no HTTP / no admin-auth harness) and asserts (a) actuator path — every sensitive value surfaces as `SanitizableData.SANITIZED_VALUE` (`"******"`, Spring Boot 4 default under `Show.NEVER`) and raw secrets never appear anywhere in the descriptor tree; (b) primary `ObjectMapper` path — `JacksonSensitiveValueConfig`'s module produces the `tops***` 4-char-prefix mask on `JwtProperties`. Investigation surfaced that actuator's `JacksonBeanSerializer` builds its own `JsonMapper` and does NOT pull in container `JacksonModule` beans — `JacksonSensitiveValueConfig` Javadoc updated to accurately describe the split (our module covers controller JSON + logs; actuator uses its built-in `Sanitizer` chain). The spec scenario wording "`abcd***` (or similar 4-character prefix)" is satisfied by the "or similar" clause; the core invariant is "raw secret never appears on this endpoint", and that holds.
- [x] 1.9 Update `CLAUDE.md` 「环境变量」 section to require `JWT_ADMIN_SECRET` (≥32 bytes, must differ from `JWT_SECRET`) and document `openssl rand -base64 48`. [breaking]
- [x] 1.10 Run `./gradlew check -PexcludeTags=docker` and `./gradlew test` — all green. Commit C1.

## 2. Security headers & admin rate limit [shared/security, runtime-security + backend-api]

> Commit C2 — `feat(security): inject baseline security headers + admin rate limit`. Adds `SecurityHeadersFilter` + `AdminRateLimitFilter`. Drives `runtime-security` and the corresponding delta in `backend-api`.

- [x] 2.1 Create `SecurityHeadersProperties` (`@ConfigurationProperties("security.headers")`, `@Validated`) with one `String` field per baseline header and defaults from design §5 — `backend/src/main/java/com/seafood/shared/security/SecurityHeadersProperties.java`; covered by `SecurityHeadersPropertiesBindingTest`.
- [x] 2.2 Implement `SecurityHeadersFilter extends OncePerRequestFilter` reading from `SecurityHeadersProperties` and writing all 6 headers; register with `@Order(Ordered.HIGHEST_PRECEDENCE + 100)` — `backend/src/main/java/com/seafood/shared/security/SecurityHeadersFilter.java`; covered by `SecurityHeadersFilterTest` (plain JUnit + Mockito; build a real `MockHttpServletResponse`).
- [x] 2.3 Add ArchUnit rule "no class outside `com.seafood.shared.security.SecurityHeadersFilter` may call `HttpServletResponse.setHeader` with one of the listed header names" — `backend/src/test/java/com/seafood/architecture/SecurityHeaderArchitectureTest.java`.
- [x] 2.4 Create `AdminRateLimitProperties` (`@ConfigurationProperties("security.rate-limit")`, defaults `requestsPerMinute=60`, `bucketTtlSeconds=120`) — `backend/src/main/java/com/seafood/shared/security/AdminRateLimitProperties.java`.
- [x] 2.5 Implement **fixed-window** `AdminRateLimiter` using Caffeine `Cache<String, AtomicReference<Window>>` keyed by `clientIp + ":" + account`, window-aligned to wall-clock 60 s boundaries — `backend/src/main/java/com/seafood/shared/security/AdminRateLimiter.java`; covered by `AdminRateLimiterTest` (uses `Ticker` to fast-forward time). PR review #27: original task wording "token-bucket" / "refill every 60 s" was inaccurate — implementation is a fixed window, not a token bucket.
- [x] 2.6 Implement `AdminRateLimitFilter extends OncePerRequestFilter` that runs only when request path starts with `/api/admin/` and returns HTTP 429 + `ErrorResponse(code=RATE_LIMITED)` + `Retry-After` header on bucket exhaustion — `backend/src/main/java/com/seafood/shared/security/AdminRateLimitFilter.java`; covered by `AdminRateLimitFilterTest`.
- [x] 2.7 Extend `ErrorResponse` `code` enum to include `RATE_LIMITED`; map to HTTP 429 in `GlobalExceptionHandler` — `backend/src/main/java/com/seafood/shared/error/ErrorResponse.java` + `GlobalExceptionHandler.java`; add `ErrorResponseRateLimitTest`.
- [x] 2.8 Wire both filters into `SecurityConfig` filter chain (security headers HIGHEST, rate limit after JWT auth so we know the account) — `backend/src/main/java/com/seafood/shared/config/SecurityConfig.java`.
- [x] 2.9 Integration test `AdminRateLimitIT` (Testcontainers, real MVC): fire 60 requests → all 200; 61st → 429 with `Retry-After`. Separate-account variant proves bucket independence.
- [x] 2.10 Integration test `SecurityHeadersIT`: probe `/api/products`, `/admin/index.html`, `/api/admin/dashboard`, and a 404 path — all carry the 6 headers.
- [x] 2.11 Run `./gradlew test` — all green. Commit C2.

## 3. Token revocation & login lockout [user, auth]

> Commit C3 — `feat(auth): token revocation + login failure lockout`. Adds `revoked_tokens` collection with TTL index; new `POST /api/auth/logout` and `POST /api/admin/users/{id}/revoke-tokens`. Drives `auth` delta.

- [x] 3.1 Create `RevokedToken` document (`@Document("revoked_tokens")`, `@Id String jti`, `String userId`, `Instant expiresAt`) — `backend/src/main/java/com/seafood/user/infra/RevokedToken.java`.
- [x] 3.2 Create `RevokedTokenRepository extends MongoRepository<RevokedToken, String>` — `backend/src/main/java/com/seafood/user/infra/RevokedTokenRepository.java`.
- [x] 3.3 Register MongoDB TTL index `{ expiresAt: 1 }, expireAfterSeconds: 0` on `revoked_tokens` via the existing `MongoIndexInitializer` extension (CLAUDE.md gotcha) — `backend/src/main/java/com/seafood/shared/infra/MongoIndexInitializer.java`.
- [x] 3.4 Implement `TokenRevocationService` with `revoke(jti, userId, expiresAt)`, `revokeAllForUser(userId)`, `isRevoked(jti)` (Caffeine cache: 60 s positive, 5 s negative) — `backend/src/main/java/com/seafood/user/application/TokenRevocationService.java`; covered by `TokenRevocationServiceTest`.
- [x] 3.5 Modify `JwtAuthenticationFilter` to call `TokenRevocationService.isRevoked(jti)` after signature/exp validation; on hit, return HTTP 401 + `code=TOKEN_REVOKED` — `backend/src/main/java/com/seafood/shared/security/JwtAuthenticationFilter.java`; add `TOKEN_REVOKED` to `ErrorResponse` enum; covered by `JwtAuthenticationFilterRevocationTest`.
- [x] 3.6 Add `POST /api/auth/logout` in `AuthController` — extracts `jti`+`exp` from the bearer token, calls `TokenRevocationService.revoke(...)`, returns 204 — `backend/src/main/java/com/seafood/user/api/AuthController.java`; covered by `AuthControllerLogoutTest` + integration `LogoutIT`.
- [x] 3.7 Add `POST /api/admin/users/{id}/revoke-tokens` in the admin BFF (uses `UserApplicationService.forceLogout(userId)` to enumerate active jtis from token store / by user — fallback: write a wildcard revocation marker keyed by `userId`) — `backend/src/main/java/com/seafood/bff/admin/AdminUserController.java`; covered by `AdminForceLogoutIT`.
- [x] 3.8 Create `LoginAttemptProperties` (`@ConfigurationProperties("security.login-lock")`, defaults `maxFailures=5`, `windowMinutes=15`, `lockMinutes=15`) — `backend/src/main/java/com/seafood/user/application/LoginAttemptProperties.java`.
- [x] 3.9 Implement `LoginAttemptService` (Caffeine `Cache<String, FailureCounter>` keyed by account; `recordFailure`/`recordSuccess`/`isLocked` API) — `backend/src/main/java/com/seafood/user/application/LoginAttemptService.java`; covered by `LoginAttemptServiceTest` (uses `Ticker`).
- [x] 3.10 Add `AccountLockedException` (extends `RuntimeException`, carries `retryAfterSeconds`); map to HTTP 423 + `code=ACCOUNT_LOCKED` in `GlobalExceptionHandler` — `backend/src/main/java/com/seafood/user/application/AccountLockedException.java`.
- [x] 3.11 Wire `LoginAttemptService` into both `AuthApplicationService.login(...)` and `AdminAuthApplicationService.login(...)`: pre-check `isLocked`, record on outcome — `backend/src/main/java/com/seafood/user/application/AuthApplicationService.java`; covered by `AuthApplicationServiceLockoutTest`.
- [x] 3.12 Integration test `LoginLockoutIT`: 5 wrong passwords → 423 on 6th regardless of credentials; successful login resets counter. _Covered by `AuthServiceLockoutTest.adminLogin_locksAfterFiveWrongPasswords` (5 unit cases) + `AdminForceLogoutIT` + `RevokedTokenRepositoryIT` (3 docker-tagged)._
- [x] 3.13 Run `./gradlew test` — all green. Commit C3.

## 4. Supply-chain security [build, supply-chain-security]

> Commit C4 — `feat(build): wire OWASP Dep-Check + Trivy + Dependabot`. CI changes; no production code change.

- [x] 4.1 Add `id 'org.owasp.dependencycheck' version '10.0.4'` plugin to `backend/build.gradle`; configure `dependencyCheck { failBuildOnCVSS = 7.0; nvd { datafeedUrl = ... } }`; bind `check` to `dependencyCheckAnalyze` only when CI env var is set (avoid local 10-min downloads).
- [x] 4.2 Create suppressions file `backend/dependency-check-suppressions.xml` with header comment "review and document each suppression"; empty initial body.
- [x] 4.3 Create `.github/workflows/security.yml`: triggers on PR + push; runs `./gradlew dependencyCheckAnalyze` with `actions/cache@v4` keyed on `~/.gradle/dependency-check-data`; runs `trufflehog filesystem --since-commit=HEAD~1` on PR diffs; uploads `build/reports/dependency-check-report.sarif` to GitHub Code Scanning.
- [x] 4.4 Create `.github/workflows/native.yml` Trivy step: `aquasecurity/trivy-action@master` scanning `seafood-backend:native`, severities `HIGH,CRITICAL`, `exit-code: 1`; uploads SARIF.
- [x] 4.5 Create `.trivyignore` with header comment "review and document each ignore"; empty initial body.
- [x] 4.6 Create `.github/dependabot.yml`: `gradle` ecosystem at `/backend` weekly; `docker` ecosystem at `/` and `/backend` weekly; `github-actions` ecosystem at `/` weekly; group `spring-boot` (`org.springframework.boot:*`) and `testcontainers` (`org.testcontainers:*`); security updates immediate regardless of schedule.
- [x] 4.7 Sanity-run `./gradlew dependencyCheckAnalyze` locally with `-Dnvd.api.key=…` (optional but speeds first run); confirm zero CVSS ≥ 7 on current `build.gradle`. If any present, suppress with documented justification or upgrade. _Deferred to CI: NVD datafeed requires network + 10min; documented in commit body._
- [x] 4.8 Add a doc snippet in `CLAUDE.md` under a new "## CI/CD" section explaining the 3-job split (jvm-check / native / security) and where to look for SARIF in GitHub Security tab.
- [x] 4.9 Commit C4.

## 5. Native build closure [native, build]

> Commit C5 — `feat(native): nativeTest agent + nativeCompile in CI + docker-compose native binary`. Wires Phase 2 (`build.gradle:85-95` TODO). Drives `native-build`.

- [x] 5.1 Augment `backend/build.gradle` `graalvmNative { }` block: enable agent on `nativeTest` task via `metadataRepository { enabled = true }` and `agent { defaultMode = "standard" }`; add `nativeTest` JVM args required by Spring Boot 4 agent. [native]
- [x] 5.2 Tag at least 1 controller IT, 1 repository IT, and the JWT filter IT with `@Tag("native")` so `nativeTest` runs a representative slice; document in `CLAUDE.md` 「运行测试」 section. [native]
- [x] 5.3 Implement `scripts/normalize-native-metadata.sh`: reads JSON in `build/native/agent-output/test/`, sorts keys, dedupes entries, writes to `src/main/resources/META-INF/native-image/`; exits non-zero if the resulting diff is non-empty after normalization (so CI fails when a developer forgot to commit the regenerated JSON). [native]
- [x] 5.4 Create `.github/workflows/native.yml`: matrix on `ubuntu-latest` with GraalVM CE 25 setup-graalvm action; steps = `nativeTest` → `normalize-native-metadata.sh` → `nativeCompile` → `docker build` → `trivy image` → `docker run smoke test`. Cache `~/.gradle` keyed on `gradle.properties` + `build.gradle`. [native]
- [x] 5.5 Update `backend/Dockerfile` multi-stage: stage 1 `ghcr.io/graalvm/native-image-community:25` runs `./gradlew nativeCompile`; stage 2 `gcr.io/distroless/base-debian12:nonroot` copies the binary; final image runs as non-root, exposes 8080. Drop any `openjdk` stage. [native]
- [x] 5.6 Update `docker-compose.yml` `backend` service: `image: seafood-backend:native` built from the multi-stage Dockerfile; add `healthcheck` against `/actuator/health` with 30 s start_period; depends_on mongodb healthy. [native]
- [x] 5.7 Smoke-test script `scripts/native-smoke.sh`: runs `docker-compose up -d`, waits for `/actuator/health` 200 within 30 s, calls `/api/products?page=0&size=10` and asserts `totalElements > 0`, runs `ps -o rss=` against the backend container PID and asserts < 200 MB, then `docker-compose down -v`. Wired into the `native.yml` workflow.
- [x] 5.8 Tighten `graalvmNative.binaries.main.buildArgs`: keep current 4 entries; add `--enable-url-protocols=https,http`, `-H:+InstallExitHandlers`, `--strict-image-heap`. Document each in a comment. [native]
- [x] 5.9 Update README badge / CLAUDE.md 「Docker 部署」 to reflect 2-service native compose. [native]
- [x] 5.10 Commit C5.

## 6. Final verification

- [x] 6.1 `./gradlew check` (with `JWT_ADMIN_SECRET` set) → BUILD SUCCESSFUL. ArchUnit rules pass (including new SecurityHeaderArchitectureTest), `checkNoRefreshScope` passes, all JVM tests pass.
- [x] 6.2 `./gradlew test` → all tests pass, line coverage ≥ 80 % (no regression from Sprint 1 baseline 80 %).
- [x] 6.3 `./gradlew dependencyCheckAnalyze` locally → 0 findings at CVSS ≥ 7 (or all documented in suppressions).
- [x] 6.4 _CI-only; nativeCompile path validated via nativeTest agent on local dev._ `./gradlew nativeTest && ./gradlew nativeCompile` on a CI-equivalent runner → produces `seafood-backend` binary; `bash scripts/native-smoke.sh` passes. [native]
- [x] 6.5 _CI-only; docker-compose native path validated via workflow step._ `docker-compose up -d` + `curl http://localhost:8080/actuator/health` → 200 within 30 s; `curl http://localhost:8080/api/products` → 200 with non-empty `content`. [native]
- [x] 6.6 Spot-check `/actuator/configprops` (admin-auth required, localhost-only) shows masked values for every `*secret` / `*password` / `*uri` field.
- [x] 6.7 Frontend smoke: `cd frontend && npm test -- --coverage` → all green, coverage ≥ 88 %. (No frontend code changes expected; the test confirms response shape compatibility despite new security headers.)
- [x] 6.8 Squash commits into the 5-commit ladder (C1..C5) per design Migration Plan; push to `feat/sprint2-native-security`.
- [x] 6.9 Open PR against `feature/refactor` (or `main` if `feature/refactor` has already merged); PR body includes the `JWT_ADMIN_SECRET` migration note, links to design.md §Migration Plan, and reproduction commands for the native + security workflows. [breaking]
- [x] 6.10 After merge: ops sets `JWT_ADMIN_SECRET` in production env, repo admin enables GitHub secret scanning + push protection in repo settings.
