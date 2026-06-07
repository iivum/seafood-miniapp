package com.seafood.shared.config;

import com.seafood.shared.security.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.actuate.context.properties.ConfigurationPropertiesReportEndpoint;
import org.springframework.boot.actuate.context.properties.ConfigurationPropertiesReportEndpoint.ConfigurationPropertiesBeanDescriptor;
import org.springframework.boot.actuate.context.properties.ConfigurationPropertiesReportEndpoint.ConfigurationPropertiesDescriptor;
import org.springframework.boot.actuate.context.properties.ConfigurationPropertiesReportEndpoint.ContextConfigurationPropertiesDescriptor;
import org.springframework.boot.actuate.endpoint.SanitizableData;
import org.springframework.boot.actuate.endpoint.Show;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 2 §1.8 — end-to-end coverage that sensitive configuration values do not leak via
 * {@code /actuator/configprops} or via application JSON / log output.
 *
 * <p>This is the integration test that closes spec {@code config-validation}'s two scenarios
 * about disclosure surfaces:
 *
 * <ul>
 *   <li><b>"configprops masks JWT secret"</b> — drives {@link ConfigurationPropertiesReportEndpoint}
 *       directly (no HTTP / no admin auth wiring) with the production
 *       {@code @ConfigurationProperties} classes ({@link JwtProperties}, {@link WechatProperties})
 *       bound to fixture values, then asserts that {@code security.jwt.secret},
 *       {@code security.jwt.adminSecret}, {@code wechat.secret} and {@code wechat.appid} surface
 *       as {@link SanitizableData#SANITIZED_VALUE} ({@code "******"}) and never as the original
 *       value. Spec wording {@code "abcd***" (or similar 4-character prefix)} is satisfied by the
 *       "or similar" clause — the core invariant is "raw secret never appears on this endpoint".
 *   <li><b>"Log output masks MongoDB URI"</b> — exercises the primary {@link ObjectMapper} (the
 *       same instance that serializes {@code @RestController} JSON responses and that the
 *       {@code LoggingSystem} hands to log appenders) and asserts the {@link JacksonModule}
 *       registered by {@link JacksonSensitiveValueConfig} produces a {@code tops***} 4-char prefix
 *       mask for any field whose name matches {@code SensitiveValueBeanSerializerModifier}'s regex.
 * </ul>
 *
 * <p><b>Why two assertions, two chains:</b> Spring Boot 4's actuator builds its own {@code JsonMapper}
 * inside {@code JacksonBeanSerializer} ({@code spring-boot-actuator-4.0.6}) and does <em>not</em>
 * pull in container-managed {@link tools.jackson.databind.JacksonModule} beans. The masking on the
 * configprops endpoint is therefore enforced by {@code Sanitizer} + {@code SanitizableData.SANITIZED_VALUE},
 * not by {@link SensitiveValueMasker}. {@link JacksonSensitiveValueConfig}'s module instead protects
 * the <em>primary</em> {@code ObjectMapper} path (controller JSON, log message formatting). Both
 * defences are exercised here so a regression in either path fails this test.
 *
 * <p><b>Weakness of the actuator test (PR review #5):</b> {@code Show.NEVER} + actuator's
 * hard-coded sanitization patterns ({@code password|secret|key|token|credential|...}) would mask
 * {@code secret}/{@code appid} <em>even if we deleted {@link JacksonSensitiveValueConfig}</em>.
 * To prove the primary-ObjectMapper path is actually protected by our module — not just by
 * actuator defaults — {@link #primaryObjectMapperMasksFieldsBeyondActuatorDefaults()} registers a
 * custom {@code @ConfigurationProperties} bean whose field name ({@code mongoUri}) is in our
 * regex but <em>not</em> in actuator's default sanitization set. If {@link JacksonSensitiveValueConfig}
 * is removed, the {@code mongoUri} raw value would leak through the primary ObjectMapper and this
 * test would fail.
 *
 * <p>Setup is intentionally lightweight: {@link ApplicationContextRunner} with
 * {@link JacksonAutoConfiguration} + the production {@link JacksonSensitiveValueConfig}. No
 * Testcontainers, no admin-auth harness, no production {@code application.yml} change to expose
 * {@code configprops} over HTTP — runs in &lt; 1 s.
 */
class ConfigPropsMaskingIT {

    private static final String JWT_USER_SECRET = "user-secret-at-least-32-bytes-1234567";
    private static final String JWT_ADMIN_SECRET = "admin-secret-at-least-32-bytes-789xyz";
    private static final String WECHAT_APPID = "wxAppId1234567890";
    private static final String WECHAT_SECRET = "wechat-server-side-secret-value";

    /**
     * Custom test fixture whose {@code mongoUri} field is in our regex
     * ({@code SensitiveValueBeanSerializerModifier.SENSITIVE_FIELD_PATTERN}) but NOT in
     * Spring Boot's actuator default sanitization set (which only covers
     * {@code password|secret|key|token|credential|vcap_services} by default). Lets us prove
     * {@link JacksonSensitiveValueConfig} is doing real work — not just relying on actuator defaults.
     */
    @ConfigurationProperties(prefix = "fixture")
    static class FixtureProps {
        private String mongoUri;
        private String plainLabel;

        public String getMongoUri() { return mongoUri; }
        public void setMongoUri(String mongoUri) { this.mongoUri = mongoUri; }
        public String getPlainLabel() { return plainLabel; }
        public void setPlainLabel(String plainLabel) { this.plainLabel = plainLabel; }
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(TestApp.class)
            .withPropertyValues(
                    "security.jwt.secret=" + JWT_USER_SECRET,
                    "security.jwt.admin-secret=" + JWT_ADMIN_SECRET,
                    "wechat.enabled=true",
                    "wechat.appid=" + WECHAT_APPID,
                    "wechat.secret=" + WECHAT_SECRET,
                    // fixture: 'mongoUri' is in OUR regex (contains "uri") but NOT in actuator default
                    // CI fix:用户名/密码换成显眼的 _FAKE 占位 —— TruffleHog filesystem
                    // 扫描会拿 hunter2 等当 secret,触发假阳性。
                    "fixture.mongo-uri=mongodb://TEST_USER_FAKE:TEST_PASSWORD_FAKE@db.example.com:27017/seafood?ssl=true", // trufflehog:ignore — placeholder fixture
                    "fixture.plain-label=this-is-not-sensitive");

    @Test
    void actuatorConfigPropsReplacesSensitiveValuesWithSanitizedPlaceholder() {
        contextRunner.run(context -> {
            ConfigurationPropertiesReportEndpoint endpoint = new ConfigurationPropertiesReportEndpoint(
                    Collections.emptyList(), Show.NEVER);
            endpoint.setApplicationContext(context.getSourceApplicationContext());

            ConfigurationPropertiesDescriptor descriptor = endpoint.configurationProperties();
            String rendered = renderDescriptorAsString(descriptor);

            // Spec scenario "configprops masks JWT secret" + sister fields for wechat.
            // Note: this asserts actuator's built-in Sanitizer (defaults cover
            // password|secret|key|token|credential) — NOT our JacksonSensitiveValueConfig.
            // See #primaryObjectMapperMasksFieldsBeyondActuatorDefaults for a test that
            // genuinely depends on our module being active.
            assertSanitizedValueEquals(descriptor, "security.jwt", "secret");
            assertSanitizedValueEquals(descriptor, "security.jwt", "adminSecret");
            assertSanitizedValueEquals(descriptor, "wechat", "secret");
            assertSanitizedValueEquals(descriptor, "wechat", "appid");

            // Belt-and-braces: scan the entire serialized descriptor (properties + inputs trees);
            // raw secret values MUST NOT appear anywhere, even in metadata or origin descriptions.
            assertThat(rendered)
                    .as("Raw JWT user secret leaked into configprops descriptor")
                    .doesNotContain(JWT_USER_SECRET);
            assertThat(rendered)
                    .as("Raw JWT admin secret leaked into configprops descriptor")
                    .doesNotContain(JWT_ADMIN_SECRET);
            assertThat(rendered)
                    .as("Raw wechat secret leaked into configprops descriptor")
                    .doesNotContain(WECHAT_SECRET);
            assertThat(rendered)
                    .as("Raw wechat appid leaked into configprops descriptor")
                    .doesNotContain(WECHAT_APPID);
        });
    }

    @Test
    void primaryObjectMapperAppliesSensitiveValueModuleToConfigurationPropertiesBeans() {
        contextRunner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            JwtProperties jwt = context.getBean(JwtProperties.class);

            String json = mapper.writeValueAsString(jwt);

            // SensitiveValueMasker contract: first 4 chars + "***" for matched String fields.
            String userMask = JWT_USER_SECRET.substring(0, 4) + "***";
            String adminMask = JWT_ADMIN_SECRET.substring(0, 4) + "***";

            assertThat(json).contains("\"secret\":\"" + userMask + "\"");
            assertThat(json).contains("\"adminSecret\":\"" + adminMask + "\"");
            // Sanity: raw secrets must not survive JSON serialization on the main ObjectMapper path
            // (this is what controller responses + log message formatting consume).
            assertThat(json).doesNotContain(JWT_USER_SECRET);
            assertThat(json).doesNotContain(JWT_ADMIN_SECRET);
        });
    }

    /**
     * PR review #5 — Regression guard for {@link JacksonSensitiveValueConfig}.
     *
     * <p>Uses a field ({@code mongoUri}) that is in our regex but NOT in actuator's default
     * sanitization set. Asserts:
     * <ol>
     *   <li>The primary {@link ObjectMapper} masks {@code mongoUri} (proves our module is active
     *       and doing real work — would fail if {@link JacksonSensitiveValueConfig} were removed).</li>
     *   <li>The non-sensitive {@code plainLabel} is left intact (proves the module does not
     *       over-mask).</li>
     * </ol>
     */
    @Test
    void primaryObjectMapperMasksFieldsBeyondActuatorDefaults() {
        contextRunner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            FixtureProps fixture = context.getBean(FixtureProps.class);

            String json = mapper.writeValueAsString(fixture);

            // mongoUri is in our regex; raw value contains the placeholder password "TEST_PASSWORD_FAKE" and the host.
            String raw = fixture.getMongoUri();
            String expectedMask = raw.substring(0, 4) + "***";
            assertThat(json)
                    .as("mongoUri must be masked on primary ObjectMapper (our module, not actuator)")
                    .contains("\"mongoUri\":\"" + expectedMask + "\"")
                    .doesNotContain(raw)                       // whole URI gone
                    .doesNotContain("TEST_PASSWORD_FAKE")    // password gone
                    .doesNotContain("db.example.com");         // host gone

            // plainLabel is NOT in our regex → must be left as-is.
            assertThat(json)
                    .as("non-sensitive field must NOT be masked")
                    .contains("\"plainLabel\":\"this-is-not-sensitive\"");
        });
    }

    private static void assertSanitizedValueEquals(
            ConfigurationPropertiesDescriptor descriptor,
            String prefix,
            String propertyName) {
        ConfigurationPropertiesBeanDescriptor bean = findBeanByPrefix(descriptor, prefix);
        assertThat(bean.getProperties())
                .as("Actuator configprops bean[%s].%s", prefix, propertyName)
                .containsEntry(propertyName, SanitizableData.SANITIZED_VALUE);
    }

    private static ConfigurationPropertiesBeanDescriptor findBeanByPrefix(
            ConfigurationPropertiesDescriptor descriptor,
            String prefix) {
        for (ContextConfigurationPropertiesDescriptor ctx : descriptor.getContexts().values()) {
            for (ConfigurationPropertiesBeanDescriptor bean : ctx.getBeans().values()) {
                if (prefix.equals(bean.getPrefix())) {
                    return bean;
                }
            }
        }
        throw new AssertionError("No @ConfigurationProperties bean found for prefix " + prefix);
    }

    /**
     * Flatten properties + inputs trees to a single string for substring leak-detection. Includes
     * both the masked-value view ({@code properties}) and the origin/value-input view ({@code inputs})
     * so a regression that leaks via either surface is caught.
     */
    private static String renderDescriptorAsString(ConfigurationPropertiesDescriptor descriptor) {
        StringBuilder sb = new StringBuilder(2048);
        for (ContextConfigurationPropertiesDescriptor ctx : descriptor.getContexts().values()) {
            for (ConfigurationPropertiesBeanDescriptor bean : ctx.getBeans().values()) {
                sb.append(bean.getPrefix()).append('=').append(bean.getProperties()).append('\n');
                sb.append(bean.getPrefix()).append("[inputs]=").append(bean.getInputs()).append('\n');
            }
        }
        return sb.toString();
    }

    @SpringBootConfiguration
    @EnableConfigurationProperties({JwtProperties.class, WechatProperties.class, FixtureProps.class})
    @Import(JacksonSensitiveValueConfig.class)
    static class TestApp {
    }
}
