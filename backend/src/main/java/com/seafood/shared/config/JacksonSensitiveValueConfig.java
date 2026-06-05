package com.seafood.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;

/**
 * Sprint 2 §1.7 — 把 {@link SensitiveValueBeanSerializerModifier} 注册为 Spring 上下文中
 * 可发现的 {@code JacksonModule} bean。
 *
 * <p><b>覆盖范围(主 ObjectMapper 路径)</b>: Spring Boot 4 {@code JacksonAutoConfiguration}
 * 会把所有容器内的 {@code JacksonModule} bean 装配进主 {@code ObjectMapper}
 * ({@code JsonMapper})。该 mapper 同时被以下路径复用:
 * <ul>
 *   <li>{@code @RestController} JSON 响应序列化(包括 {@code GlobalExceptionHandler}
 *       的 {@code ErrorResponse} 渲染)</li>
 *   <li>应用日志中通过 {@code ObjectMapper.writeValueAsString(...)} 序列化的对象</li>
 * </ul>
 *
 * <p><b>不覆盖(Actuator 路径,Boot 自带防护已足够)</b>: {@code /actuator/configprops} 与
 * {@code /actuator/env} <strong>不</strong>使用主 ObjectMapper —— 它们走 Spring Boot 内置的
 * {@code JacksonBeanSerializer}(actuator 内部自构一个 {@code JsonMapper},参见
 * {@code spring-boot-actuator-4.0.6}),脱敏由 {@code Sanitizer} +
 * {@code SanitizableData.SANITIZED_VALUE} 链路完成(默认 {@code Show.NEVER} 把所有值替换为
 * {@code "******"})。两条脱敏链各自独立,但都阻止敏感字段以原值形式泄漏。
 *
 * <p>端到端覆盖见 {@code ConfigPropsMaskingIT}(spec {@code config-validation} §1.8)。
 */
@Configuration
public class JacksonSensitiveValueConfig {

    @Bean
    public JacksonModule sensitiveValueModule() {
        return new SimpleModule("SensitiveValueModule")
                .setSerializerModifier(new SensitiveValueBeanSerializerModifier());
    }
}
