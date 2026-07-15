## MODIFIED Requirements

### Requirement: Uniform error responses
The system SHALL translate all unhandled and domain exceptions into a single `ErrorResponse` shape with a stable `code` and human-readable `message`. This applies to unclassified/unexpected exceptions as well: they MUST NOT be allowed to fall through to a generic authorization rejection or an unrelated status code. The `/error` internal dispatch path MUST NOT be blocked by the authorization allowlist's catch-all deny rule.

**Rationale for this delta**: 2026-07-13 E2E found an unhandled `IllegalArgumentException` (product status enum deserialization failure) surfacing to the client as HTTP 403 with an empty body — the exception fell through to Spring's `/error` redispatch, which was not in the security allowlist and was caught by `anyRequest().denyAll()`. The true status code (500) and error contract (`{code,message}`) were both lost.

#### Scenario: Validation failure on request body
- **WHEN** a controller receives a request whose body fails Bean Validation
- **THEN** the system returns HTTP 400 with `code=VALIDATION` and a `fieldErrors` map

#### Scenario: Domain rule violation
- **WHEN** any application service throws `DomainException`
- **THEN** the system returns HTTP 409 with `code=DOMAIN` and the exception message

#### Scenario: Resource not found
- **WHEN** any application service throws `NotFoundException`
- **THEN** the system returns HTTP 404 with `code=NOT_FOUND` and the exception message

#### Scenario: Unclassified exception
- **WHEN** any controller/service/repository call throws an exception not covered by a specific `@ExceptionHandler`
- **THEN** the system returns HTTP 500 with `code=INTERNAL` and a generic message (no stack trace or internal details in the body)
- **AND** the response is NOT HTTP 403 and is NOT an empty body

#### Scenario: /error path is not blocked by authorization
- **WHEN** a request is internally redispatched to `/error` (e.g. an exception not caught by any `@ExceptionHandler`, or a framework-level dispatch failure)
- **THEN** the security filter chain does not reject it with `denyAll`
- **AND** the response still carries a `{code,message}`-shaped body, not Spring Boot's default error attributes shape
