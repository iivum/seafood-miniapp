package com.seafood.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 2 §2.1 — {@link SecurityHeadersProperties} 在启动期完成 JSR-303 校验,
 * 默认值满足 spec 列出的最小基线。
 *
 * <p>覆盖:
 * <ul>
 *   <li>默认值与 spec 表格一致</li>
 *   <li>{@code @NotBlank} 校验:任一字段为空即 fail-fast</li>
 *   <li>按 yaml 覆盖生效</li>
 * </ul>
 */
class SecurityHeadersPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestApp.class);

    @Test
    void defaultsMatchSpecBaseline() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            SecurityHeadersProperties p = ctx.getBean(SecurityHeadersProperties.class);
            assertThat(p.getStrictTransportSecurity()).isEqualTo("max-age=31536000; includeSubDomains");
            assertThat(p.getXContentTypeOptions()).isEqualTo("nosniff");
            assertThat(p.getXFrameOptions()).isEqualTo("DENY");
            assertThat(p.getReferrerPolicy()).isEqualTo("strict-origin-when-cross-origin");
            assertThat(p.getPermissionsPolicy()).isEqualTo("geolocation=(), microphone=(), camera=()");
            assertThat(p.getContentSecurityPolicy())
                    .isEqualTo("default-src 'self'; img-src 'self' data: https:; "
                            + "style-src 'self' 'unsafe-inline'; script-src 'self'");
        });
    }

    @Test
    void failsFastWhenContentSecurityPolicyIsBlank() {
        contextRunner
                .withPropertyValues("security.headers.content-security-policy=")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(BindValidationException.class);
                });
    }

    @Test
    void customValuesBind() {
        contextRunner
                .withPropertyValues(
                        "security.headers.x-frame-options=SAMEORIGIN",
                        "security.headers.referrer-policy=no-referrer")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    SecurityHeadersProperties p = ctx.getBean(SecurityHeadersProperties.class);
                    assertThat(p.getXFrameOptions()).isEqualTo("SAMEORIGIN");
                    assertThat(p.getReferrerPolicy()).isEqualTo("no-referrer");
                });
    }

    @SpringBootConfiguration
    @EnableConfigurationProperties(SecurityHeadersProperties.class)
    static class TestApp {
    }
}
