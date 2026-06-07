package com.seafood.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 2 §1.1 — JWT 配置在启动期完成 JSR-303 校验,缺失/弱值/双密钥冲突即 fail-fast。
 *
 * <p>与现有 {@code com.seafood.shared.config.ConfigurationPropertiesBindingTest}(happy
 * path,full SpringBootTest)互补:本测试用 {@link ApplicationContextRunner} 精确断言每
 * 一种校验失败的根因,避免起完整 ApplicationContext。
 */
class JwtPropertiesValidationTest {

    private static final String VALID_SECRET = "secret-at-least-32-bytes-long-12345";
    private static final String VALID_ADMIN_SECRET = "admin-secret-at-least-32-bytes-67890";

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestApp.class);

    @Test
    void bindsWhenSecretsArePresentLongEnoughAndDistinct() {
        contextRunner
                .withPropertyValues(
                        "security.jwt.secret=" + VALID_SECRET,
                        "security.jwt.admin-secret=" + VALID_ADMIN_SECRET)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JwtProperties props = context.getBean(JwtProperties.class);
                    assertThat(props.getSecret()).isEqualTo(VALID_SECRET);
                    assertThat(props.getAdminSecret()).isEqualTo(VALID_ADMIN_SECRET);
                });
    }

    @Test
    void failsFastWhenSecretIsMissing() {
        contextRunner
                .withPropertyValues(
                        "security.jwt.secret=",
                        "security.jwt.admin-secret=" + VALID_ADMIN_SECRET)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(BindValidationException.class)
                            .hasMessageContaining("secret");
                });
    }

    @Test
    void failsFastWhenAdminSecretIsMissing() {
        contextRunner
                .withPropertyValues(
                        "security.jwt.secret=" + VALID_SECRET,
                        "security.jwt.admin-secret=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(BindValidationException.class)
                            .hasMessageContaining("adminSecret");
                });
    }

    @Test
    void failsFastWhenSecretIsTooShort() {
        contextRunner
                .withPropertyValues(
                        "security.jwt.secret=too-short",
                        "security.jwt.admin-secret=" + VALID_ADMIN_SECRET)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(BindValidationException.class)
                            .hasMessageContaining("secret");
                });
    }

    @Test
    void failsFastWhenAdminSecretIsTooShort() {
        contextRunner
                .withPropertyValues(
                        "security.jwt.secret=" + VALID_SECRET,
                        "security.jwt.admin-secret=tiny")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(BindValidationException.class)
                            .hasMessageContaining("adminSecret");
                });
    }

    @Test
    void failsFastWhenAdminSecretEqualsUserSecret() {
        contextRunner
                .withPropertyValues(
                        "security.jwt.secret=" + VALID_SECRET,
                        "security.jwt.admin-secret=" + VALID_SECRET)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(BindValidationException.class)
                            .hasMessageContaining("adminSecret");
                });
    }

    @SpringBootConfiguration
    @EnableConfigurationProperties(JwtProperties.class)
    static class TestApp {
    }
}
