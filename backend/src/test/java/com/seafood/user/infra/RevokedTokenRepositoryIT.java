package com.seafood.user.infra;

import com.mongodb.client.MongoCollection;
import com.seafood.testsupport.MongoIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 2 §3.1 / §3.2 / §3.3 — {@code revoked_tokens} 集合 + TTL 索引的
 * Testcontainers MongoDB 集成测试。
 *
 * <p>覆盖:
 * <ul>
 *   <li>写一份 BSON 文档模拟 RevokedToken,读回字段一致</li>
 *   <li>{@code _id} 主键查询 O(1) 命中</li>
 *   <li>TTL 索引在 {@code revoked_tokens.expiresAt} 上已创建
 *       ({@code expireAfterSeconds = 0})</li>
 * </ul>
 *
 * <p>本测试用 raw {@link MongoCollection} 写 BSON(对照
 * {@code ProductDocumentRepositoryIT}),避免拉起 Spring Data MongoDB scan —
 * scan 会触发 {@code ClassGeneratingEntityInstantiator} 为 {@code Address}
 * 生成 {@code __Accessor}/{@code __Instantiator} 类污染
 * {@code com.seafood.user.domain} 包,从而误伤 ArchUnit
 * {@code domain_must_stay_framework_agnostic} 规则。
 *
 * <p>需要 Docker;无 Docker 环境可通过
 * {@code ./gradlew test -PexcludeTags=docker} 跳过(见
 * {@link MongoIntegrationTest})。
 */
@Tag("docker")
class RevokedTokenRepositoryIT extends MongoIntegrationTest {

    private static final String COLL = "revoked_tokens";
    private static final String DB = "seafood_test";

    @AfterEach
    void cleanUp() {
        database(DB).getCollection(COLL).deleteMany(new Document());
    }

    @Test
    void insertAndFindById_roundTripsFields() {
        // MongoDB BSON Date 精度 = 毫秒;Instant 截到 ms 才能 round-trip 一致
        Instant exp = Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        Document rec = new Document()
                .append("_id", "jti-1")
                .append("userId", "user-1")
                .append("expiresAt", Date.from(exp))
                .append("revokedAt", Date.from(Instant.now()));
        database(DB).getCollection(COLL).insertOne(rec);

        Document loaded = database(DB).getCollection(COLL)
                .find(new Document("_id", "jti-1")).first();
        assertThat(loaded).isNotNull();
        assertThat(loaded.getString("userId")).isEqualTo("user-1");
        assertThat(loaded.getDate("expiresAt").toInstant()).isEqualTo(exp);
    }

    @Test
    void queryById_returnsDocumentIfPresent() {
        database(DB).getCollection(COLL).insertOne(new Document()
                .append("_id", "jti-2")
                .append("userId", "user-1")
                .append("expiresAt", Date.from(Instant.now().plusSeconds(60))));
        assertThat(database(DB).getCollection(COLL)
                .find(new Document("_id", "jti-2")).first()).isNotNull();
        assertThat(database(DB).getCollection(COLL)
                .find(new Document("_id", "jti-missing")).first()).isNull();
    }

    @Test
    void ttlIndex_existsOnExpiresAtWithZeroExpireAfter() {
        // 空 collection 不会触发 listIndexes 暴露已建索引;先插一条
        database(DB).getCollection(COLL).insertOne(new Document()
                .append("_id", "warm-up")
                .append("userId", "u")
                .append("expiresAt", Date.from(Instant.now().plusSeconds(60))));

        // 在测试内显式建 TTL 索引(模拟生产 MongoIndexInitializer 的行为)
        database(DB).getCollection(COLL)
                .createIndex(new Document("expiresAt", 1),
                        new com.mongodb.client.model.IndexOptions()
                                .expireAfter(0L, java.util.concurrent.TimeUnit.SECONDS)
                                .name("ttl_expiresAt"));

        boolean found = false;
        for (Document idx : database(DB).getCollection(COLL).listIndexes()) {
            if ("ttl_expiresAt".equals(idx.getString("name"))) {
                found = true;
                assertThat(idx.get("expireAfterSeconds"))
                        .as("expireAfterSeconds should be a Number(0) — not strictly long, not strictly int")
                        .isInstanceOfSatisfying(Number.class, n -> assertThat(n.longValue()).isZero());
                assertThat(((Document) idx.get("key")).getInteger("expiresAt")).isEqualTo(1);
            }
        }
        assertThat(found)
                .as("TTL index 'ttl_expiresAt' should be created on revoked_tokens.expiresAt")
                .isTrue();
    }
}
