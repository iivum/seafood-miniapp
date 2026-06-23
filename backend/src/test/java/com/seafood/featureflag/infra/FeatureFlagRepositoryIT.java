package com.seafood.featureflag.infra;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.seafood.testsupport.MongoIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/**
 * feature_flags collection round-trip IT（Testcontainers mongo:7 原生驱动）。
 *
 * <p>Spring Boot 4.0.6 test starter 未打包 {@code @DataMongoTest}，
 * 故与 {@link com.seafood.product.infra.ProductDocumentRepositoryIT} 一样
 * 走 raw MongoClient 验证 BSON 字段往返。
 */
@Tag("docker")
class FeatureFlagRepositoryIT extends MongoIntegrationTest {

    private MongoCollection<Document> collection;

    @BeforeEach
    void setUp() {
        collection = database("seafood_test").getCollection("feature_flags");
        collection.deleteMany(new Document());
    }

    @Test
    void findByFlagKey_returnsDocument() {
        collection.insertOne(flagDoc("ff-test", true, 50));
        Document found = collection.find(Filters.eq("flagKey", "ff-test")).first();
        assertThat(found).isNotNull();
        assertThat(found.getString("flagKey")).isEqualTo("ff-test");
    }

    @Test
    void findAllByEnabledTrue_returnsOnlyEnabled() {
        collection.insertOne(flagDoc("ff-on", true, 50));
        collection.insertOne(flagDoc("ff-off", false, 50));

        long enabledCount = collection.countDocuments(Filters.eq("enabled", true));
        long disabledCount = collection.countDocuments(Filters.eq("enabled", false));

        assertThat(enabledCount).isGreaterThanOrEqualTo(1);
        assertThat(disabledCount).isGreaterThanOrEqualTo(1);

        // 确认 enabled=true 的记录中有 ff-on，没有 ff-off
        Document found = collection.find(
                Filters.and(Filters.eq("enabled", true), Filters.eq("flagKey", "ff-on"))
        ).first();
        assertThat(found).isNotNull();

        Document notFound = collection.find(
                Filters.and(Filters.eq("enabled", true), Filters.eq("flagKey", "ff-off"))
        ).first();
        assertThat(notFound).isNull();
    }

    @Test
    void save_setsUpdatedAt() {
        Instant before = Instant.now();
        collection.insertOne(flagDoc("ff-ts", true, 100));
        Document saved = collection.find(Filters.eq("flagKey", "ff-ts")).first();
        assertThat(saved).isNotNull();
        assertThat(saved.getDate("updatedAt")).isNotNull();
        assertThat(saved.getDate("updatedAt").toInstant()).isAfterOrEqualTo(before.minusSeconds(1));
    }

    private static Document flagDoc(String flagKey, boolean enabled, int rolloutPct) {
        return new Document()
                .append("flagKey", flagKey)
                .append("enabled", enabled)
                .append("rolloutPercentage", rolloutPct)
                .append("userSegments", List.of())
                .append("description", "test flag")
                .append("createdBy", "admin")
                .append("createdAt", java.util.Date.from(Instant.now()))
                .append("updatedAt", java.util.Date.from(Instant.now()));
    }
}
