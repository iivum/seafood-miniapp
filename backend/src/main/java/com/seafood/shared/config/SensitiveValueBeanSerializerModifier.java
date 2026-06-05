package com.seafood.shared.config;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Sprint 2 §1.6 — 全局按字段名脱敏的 Jackson 3 {@link ValueSerializerModifier}。
 *
 * <p>规则:String 字段且字段名命中 {@code (?i).*(secret|password|uri|token|appid).*} 时,
 * 把该 property 的 serializer 替换为 {@link SensitiveValueMasker};新字段无需注解即自动覆盖。
 * 非 String 类型即便字段名命中也不动(避免误伤 {@code int tokenCount} 这类计数字段)。
 *
 * <p>注册见 {@link JacksonSensitiveValueConfig}。
 */
public class SensitiveValueBeanSerializerModifier extends ValueSerializerModifier {

    /** 大小写不敏感,部分匹配。例:{@code accessToken}、{@code adminSecret}、{@code apiUri}。 */
    static final Pattern SENSITIVE_FIELD_PATTERN =
            Pattern.compile("(?i).*(secret|password|uri|token|appid).*");

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<BeanPropertyWriter> changeProperties(
            SerializationConfig config,
            BeanDescription.Supplier beanDesc,
            List<BeanPropertyWriter> beanProperties) {
        for (BeanPropertyWriter writer : beanProperties) {
            if (shouldMask(writer)) {
                writer.assignSerializer((ValueSerializer) new SensitiveValueMasker());
            }
        }
        return beanProperties;
    }

    private static boolean shouldMask(BeanPropertyWriter writer) {
        if (writer.getType().getRawClass() != String.class) {
            return false;
        }
        return SENSITIVE_FIELD_PATTERN.matcher(writer.getName()).matches();
    }
}
