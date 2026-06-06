package com.seafood.shared.config;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 2 §1.6 — 按字段名正则匹配的全局脱敏 modifier。
 *
 * <p>规则:字段名命中 {@code (?i).*(secret|password|uri|token|appid).*} 且类型为 String
 * 时,替换 serializer 为 {@link SensitiveValueMasker};新字段无需注解即自动覆盖。
 */
class SensitiveValueBeanSerializerModifierTest {

    private ObjectMapper objectMapper() {
        SimpleModule module = new SimpleModule();
        module.setSerializerModifier(new SensitiveValueBeanSerializerModifier());
        return JsonMapper.builder().addModule(module).build();
    }

    @Test
    void masksFieldNamedSecret() {
        record Bean(String secret) {}
        String json = objectMapper().writeValueAsString(new Bean("topsecret"));
        assertThat(json).contains("\"secret\":\"tops***\"");
    }

    @Test
    void masksFieldNamedAdminSecret() {
        record Bean(String adminSecret) {}
        String json = objectMapper().writeValueAsString(new Bean("admin-key-very-long"));
        assertThat(json).contains("\"adminSecret\":\"admi***\"");
    }

    @Test
    void masksFieldNamedPassword() {
        record Bean(String password) {}
        String json = objectMapper().writeValueAsString(new Bean("hunter2-the-classic"));
        assertThat(json).contains("\"password\":\"hunt***\"");
    }

    @Test
    void masksFieldNamedUri() {
        // 字段名就叫 "uri" — 命中 *Uri$ 规则(以 Uri 结尾)
        record Bean(String uri) {}
        String json = objectMapper().writeValueAsString(new Bean("mongodb://user:pw@host"));
        assertThat(json).contains("\"uri\":\"mong***\"");
    }

    @Test
    void masksFieldEndingInUri() {
        // PR review #24:常见 URI 字段命名(后缀 Uri)— 仍应被脱敏
        record Bean(String mongoUri, String jdbcUri, String redirectUri) {}
        String json = objectMapper().writeValueAsString(
                new Bean("mongodb://u:p@h", "jdbc:postgresql://x", "https://example.com/cb"));
        assertThat(json).contains("\"mongoUri\":\"mong***\"")
                .contains("\"jdbcUri\":\"jdbc***\"")
                .contains("\"redirectUri\":\"http***\"");
    }

    @Test
    void doesNotMaskWordsContainingUriSubstring() {
        // PR review #24 回归保护:false positive 防御。原 regex `(?i).*uri.*`
        // 会把这些合法字段误判为 URI 凭据。修复后 `.*Uri$` 只匹配后缀。
        record Bean(String touristName, String tutorialTitle, String durationMs, String curious) {}
        String json = objectMapper().writeValueAsString(
                new Bean("Alice", "How to cook", "PT5M", "wonder"));
        assertThat(json)
                .as("none of these may be masked — none end in 'Uri'")
                .contains("\"touristName\":\"Alice\"")
                .contains("\"tutorialTitle\":\"How to cook\"")
                .contains("\"durationMs\":\"PT5M\"")
                .contains("\"curious\":\"wonder\"")
                .doesNotContain("***");
    }

    @Test
    void masksFieldNamedToken() {
        record Bean(String accessToken) {}
        String json = objectMapper().writeValueAsString(new Bean("eyJhbGciOi"));
        assertThat(json).contains("\"accessToken\":\"eyJh***\"");
    }

    @Test
    void masksFieldNamedAppid() {
        record Bean(String appid) {}
        String json = objectMapper().writeValueAsString(new Bean("wx1234567890"));
        assertThat(json).contains("\"appid\":\"wx12***\"");
    }

    @Test
    void caseInsensitiveMatchOnFieldName() {
        // record getter "getSECRET" becomes property "SECRET" in JSON
        record Bean(String SECRET) {}
        String json = objectMapper().writeValueAsString(new Bean("uppercase"));
        assertThat(json).contains("\"SECRET\":\"uppe***\"");
        assertThat(json).doesNotContain("uppercase");
    }

    @Test
    void doesNotMaskNonSensitiveFields() {
        record Bean(String name, String productId, String userName, int age) {}
        String json = objectMapper().writeValueAsString(new Bean("alice", "p-100", "alice@x", 30));

        assertThat(json).contains("\"name\":\"alice\"");
        assertThat(json).contains("\"productId\":\"p-100\"");
        assertThat(json).contains("\"userName\":\"alice@x\"");
        assertThat(json).contains("\"age\":30");
        assertThat(json).doesNotContain("***");
    }

    @Test
    void doesNotMaskNonStringFieldsEvenIfNameMatches() {
        record Bean(int tokenCount) {}
        String json = objectMapper().writeValueAsString(new Bean(42));
        assertThat(json).contains("\"tokenCount\":42");
        assertThat(json).doesNotContain("***");
    }
}
