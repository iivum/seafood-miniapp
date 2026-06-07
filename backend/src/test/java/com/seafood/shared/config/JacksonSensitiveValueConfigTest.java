package com.seafood.shared.config;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 2 §1.7 — 把 {@link SensitiveValueBeanSerializerModifier} 装配成 Spring
 * 可发现的 {@code JacksonModule} bean,让 Spring Boot 主 ObjectMapper 与 Actuator 的
 * configprops serializer 自动应用。
 */
class JacksonSensitiveValueConfigTest {

    private final JacksonSensitiveValueConfig config = new JacksonSensitiveValueConfig();

    @Test
    void registersJacksonModuleNamed() {
        JacksonModule module = config.sensitiveValueModule();
        assertThat(module).isNotNull();
        assertThat(module.getModuleName()).contains("Sensitive");
    }

    @Test
    void registeredModuleApplyMaskingToSensitiveFields() {
        record Bean(String secret, String name) {}
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(config.sensitiveValueModule())
                .build();

        String json = mapper.writeValueAsString(new Bean("topsecret", "alice"));

        assertThat(json).contains("\"secret\":\"tops***\"");
        assertThat(json).contains("\"name\":\"alice\"");
    }
}
