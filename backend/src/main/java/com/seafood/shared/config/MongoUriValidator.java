package com.seafood.shared.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Sprint 2 §1.2 — MongoDB URI 启动期 fail-fast 校验。
 *
 * <p>Spring Boot 4 自带 {@code MongoProperties} 已绑定 {@code spring.data.mongodb.*};
 * 这里不再重复绑定,只读同一属性并在 {@code @PostConstruct} 做模式校验,缺失或不匹配
 * 即抛 {@link IllegalStateException},Spring 启动 fail-fast 退出非 0。
 *
 * <p>对应 spec: scenarios "Invalid MongoDB URI"。
 */
@Component
public class MongoUriValidator {

    /** 接受 {@code mongodb://…} 与 {@code mongodb+srv://…},后续字符任意但需非空。 */
    private static final Pattern MONGODB_URI_PATTERN = Pattern.compile("^mongodb(\\+srv)?://.+");

    private final String uri;

    public MongoUriValidator(@Value("${spring.data.mongodb.uri:}") String uri) {
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }

    @PostConstruct
    public void validate() {
        if (uri == null || uri.isBlank()) {
            throw new IllegalStateException(
                    "spring.data.mongodb.uri must not be blank — set MONGODB_URI environment variable "
                            + "to a value starting with mongodb:// or mongodb+srv://");
        }
        if (!MONGODB_URI_PATTERN.matcher(uri).matches()) {
            // PR review #9:不要把 URI 的任何子串回显到错误消息。
            // 原实现 `uri.substring(0, 16)` 会泄漏 username(`mongodb://user:...` 前 16 字符
            // 就包含 user),密码虽然在第一个 @ 之后,但 username 本身已是 PII。
            // 错误消息只描述"应当是什么格式"和"环境变量名",让运维自行查 ——
            // 这与 {code @ConfigurationProperties} 校验异常的脱敏惯例一致。
            throw new IllegalStateException(
                    "spring.data.mongodb.uri must start with mongodb:// or mongodb+srv://. "
                            + "Check the MONGODB_URI environment variable (value not echoed to avoid "
                            + "leaking embedded credentials).");
        }
    }
}
