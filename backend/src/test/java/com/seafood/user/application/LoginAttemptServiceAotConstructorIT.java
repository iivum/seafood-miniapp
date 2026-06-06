package com.seafood.user.application;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This test guards against removing the @Autowired annotation that
 * disambiguates the 1-arg constructor for Spring 6 AOT processing.
 * See PR review C2.
 */
@DisplayName("Sprint 2 CI fix: LoginAttemptService 1-arg ctor @Autowired disambiguates AOT bean wiring")
class LoginAttemptServiceAotConstructorIT {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void springPicksOneArgConstructorWhenBothAreViable() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(LoginAttemptService.class);
        });
    }

    @Configuration
    @ComponentScan(
            basePackages = "com.seafood.user.application",
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = LoginAttemptService.class))
    static class TestConfig {

        @Bean
        LoginAttemptProperties loginAttemptProperties() {
            return new LoginAttemptProperties();
        }

        @Bean
        Ticker ticker() {
            return Ticker.systemTicker();
        }
    }
}
