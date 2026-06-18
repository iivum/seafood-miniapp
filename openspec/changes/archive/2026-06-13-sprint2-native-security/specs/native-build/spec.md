## ADDED Requirements

### Requirement: Native binary builds reproducibly in CI

The CI pipeline SHALL produce a working `seafood-backend` GraalVM Native binary from `feature/refactor` (and any branch targeting it). The build SHALL collect missing reflection / resource / proxy metadata via the GraalVM tracing agent during `nativeTest`, persist the generated JSON under `backend/src/main/resources/META-INF/native-image/`, and then SHALL run `./gradlew nativeCompile` and produce an executable. Hand-edited JSON SHALL NOT be used as the primary source of metadata.

#### Scenario: nativeTest captures metadata

- **WHEN** CI runs `./gradlew nativeTest` against the test suite tagged for native validation
- **THEN** the GraalVM agent writes `reflect-config.json`, `resource-config.json`, `proxy-config.json`, and `serialization-config.json` to a workspace path, and the job uploads the diff against the in-tree `META-INF/native-image/` as a build artifact

#### Scenario: nativeCompile produces the binary

- **WHEN** CI runs `./gradlew nativeCompile` after metadata is in place
- **THEN** the job exits with status 0 and produces `backend/build/native/nativeCompile/seafood-backend` larger than 30 MB

#### Scenario: Missing metadata fails the build

- **WHEN** a developer adds a new reflective code path without re-running `nativeTest`
- **THEN** `nativeCompile` fails with `Unsupported feature` or `ClassNotFoundException`, and the CI log identifies the missing class

### Requirement: Native binary boots and serves traffic

The native binary SHALL boot in under 2 seconds on a 2-vCPU runner, occupy under 200 MB resident memory after warm-up, and SHALL serve at least one round-trip request against MongoDB via `GET /api/products` returning HTTP 200.

#### Scenario: Cold start within budget

- **WHEN** the native binary starts against a running MongoDB on the CI runner
- **THEN** `/actuator/health` returns 200 within 2000 ms of process start

#### Scenario: End-to-end product list

- **WHEN** the native binary is started with seeded MongoDB and a client calls `GET /api/products?page=0&size=10`
- **THEN** the response is HTTP 200 with a non-empty `content` array and `totalElements > 0`

#### Scenario: Memory ceiling

- **WHEN** the native binary has handled 100 sequential `GET /api/products` calls
- **THEN** RSS reported by `ps -o rss=` is below 200 MB

### Requirement: docker-compose deploys the native binary

The repository's `docker-compose.yml` SHALL build the backend service from a multi-stage `Dockerfile` whose final stage copies the native binary onto a distroless base image. The compose stack SHALL come up with two services (backend native + mongodb) and SHALL NOT require a JRE in the runtime image.

#### Scenario: Compose up boots the native stack

- **WHEN** a developer runs `docker-compose up -d` against a fresh checkout
- **THEN** both `backend` and `mongodb` services report `healthy` within 30 seconds, and `docker inspect backend` shows the image based on `gcr.io/distroless/base` (no `openjdk` layer)

#### Scenario: Runtime image excludes JRE

- **WHEN** `docker run --rm seafood-backend:native java -version` is executed
- **THEN** the command fails because no `java` binary exists in the image
