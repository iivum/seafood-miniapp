package com.seafood.shared.config;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Sprint 2 §1.5 — 敏感字符串脱敏器(Spring Boot 4 / Jackson 3.x)。
 *
 * <p>规则:显示前 4 个字符,后接 {@code ***}。
 * 短串(<4)显示全部原值 + {@code ***};空串 → {@code ***};null 透传由 Jackson 处理
 * (Jackson 在 {@code value == null} 时不会调用本 {@code serialize})。
 *
 * <p>注册路径见 {@link SensitiveValueBeanSerializerModifier}。
 */
public class SensitiveValueMasker extends ValueSerializer<String> {

    static final int PREFIX_LENGTH = 4;
    static final String MASK_SUFFIX = "***";

    @Override
    public void serialize(String value, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeString(mask(value));
    }

    static String mask(String value) {
        if (value == null) {
            // 防御性:Jackson 通常不会在 null 时调用 serialize
            return MASK_SUFFIX;
        }
        if (value.isEmpty()) {
            return MASK_SUFFIX;
        }
        int cut = Math.min(value.length(), PREFIX_LENGTH);
        return value.substring(0, cut) + MASK_SUFFIX;
    }
}
