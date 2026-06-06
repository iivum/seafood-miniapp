package com.seafood.shared.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 2 §1.2 — MongoDB URI 启动期 fail-fast 校验。
 *
 * <p>Spring Boot 4 自带 {@code MongoProperties} 已绑定 {@code spring.data.mongodb.*},
 * 重复绑定会冲突;改由独立 {@link MongoUriValidator} 在 {@code @PostConstruct} 读取
 * 同一属性并校验,缺失 / 空 / 协议错误均抛 {@link IllegalStateException}。
 */
class MongoUriValidatorTest {

    @Test
    void acceptsValidMongodbUri() {
        MongoUriValidator validator = new MongoUriValidator("mongodb://localhost:27017/seafood");

        assertThat(validator.getUri()).isEqualTo("mongodb://localhost:27017/seafood");
        // 不抛 = 通过校验
        validator.validate();
    }

    @Test
    void acceptsValidMongodbSrvUri() {
        MongoUriValidator validator = new MongoUriValidator(
                "mongodb+srv://user:pw@cluster.mongodb.net/seafood");

        validator.validate();
    }

    @Test
    void rejectsBlankUri() {
        MongoUriValidator validator = new MongoUriValidator("");

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.data.mongodb.uri");
    }

    @Test
    void rejectsWhitespaceOnlyUri() {
        MongoUriValidator validator = new MongoUriValidator("   ");

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.data.mongodb.uri");
    }

    @Test
    void rejectsHttpScheme() {
        MongoUriValidator validator = new MongoUriValidator("http://localhost:27017/seafood");

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mongodb://")
                .hasMessageContaining("spring.data.mongodb.uri");
    }

    @Test
    void rejectsBareHostname() {
        MongoUriValidator validator = new MongoUriValidator("localhost:27017");

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mongodb://");
    }

    /**
     * PR review #9 — 启动期错误消息<em>绝不</em>回显 URI 的子串,否则会泄漏
     * 内嵌凭据的 username。`mongodb://alice:hunter2@db.example.com/...` 前 16
     * 字符是 {@code mongodb://alic},包含用户名。原实现就是这么写。
     *
     * <p>用 {@code postgres://...} 而非 {@code mongodb://...} 触发校验失败 ——
     * mongo URI 本身能通过校验,无法触发错误消息路径。
     */
    @Test
    void invalidUriErrorDoesNotLeakEmbeddedCredentials() {
        // 错的协议 + 内嵌凭据;目标是触发 schema 校验失败同时验证错误消息不泄漏。
        String sensitiveUri = "postgres://alice:hunter2@db.example.com:5432/seafood";
        MongoUriValidator validator = new MongoUriValidator(sensitiveUri);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                // 关键:不出现任何 URI 子串
                .satisfies(thrown -> {
                    String msg = thrown.getMessage();
                    org.assertj.core.api.Assertions.assertThat(msg)
                            .as("error message must not echo ANY substring of the URI")
                            .doesNotContain("alice")
                            .doesNotContain("hunter2")
                            .doesNotContain("db.example.com")
                            .doesNotContain("postgres")
                            .doesNotContain(sensitiveUri);
                })
                // 但要告诉运维去哪里看
                .hasMessageContaining("MONGODB_URI")
                .hasMessageContaining("mongodb://");
    }
}
