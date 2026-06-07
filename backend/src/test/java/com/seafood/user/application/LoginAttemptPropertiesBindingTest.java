package com.seafood.user.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 2 §3.8 — {@link LoginAttemptProperties} 默认值与 yaml 覆盖。
 */
class LoginAttemptPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestApp.class);

    @Test
    void defaultsMatchDesignBaseline() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            LoginAttemptProperties p = ctx.getBean(LoginAttemptProperties.class);
            assertThat(p.getMaxFailures()).isEqualTo(5);
            assertThat(p.getWindowMinutes()).isEqualTo(15);
            assertThat(p.getLockMinutes()).isEqualTo(15);
        });
    }

    @Test
    void customValuesBind() {
        contextRunner
                .withPropertyValues(
                        "security.login-lock.max-failures=3",
                        "security.login-lock.window-minutes=10",
                        "security.login-lock.lock-minutes=30")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    LoginAttemptProperties p = ctx.getBean(LoginAttemptProperties.class);
                    assertThat(p.getMaxFailures()).isEqualTo(3);
                    assertThat(p.getWindowMinutes()).isEqualTo(10);
                    assertThat(p.getLockMinutes()).isEqualTo(30);
                });
    }

    @SpringBootConfiguration
    @EnableConfigurationProperties(LoginAttemptProperties.class)
    static class TestApp {
    }
}
