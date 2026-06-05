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
            throw new IllegalStateException(
                    "spring.data.mongodb.uri must start with mongodb:// or mongodb+srv:// "
                            + "(got: " + uri.substring(0, Math.min(uri.length(), 16)) + "***)");
        }
    }
}
