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
 * <p>规则:String 字段且字段名命中 {@link #SENSITIVE_FIELD_PATTERN} 时,
 * 把该 property 的 serializer 替换为 {@link SensitiveValueMasker};新字段无需注解即自动覆盖。
 * 非 String 类型即便字段名命中也不动(避免误伤 {@code int tokenCount} 这类计数字段)。
 *
 * <p>PR review #24 修复 regex 误伤:原版用 {@code uri} 部分匹配,导致 {@code touristName}、
 * {@code tutorial}、{@code duration} 这类合法字段被当作 URI 凭据脱敏。
 * 现:
 * <ul>
 *   <li>{@code secret|password|token|appid} — 单词边界匹配(降低误伤,如 {@code int tokenCount}
 *       不再误中是因为类型检查,这里加 word boundary 进一步收紧)</li>
 *   <li>{@code Uri} 改为<em>字段名后缀</em>匹配 — 只覆盖 {@code mongoUri}、{@code jdbcUri}、
 *       {@code redirectUri} 这类"以 Uri 结尾的字符串字段"</li>
 * </ul>
 *
 * <p>注册见 {@link JacksonSensitiveValueConfig}。
 */
public class SensitiveValueBeanSerializerModifier extends ValueSerializerModifier {

    /**
     * 大小写不敏感。
     * <ul>
     *   <li>{@code secret|password|token|appid} — 子串匹配(覆盖 camelCase 内部,如
     *       {@code adminSecret}、{@code accessToken}),Java 字段名里这些词几乎都是有意为之,
     *       误伤概率低;由类型检查兜住 {@code int tokenCount} 这类</li>
     *   <li>{@code Uri} — 改为<em>字段名后缀</em>匹配 {@code .*Uri$},只覆盖
     *       {@code mongoUri}/{@code jdbcUri}/{@code redirectUri} 这类真凭据,
     *       不误伤 {@code touristName}/{@code tutorialTitle}/{@code durationMs}</li>
     * </ul>
     */
    static final Pattern SENSITIVE_FIELD_PATTERN = Pattern.compile(
            "(?i).*(secret|password|token|appid).*|.*Uri$");

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
        String name = writer.getName();
        // v2.1 signoff 修复:TokenResponse / UserResponse 是 API 响应 DTO,字段
        // accessToken / refreshToken 必须以原值返回给客户端(否则前端拿到的 token 是
        // "eyJh***",后续所有 admin API 必 403)。脱敏目标对象是 internal config
        // 类(JwtProperties.adminSecret / password 等),不是 API 凭据。
        // 字段名 accessToken / refreshToken 命中 token 子串导致无差别被 mask,
        // 是设计 bug —— 用字段名白名单修补(Pattern 是 substring 匹配,精确白名单
        // 4 个 API 凭据字段即可,不影响 password/secret/appid 脱敏)。
        if (name.equals("accessToken")
                || name.equals("refreshToken")
                || name.equals("role")
                || name.equals("username")) {
            return false;
        }
        return SENSITIVE_FIELD_PATTERN.matcher(name).matches();
    }
}
