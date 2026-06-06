package com.seafood.shared.security;

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
 *
 * <p>Strategy: provide a {@link Ticker} bean so BOTH constructors are viable,
 * then let Spring's component scan autowire {@link AdminRateLimiter}. With
 * {@code @Autowired} on the 1-arg ctor, Spring picks it. Without it, AOT
 * fails with the same "ambiguous constructor" error that broke
 * {@code ./gradlew nativeCompile}.
 */
@DisplayName("Sprint 2 CI fix: AdminRateLimiter 1-arg ctor @Autowired disambiguates AOT bean wiring")
class AdminRateLimiterAotConstructorIT {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void springPicksOneArgConstructorWhenBothAreViable() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AdminRateLimiter.class);
        });
    }

    @Configuration
    @ComponentScan(
            basePackages = "com.seafood.shared.security",
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = AdminRateLimiter.class))
    static class TestConfig {

        @Bean
        AdminRateLimitProperties adminRateLimitProperties() {
            return new AdminRateLimitProperties();
        }

        @Bean
        Ticker ticker() {
            return Ticker.systemTicker();
        }
    }
}
