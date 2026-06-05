package com.seafood.shared.config;

import com.seafood.shared.security.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Configuration binding smoke test. Asserts the {@code security.jwt.*} prefix
 * binds successfully when {@code application.yml} is loaded.
 *
 * <p>Scoping to a single binding because the codebase has exactly one
 * {@code @ConfigurationProperties} class ({@link JwtProperties}) — MongoDB URI
 * is auto-configured by Spring Boot and WeChat is consumed via plain
 * environment variables, not via a custom binding class.
 */
@SpringBootTest(
        classes = ConfigurationPropertiesBindingTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "security.jwt.secret=test-secret-at-least-32-bytes-long-xx",
        "security.jwt.admin-secret=admin-secret-at-least-32-bytes-xx",
        "security.jwt.access-token-ttl=10m",
        "security.jwt.refresh-token-ttl=1d"
})
class ConfigurationPropertiesBindingTest {

    @org.springframework.beans.factory.annotation.Autowired
    private JwtProperties jwt;

    @Test
    void securityJwtPrefixBinds() {
        assertThat(jwt.getSecret()).isEqualTo("test-secret-at-least-32-bytes-long-xx");
        assertThat(jwt.getAdminSecret()).isEqualTo("admin-secret-at-least-32-bytes-xx");
        assertThat(jwt.getAccessTokenTtl()).isEqualTo(java.time.Duration.ofMinutes(10));
        assertThat(jwt.getRefreshTokenTtl()).isEqualTo(java.time.Duration.ofDays(1));
    }

    @org.springframework.boot.SpringBootConfiguration
    @EnableConfigurationProperties(JwtProperties.class)
    static class TestApp {
    }
}
