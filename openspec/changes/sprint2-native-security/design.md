## Context

Sprint 1 left the test foundation in place but explicitly deferred four production-readiness gaps to Sprint 2/3 (see archived `2026-06-05-sprint1-test-foundation` Non-Goals). The single Spring Boot 4.0.6 module now passes 78 tests against MongoDB via Testcontainers, but:

- `nativeCompile` has only been attempted manually; CI never runs it. `build.gradle:85-95` comments explicitly state this is Phase 1 ("buildArgs 是最小安全集"); Phase 2 (`nativeTest` agent + META-INF sync) was deferred.
- No supply-chain scanning exists. Neither OWASP Dependency-Check, Trivy, nor secret scanning is wired. GitHub Advanced Security is not enabled on the repo.
- Runtime security is limited to "JWT signs and verifies." There is no token revocation, no security response headers, no rate limit on admin endpoints, no login-failure lockout.
- Only one `@ConfigurationProperties` class (`JwtProperties`) is binding-tested. `MONGODB_URI`, `WECHAT_*`, and any future config classes can fail silently at first request rather than at startup.

Stakeholders: backend engineering (own everything below), DevOps (owns docker-compose / CI runners), Security (sign-off on header set, scan thresholds, token revocation model). All four work streams ship in one change because Sprint 1's Non-Goals listed them together and the same `shared/security/` + `shared/config/` packages are touched by 3 of 4 streams — separating into 4 changes would multiply review overhead with no rollback benefit.

## Goals / Non-Goals

**Goals:**

- `./gradlew nativeCompile` produces `seafood-backend` in CI on every PR against `feature/refactor`/`main`, and `docker-compose up -d` runs the native binary plus MongoDB on a fresh checkout.
- OWASP Dep-Check (CVSS ≥ 7 fail) + Trivy image scan (HIGH/CRITICAL fail) + GitHub secret scanning + Dependabot all running on every PR or push.
- One `SecurityHeadersFilter` injects the 6-header baseline into every HTTP response. One `AdminRateLimitFilter` enforces 60 rpm on `/api/admin/**`. One `LoginAttemptService` locks accounts after 5 consecutive failures in 15 min. One `TokenRevocationService` backed by a MongoDB TTL collection lets logout and admin force-logout work.
- Every `@ConfigurationProperties` class is `@Validated` with JSR-303; `JWT_ADMIN_SECRET` must be set AND differ from `JWT_SECRET`; secrets in `/actuator/configprops` and logs are masked to 4-char prefix.
- All net-new functionality covered by tests; backend line coverage remains ≥80%.

**Non-Goals:**

- mTLS / TLS termination — handled by infrastructure layer (load balancer), out of backend scope.
- PIT mutation testing, OpenAPI Diff, full-fleet PII/audit logging — Sprint 3.
- Replacing JWT with opaque tokens or moving session storage to Redis — keep JWT + MongoDB revocation list to bound this change.
- WAF rules, IP allowlists for admin — operational concern, deferred.
- Frontend admin-ui changes — current admin-ui is being rebuilt under §9 (React 18). Keep current Vue admin-ui working; do NOT redesign it as part of this change.

## Decisions

### 1. Native build pipeline — `nativeTest` agent, not hand-written JSON

- **Decision**: Run `./gradlew nativeTest` in CI with the `org.graalvm.buildtools.native` plugin's agent mode enabled (`-Pagent` or the plugin's `metadataRepository`). Collect output JSON into `backend/build/native/agent-output/test/` and commit the diff against `backend/src/main/resources/META-INF/native-image/` as part of the PR that introduces the new code.
- **Rationale**: CLAUDE.md "单仓常见坑" explicitly warns `bson 5.6 + GraalVM Native` reflection breaks under `--no-fallback` and the only working approach is the tracing agent. Hand-editing JSON for jjwt + Spring Security CGLIB + Jackson is brittle; the agent captures the actual access pattern of our tests.
- **Alternative considered**: GraalVM Reachability Metadata Repository only (no agent). Rejected — the repo covers Spring Boot 4 and Jackson well but does NOT cover our DTO records or jjwt-impl 0.12.6's runtime keys.
- **Trade-off**: New native code paths exercised only by manual testing won't be in `META-INF` until they're also covered by an automated test. This forces test coverage on the native critical path, which we accept as a forcing function.

### 2. CI workflow split — three jobs, parallel where independent

- `jvm-check` (existing, fast): `./gradlew check -PexcludeTags=docker` — JVM unit, ArchUnit, JsonTest, RefreshScope scan. < 2 min.
- `integration` (existing-with-Docker): `./gradlew test` — adds Testcontainers MongoDB IT. < 5 min.
- `native` (NEW): `./gradlew nativeTest nativeCompile` then `trivy image ...`. ~15-20 min. Runs only on PRs touching `backend/` or on `main` push (path filter).
- `security` (NEW): `./gradlew dependencyCheckAnalyze` + `trufflehog` PR-diff scan. ~3 min (warm NVD cache).
- **Rationale**: Native is too slow to gate every PR; path filter keeps the fast loop fast. Security is independent and runs in parallel.

### 3. Token revocation — MongoDB TTL collection, not Redis

- **Decision**: New collection `revoked_tokens` with documents `{ _id: jti, userId, expiresAt }` and a TTL index `{ expiresAt: 1 }, expireAfterSeconds: 0`. The JWT filter does one indexed lookup per authenticated request.
- **Rationale**: We already have MongoDB; adding Redis doubles the operational surface for one feature. TTL index removes the cleanup job. With Caffeine in front (60-second positive + 5-second negative cache), the lookup cost is amortized to 0–1 Mongo round-trip per minute per active token.
- **Alternative considered**: Stateless JWT denylist via short-lived access tokens + refresh rotation only. Rejected — refresh rotation already exists; admin-driven "kick this user out NOW" is a hard requirement that statelessness cannot meet.
- **Cross-module impact**: New `shared/security/TokenRevocationService` is called by `JwtAuthenticationFilter` (read) and by `user.application.UserApplicationService.forceLogout(userId)` (write). Cross-module call is via ApplicationService, per design §1.3.

### 4. Rate limit — Caffeine in-memory token bucket, scoped to `/api/admin/**`

- **Decision**: 60 rpm per `(clientIp, account)` tuple using `caffeine` with token-bucket semantics (`Bucket4j` rejected — adds a dependency for a single use site we control).
- **Rationale**: Admin endpoints are low-volume (≤10 admin users × ≤1 rps under normal use). 60 rpm headroom is generous; the limit is a brute-force / scraping defense, not a throughput governor. Customer-facing `/api/products` traffic is much higher and would need a different strategy (CDN, Redis); explicit non-goal.
- **Alternative considered**: Spring Cloud Gateway rate-limit filter. Rejected — pulls in Spring Cloud, conflicts with GraalVM Native goals.
- **GraalVM caveat**: `caffeine` is supported by the GraalVM Reachability Metadata Repository for Boot 4. No new META-INF entries expected; `nativeTest` confirms.

### 5. Security headers — single `OncePerRequestFilter` registered with high precedence

- **Decision**: `SecurityHeadersFilter extends OncePerRequestFilter`, registered with `@Order(Ordered.HIGHEST_PRECEDENCE + 100)` so it runs before any other filter that might short-circuit (e.g. `JwtAuthenticationFilter`). Header values are read from `@ConfigurationProperties("security.headers")` so per-env tightening (e.g. CSP `report-uri` in prod) does not require code change.
- **Rationale**: One source of truth, easy to audit. ArchUnit rule will fail any other class that sets the listed headers.
- **CSP value**: Static admin UI is built into the same backend; `script-src 'self'` works because Vite builds hash-named JS into `/admin/assets/`. `style-src 'self' 'unsafe-inline'` accommodates Element Plus runtime styles; tightening this to nonce-based requires admin-ui changes — deferred to §9.

### 6. Config validation — `@Validated` on every class, `@Valid` on nested types

- **Decision**: Every `@ConfigurationProperties` class in `shared/config/` gets `@Validated`. Nested types get `@Valid`. Custom `@AssertTrue` validators handle cross-field rules (e.g. "admin secret must differ from user secret"). `application.yml` rejects empty strings via `@NotBlank`, not `@NotNull`, so `JWT_SECRET=` (empty) fails fast.
- **Rationale**: Spring Boot already wires `LocalValidatorFactoryBean` for the starter; no new dependency. `@Validated` triggers Bean Validation during the `Binder` phase, which means failures throw `ConfigurationPropertiesBindException` before any bean accepts traffic.
- **Test strategy**: `JwtPropertiesBindingTest` already exists (Sprint 1). Mirror it for `MongoProperties`, `WechatProperties`, and the cross-field admin-secret rule.

### 7. Secret masking — Jackson `@JsonSerialize(using = SensitiveValueMasker.class)` + custom converter

- **Decision**: Mask serializer applied via field-name regex (`(?i).*(secret|password|uri|token|appid).*`) using a `BeanSerializerModifier`, NOT per-field annotation. This catches new config fields automatically.
- **Rationale**: Per-field annotation has 100% false-negative risk on new fields. Regex on field name catches additions for free. The regex is narrow enough to not mask legitimate non-sensitive fields (`productId`, `userId` don't match `token|password|secret|uri|appid`).
- **Trade-off**: A future field literally named `tokenCount` would get masked. Acceptable; rename or override with explicit `@JsonRawValue`.

## Risks / Trade-offs

- **[Native build slow on warm cache miss]** → Mitigation: pin GraalVM CE 25 base image in CI, use `actions/cache` keyed by `gradle.properties` + `build.gradle` hash. Expect ~20 min cold, ~6 min warm.
- **[nativeTest agent generates noisy `META-INF` diff]** → Mitigation: a normalizer script sorts JSON keys + dedupes entries before commit. Add `scripts/normalize-native-metadata.sh` and run it in the `native` job, fail PR if diff is non-empty after normalization.
- **[Token revocation lookup adds 1 ms p99 to every authenticated request]** → Mitigation: Caffeine cache + index on `_id` (already free in MongoDB). Budget: well under the 500 ms API SLO.
- **[Caffeine bucket state lost on restart → user can briefly re-burst]** → Accept. Single-instance backend today; multi-instance would need shared state (Redis), deferred.
- **[CSP `unsafe-inline` for styles weakens XSS protection]** → Documented. Tightening requires admin-ui style refactor (§9).
- **[JWT_ADMIN_SECRET fail-fast breaks existing deployments]** → Migration: announce in PR body, update `CLAUDE.md` 「环境变量」 section, provide `openssl rand -base64 48` snippet. Deploy environment must add the new variable before the change lands on `main`.
- **[OWASP Dep-Check first run is slow and downloads NVD]** → Mitigation: CI caches `~/.m2/repository/org/owasp/dependency-check-data/` between runs. First run on a fresh runner: ~10 min; warm runs: ~2 min.
- **[Trivy false positives on distroless base]** → Mitigation: explicit `.trivyignore` file with reviewed CVE-suppressions; require Security sign-off for each addition.

## Migration Plan

1. **Pre-merge**: ops adds `JWT_ADMIN_SECRET` to `.env` and production secrets. Generate via `openssl rand -base64 48`. Value MUST differ from `JWT_SECRET`.
2. **Merge order**: PR ladder of 5 commits:
   - C1 `feat(config): validate all @ConfigurationProperties + mask sensitive values` (no behavior change, fail-fast enables)
   - C2 `feat(security): inject baseline security headers + admin rate limit`
   - C3 `feat(auth): token revocation + login failure lockout`
   - C4 `feat(build): wire OWASP Dep-Check + Trivy + Dependabot`
   - C5 `feat(native): nativeTest agent + nativeCompile in CI + docker-compose native binary`
3. **Rollback**: each commit is independently revertible. Native (C5) revert leaves a JVM Dockerfile (C5 keeps the old Dockerfile as `Dockerfile.jvm` for one cycle).
4. **Post-merge**: enable GitHub Advanced Security + push protection in repo settings (not in this PR — requires repo admin).

## Open Questions

- Should `/actuator/configprops` be restricted to ADMIN role even after masking? Current Spring Security config allows actuator on `localhost` only; we should confirm the production reverse proxy strips `/actuator/**` from public traffic. Defer the answer to PR review with Security.
- Trivy thresholds: HIGH+CRITICAL fail-only, or HIGH=warn / CRITICAL=fail? Proposal uses HIGH+CRITICAL fail. Revisit if it generates noise in week 1.
- Dependabot grouping: single grouped PR per ecosystem per week, or one PR per dependency? Proposal uses grouped (`groups.spring-boot.patterns: ["org.springframework.boot:*"]`) to reduce review load.
