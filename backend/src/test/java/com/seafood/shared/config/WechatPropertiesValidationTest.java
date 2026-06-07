package com.seafood.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 2 §1.3 — wechat.* 启动期校验。
 *
 * <p>开发期默认 {@code wechat.enabled=false},appid/secret 可缺;一旦 {@code enabled=true}
 * 必须同时提供非空 appid 与非空 secret(由 {@code @AssertTrue} 跨字段规则保证)。
 */
class WechatPropertiesValidationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestApp.class);

    @Test
    void bindsWithDefaultsWhenDisabled() {
        contextRunner
                .withPropertyValues(
                        "wechat.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    WechatProperties props = context.getBean(WechatProperties.class);
                    assertThat(props.isEnabled()).isFalse();
                    assertThat(props.getAppid()).isNullOrEmpty();
                    assertThat(props.getSecret()).isNullOrEmpty();
                });
    }

    @Test
    void bindsWhenEnabledAndBothCredentialsPresent() {
        contextRunner
                .withPropertyValues(
                        "wechat.enabled=true",
                        "wechat.appid=wx1234567890",
                        "wechat.secret=top-secret-value")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    WechatProperties props = context.getBean(WechatProperties.class);
                    assertThat(props.isEnabled()).isTrue();
                    assertThat(props.getAppid()).isEqualTo("wx1234567890");
                    assertThat(props.getSecret()).isEqualTo("top-secret-value");
                });
    }

    @Test
    void failsWhenEnabledButAppidBlank() {
        contextRunner
                .withPropertyValues(
                        "wechat.enabled=true",
                        "wechat.appid=",
                        "wechat.secret=top-secret-value")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(BindValidationException.class)
                            .hasMessageContaining("wechat");
                });
    }

    @Test
    void failsWhenEnabledButSecretBlank() {
        contextRunner
                .withPropertyValues(
                        "wechat.enabled=true",
                        "wechat.appid=wx1234567890",
                        "wechat.secret=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(BindValidationException.class)
                            .hasMessageContaining("wechat");
                });
    }

    @Test
    void failsWhenEnabledButBothCredentialsBlank() {
        contextRunner
                .withPropertyValues(
                        "wechat.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(BindValidationException.class)
                            .hasMessageContaining("wechat");
                });
    }

    @SpringBootConfiguration
    @EnableConfigurationProperties(WechatProperties.class)
    static class TestApp {
    }
}
