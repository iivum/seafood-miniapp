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
}
