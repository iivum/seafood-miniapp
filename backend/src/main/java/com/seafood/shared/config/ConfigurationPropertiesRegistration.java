package com.seafood.shared.config;

import com.seafood.shared.security.AdminRateLimitProperties;
import com.seafood.shared.security.SecurityHeadersProperties;
import com.seafood.user.application.LoginAttemptProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Sprint 2 §1.4 / §2.8 / §3.8 — 统一注册 {@code shared/config/}、{@code shared/security/}
 * 与 {@code user/application/} 下的 {@code @ConfigurationProperties} 类型,触发
 * {@code @Validated} 在启动期完成 JSR-303 校验。
 *
 * <p>{@code JwtProperties} 仍由 {@code SecurityConfig} 注册(历史位置,避免无谓 diff)。
 * {@code MongoUriValidator} 是 {@code @Component},由组件扫描自动发现,不在此处。
 * {@code LoginAttemptProperties} 跨 {@code user.application} 包,放这里集中注册
 * 方便审计。
 */
@Configuration
@EnableConfigurationProperties({
        WechatProperties.class,
        SecurityHeadersProperties.class,
        AdminRateLimitProperties.class,
        LoginAttemptProperties.class
})
public class ConfigurationPropertiesRegistration {
}
