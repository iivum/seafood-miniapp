## ADDED Requirements

### Requirement: All `@ConfigurationProperties` are validated at startup

Every `@ConfigurationProperties` class in `com.seafood.shared.config` SHALL be annotated with `@Validated` and use JSR-303 constraints (`@NotBlank`, `@Size`, `@Pattern`, custom validators) to express required values and shape. Binding failures SHALL prevent the application context from finishing startup; the process SHALL exit with status code 1 and SHALL log the property path that failed.

#### Scenario: Missing JWT user secret

- **WHEN** the process starts without `JWT_SECRET` set (or with a value shorter than 32 bytes)
- **THEN** Spring throws `ConfigurationPropertiesBindException`, the application context fails to start, and the process exits with status 1

#### Scenario: Admin secret equals user secret

- **WHEN** `JWT_ADMIN_SECRET` is set to the same value as `JWT_SECRET`
- **THEN** the custom validator on `JwtProperties` rejects the binding and the process exits with status 1

#### Scenario: Invalid MongoDB URI

- **WHEN** `MONGODB_URI` is missing, empty, or does not start with `mongodb://` / `mongodb+srv://`
- **THEN** the process exits with status 1 and logs the binding error referencing `mongodb.uri`

#### Scenario: WeChat enabled without credentials

- **WHEN** `WECHAT_ENABLED=true` is set but `WECHAT_APPID` or `WECHAT_SECRET` is missing
- **THEN** the process exits with status 1 and logs the missing field

### Requirement: Sensitive values are masked in diagnostics

The system SHALL register a Jackson serializer that masks any field whose name matches `(?i).*(secret|password|uri|token|appid).*` when the `/actuator/configprops`, `/actuator/env`, or application logs render that value. The mask SHALL show only the first 4 characters followed by `***`.

#### Scenario: configprops masks JWT secret

- **WHEN** an ADMIN calls `GET /actuator/configprops`
- **THEN** the JSON contains `"secret": "abcd***"` (or similar 4-character prefix) and never the full secret value

#### Scenario: Log output masks MongoDB URI

- **WHEN** the application logs the active configuration at startup
- **THEN** any line referencing `mongodb.uri` shows only the leading 4 characters of the URI followed by `***`

#### Scenario: Stack trace does not include secret

- **WHEN** a `ConfigurationPropertiesBindException` is logged
- **THEN** the rendered message does not contain any character of the original secret beyond the 4-character prefix
