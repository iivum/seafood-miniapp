## ADDED Requirements

### Requirement: Test sources use the Spring Boot 4 testing stack

The project's test sources SHALL use the Spring Boot 4 / Spring Framework 7 testing API. The annotations `@MockBean` and `@SpyBean` from `org.springframework.boot.test.mock` MUST NOT appear in any test source. The annotations `@MockitoBean` and `@MockitoSpyBean` from `org.springframework.test.context.bean.override.mockito` MUST be used in their place. Tests using `@SpringBootTest` MUST explicitly configure `MockMvc`, `WebClient`, or `TestRestTemplate` via dedicated annotations, since Spring Boot 4 no longer auto-configures them.

#### Scenario: No legacy mock annotations remain

- **WHEN** the test sources are scanned with `grep -rE "@MockBean|@SpyBean" backend/src/test/`
- **THEN** the command returns zero matches

#### Scenario: Modern mock annotations are used

- **WHEN** a test class declares a Mockito-managed bean field
- **THEN** the field is annotated with `@MockitoBean` (or `@MockitoSpyBean` for spies) and the test class is annotated with `@ExtendWith(MockitoExtension.class)` or the equivalent Spring Boot 4 wiring

#### Scenario: Spring Boot test auto-configurations are explicit

- **WHEN** a test class uses `@SpringBootTest` and needs a request-mapping entry point
- **THEN** the class additionally declares `@AutoConfigureMockMvc`, `@AutoConfigureWebTestClient`, or imports `TestRestTemplate` configuration explicitly

### Requirement: DDD layering is enforced by an architecture test

The project MUST contain an ArchUnit test class that fails the build on DDD layering violations. The test class SHALL be discoverable by Gradle's default test task. The following rules MUST be enforced:

- Classes in `com.seafood.*.api` MUST NOT depend on classes in `com.seafood.*.infra`.
- Classes in `com.seafood.bff` MUST NOT depend on classes in `com.seafood.*.infra`.
- Classes in `com.seafood.*.domain` MUST NOT depend on `org.springframework.*` except for `org.springframework.data.annotation.Id` and `org.springframework.data.mongodb.core.mapping.Document`.
- Classes in `com.seafood.*.api` that are annotated with `@RestController` or `@Controller` MUST NOT declare a constructor parameter or field whose type is assignable to `*Repository`.

#### Scenario: API layer does not reach into infrastructure

- **WHEN** a developer adds a `import com.seafood.product.infra.ProductRepository` to a class in `com.seafood.product.api`
- **THEN** the architecture test fails the build with the file path, the offending import, and the rule name

#### Scenario: Domain layer stays framework-agnostic

- **WHEN** a developer adds a `import org.springframework.stereotype.Service` to a class in `com.seafood.product.domain`
- **THEN** the architecture test fails the build

#### Scenario: BFF does not bypass ApplicationService

- **WHEN** a developer adds a `import com.seafood.product.infra.*` to any class in `com.seafood.bff`
- **THEN** the architecture test fails the build

#### Scenario: Controller never holds a repository reference

- **WHEN** a class in `com.seafood.*.api` annotated with `@RestController` declares a field of type `ProductRepository`
- **THEN** the architecture test fails the build

### Requirement: MongoDB integration tests use Testcontainers

MongoDB-backed integration tests MUST run against a real MongoDB 7 instance managed by Testcontainers, not against an embedded driver or a hard-coded local socket. The Testcontainers lifecycle MUST be shared across the test JVM (single static `MongoDBContainer` reused by all `@DataMongoTest` classes in the run). Tests using the container MUST be tagged with JUnit 5 `@Tag("docker")` so a Docker-less run can skip them.

#### Scenario: First test triggers container start

- **WHEN** the first `@DataMongoTest` class is executed
- **THEN** Testcontainers starts a `mongo:7` container, and the test connects via Spring Data MongoDB using the dynamically assigned port

#### Scenario: Subsequent tests reuse the container

- **WHEN** a second `@DataMongoTest` class runs in the same Gradle test JVM
- **THEN** no new container is started; the existing container is reused

#### Scenario: Docker-less environment skips the suite

- **WHEN** a contributor runs `./gradlew test -PexcludeTags=docker`
- **THEN** all tests tagged `@Tag("docker")` are skipped, the build passes for the remaining suite, and the contributor is informed via a Gradle warning which test classes were excluded

### Requirement: `@RefreshScope` is banned in test sources

The existing `check-no-refresh-scope.sh` script MUST also scan `backend/src/test/java` and report the same error format. The Gradle `check` task MUST fail if the script finds a `@RefreshScope` reference in test sources.

#### Scenario: RefreshScope in a test is detected

- **WHEN** a developer adds `import org.springframework.cloud.context.config.annotation.RefreshScope` to a file under `backend/src/test/java`
- **THEN** `./gradlew check` fails with the file path and the offending import

#### Scenario: Empty match set passes

- **WHEN** no test source contains `@RefreshScope` or its import
- **THEN** the script exits 0 and `gradle check` proceeds

### Requirement: Configuration properties binding is smoke-tested

A test class MUST load the project's `application.yml` and assert that the three highest-value property prefixes bind successfully: the MongoDB URI binding, the JWT binding (the application-specific root, not the Spring `spring.*` prefix), and (if present) the WeChat binding. The test MUST use `@ConfigurationPropertiesTest` or the equivalent Spring Boot 4 mechanism.

#### Scenario: All three prefixes bind

- **WHEN** the test loads `application.yml` with all three prefixes populated
- **THEN** the corresponding `@ConfigurationProperties` beans are constructed with non-null values for the documented fields

#### Scenario: Missing prefix is reported, not silently ignored

- **WHEN** a developer removes one of the three prefixes from `application.yml` without deleting the corresponding `@ConfigurationProperties` class
- **THEN** the binding test fails with the prefix name and the affected class
