package com.seafood.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;

/**
 * Sprint 2 §1.7 — 把 {@link SensitiveValueBeanSerializerModifier} 注册为 Spring 上下文中
 * 可发现的 {@code JacksonModule} bean。
 *
 * <p>Spring Boot 自动装配会把所有 {@code JacksonModule} bean 注入主 ObjectMapper,
 * 因此 {@code /actuator/configprops}、{@code /actuator/env}、JSON 响应都会自动应用脱敏。
 */
@Configuration
public class JacksonSensitiveValueConfig {

    @Bean
    public JacksonModule sensitiveValueModule() {
        return new SimpleModule("SensitiveValueModule")
                .setSerializerModifier(new SensitiveValueBeanSerializerModifier());
    }
}
