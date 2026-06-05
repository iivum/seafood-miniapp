## Why

The codebase currently has 77 tests at ~80% coverage, but several Spring Boot 4 testing patterns are underused: the codebase has no slice tests (`@WebMvcTest`, `@DataMongoTest`, `@JsonTest`), no integration tests against real MongoDB, and no automated guardrails enforcing the DDD layered architecture (`api → application → domain → infra`). Spring Boot 4.0 also removed `@MockBean` and `@SpyBean`, replacing them with `@MockitoBean` / `@MockitoSpyBean`, and `@SpringBootTest` no longer auto-configures `MockMvc`, `WebClient`, or `TestRestTemplate` — the existing tests need to be audited and migrated where they rely on removed APIs. Without these foundations, refactors silently break architectural boundaries and CI cannot detect Boot 4 API drift until runtime.

## What Changes

- **Migrate deprecated test annotations** to Spring Boot 4 equivalents: `@MockBean` → `@MockitoBean`, `@SpyBean` → `@MockitoSpyBean`; remove any `MockitoTestExecutionListener` references; add explicit `MockMvc` / `WebClient` / `TestRestTemplate` configuration to `@SpringBootTest` usages.
- **Add slice tests** for HTTP controllers (`@WebMvcTest`), JSON DTOs (`@JsonTest`), and MongoDB repositories (`@DataMongoTest` + Testcontainers).
- **Add Testcontainers MongoDB** module as the single integration-test entry point — `MongoDBContainer("mongo:7")` started via `@Container` static field.
- **Add ArchUnit DDD layer rules** enforcing: `api` does not depend on `infra`; `bff` does not depend on `infra`; `domain` has no Spring framework dependencies; no `controller` calls `repository` directly. Rules fail the build on violation.
- **Add static check for `@RefreshScope`** in test sources (existing `check-no-refresh-scope.sh` covers main sources; extend or mirror for tests).
- **Add `@ConfigurationPropertiesTest`** coverage for at least the JWT, MongoDB, and (if present) WeChat configuration bindings.
- **Add `gradle test --tests "*.ArchUnit*"`** fast lane separate from the full integration suite, so DDD guardrails run on every PR.

**BREAKING**: No public API or runtime behavior change. Test-internal annotation renames only.

## Capabilities

### New Capabilities

- `test-foundation`: Test infrastructure covering Spring Boot 4 testing stack, Testcontainers integration, ArchUnit DDD guardrails, and static checks. This is a developer-facing capability (CI / build-time) and does not change product behavior.

### Modified Capabilities

_None._ Existing capabilities (`admin-ui`, `auth`, `backend-api`, `mini-program`) describe product behavior; this change is internal to the test build and does not alter their requirements.

## Impact

- **Build files**: `backend/build.gradle` (add testcontainers-bom, testcontainers-junit-jupiter, testcontainers-mongodb, archunit-junit5; bump junit-jupiter / mockito-core if Boot 4 BOM requires).
- **Test sources**: `backend/src/test/java/**` (audit & rename annotations; add slice / integration / architecture test classes).
- **CI**: `gradle check` continues to be the entry point. No new job required, but matrix may grow (covered in a later change).
- **No runtime / production code impact.** No DTO shape change, no endpoint change, no MongoDB schema change.
- **Local dev**: requires Docker for Testcontainers. JDK 25 + GraalVM toolchain (already required by `gradle.properties`).
