package com.seafood.banner.infra;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.seafood.testsupport.MongoIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test:写 banner 文档到真实 Testcontainers MongoDB,读回断言 BSON 级 round-trip。
 *
 * <p>{@code @Tag("native")} — repository-IT 切片样本,让 nativeTest agent 捕获 banners
 * 集合的 codec/反射条目(BannerTone/BannerStatus 枚举 + Instant)。
 */
@Tag("native")
class BannerDocumentRepositoryIT extends MongoIntegrationTest {

    @Test
    void roundTrip_preservesFields() {
        MongoCollection<Document> banners = database("seafood_test").getCollection("banners");
        banners.deleteMany(new Document());

        banners.insertOne(new Document()
                .append("tone", "ACCENT")
                .append("emoji", "🦞")
                .append("title", "波龙季 返场")
                .append("subtitle", "鲜活到岸 · 满 1 只减 30")
                .append("targetProductId", "p-1")
                .append("sortOrder", 0)
                .append("status", "ACTIVE")
                .append("createdAt", java.util.Date.from(
                        java.time.Instant.parse("2026-06-20T00:00:00Z"))));

        Document loaded = banners.find(Filters.eq("title", "波龙季 返场")).first();

        assertThat(loaded).isNotNull();
        assertThat(loaded.getString("tone")).isEqualTo("ACCENT");
        assertThat(loaded.getString("emoji")).isEqualTo("🦞");
        assertThat(loaded.getString("subtitle")).isEqualTo("鲜活到岸 · 满 1 只减 30");
        assertThat(loaded.getString("targetProductId")).isEqualTo("p-1");
        assertThat(loaded.getInteger("sortOrder")).isEqualTo(0);
        assertThat(loaded.getString("status")).isEqualTo("ACTIVE");
    }
}
