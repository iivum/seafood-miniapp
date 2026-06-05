package com.seafood.shared.config;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 2 §1.5 — 敏感值脱敏器单元测试(Jackson 3.x)。
 *
 * <p>规则(spec config-validation):「显示前 4 个字符,后接 ***」。短串(<4)显示原值 + ***,
 * 空串 → ***,null 透传由 Jackson 自身决定。
 *
 * <p>测试分两层:{@link #masksXxx()} 直接断言静态 {@code mask()} 规则;
 * {@link #integratesWithJacksonViaModule()} 用真实 ObjectMapper + Module 注册路径验证
 * 末端 JSON 行为。
 */
class SensitiveValueMaskerTest {

    @Test
    void masksLongString() {
        assertThat(SensitiveValueMasker.mask("topsecret123")).isEqualTo("tops***");
    }

    @Test
    void masksLeavesExactlyFourPrefixChars() {
        assertThat(SensitiveValueMasker.mask("abcdefghijklmnop")).isEqualTo("abcd***");
    }

    @Test
    void masksShortStringByAppendingStars() {
        assertThat(SensitiveValueMasker.mask("ab")).isEqualTo("ab***");
    }

    @Test
    void masksEmptyStringAsStarsOnly() {
        assertThat(SensitiveValueMasker.mask("")).isEqualTo("***");
    }

    @Test
    void masksExactlyFourCharString() {
        assertThat(SensitiveValueMasker.mask("1234")).isEqualTo("1234***");
    }

    @Test
    void masksNullDefensively() {
        assertThat(SensitiveValueMasker.mask(null)).isEqualTo("***");
    }

    /**
     * 走完整 Jackson 路径:把 {@link SensitiveValueMasker} 注册成 String 的全局
     * 序列化器,确认末端 JSON 字面值含 {@code ***}。这层验证 Jackson 3 的
     * {@code ValueSerializer} API 接得上,而不是只测自家方法。
     */
    @Test
    void integratesWithJacksonViaModule() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(String.class, new SensitiveValueMasker());
        ObjectMapper mapper = JsonMapper.builder().addModule(module).build();

        String json = mapper.writeValueAsString("topsecret123");

        assertThat(json).isEqualTo("\"tops***\"");
    }
}
