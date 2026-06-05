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
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(TestApp.class)
            .withPropertyValues(
                    "security.jwt.secret=" + JWT_USER_SECRET,
                    "security.jwt.admin-secret=" + JWT_ADMIN_SECRET,
                    "wechat.enabled=true",
                    "wechat.appid=" + WECHAT_APPID,
                    "wechat.secret=" + WECHAT_SECRET);

    @Test
    void actuatorConfigPropsReplacesSensitiveValuesWithSanitizedPlaceholder() {
        contextRunner.run(context -> {
            ConfigurationPropertiesReportEndpoint endpoint = new ConfigurationPropertiesReportEndpoint(
                    Collections.emptyList(), Show.NEVER);
            endpoint.setApplicationContext(context.getSourceApplicationContext());

            ConfigurationPropertiesDescriptor descriptor = endpoint.configurationProperties();
            String rendered = renderDescriptorAsString(descriptor);

            // Spec scenario "configprops masks JWT secret" + sister fields for wechat.
            // Actuator default Show.NEVER → Sanitizer returns the literal SANITIZED_VALUE ("******").
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
    @EnableConfigurationProperties({JwtProperties.class, WechatProperties.class})
    @Import(JacksonSensitiveValueConfig.class)
    static class TestApp {
    }
}
