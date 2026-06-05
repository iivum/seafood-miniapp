package com.seafood.product.infra;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.seafood.testsupport.MongoIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test: write a product document to the real Testcontainers MongoDB,
 * read it back, assert round-trip preservation at the BSON level.
 *
 * <p>Drives the raw MongoDB driver to avoid depending on Spring Data slice
 * annotations that Boot 4.0.6's test starter does not bundle.
 */
class ProductDocumentRepositoryIT extends MongoIntegrationTest {

    @Test
    void roundTrip_preservesFields() {
        MongoCollection<Document> products = database("seafood_test")
                .getCollection("products");
        products.drop();

        products.insertOne(new Document()
                .append("name", "三文鱼")
                .append("description", "新鲜")
                .append("price", 99.00)
                .append("stock", 10)
                .append("category", "鱼类")
                .append("imageUrl", "http://img")
                .append("onSale", true)
                .append("status", "ACTIVE")
                .append("createdAt", java.util.Date.from(
                        java.time.Instant.parse("2026-06-05T00:00:00Z"))));

        Document loaded = products.find(Filters.eq("name", "三文鱼")).first();

        assertThat(loaded).isNotNull();
        assertThat(loaded.getString("name")).isEqualTo("三文鱼");
        assertThat(loaded.getDouble("price")).isEqualTo(99.00);
        assertThat(loaded.getInteger("stock")).isEqualTo(10);
        assertThat(loaded.getString("category")).isEqualTo("鱼类");
        assertThat(loaded.getString("status")).isEqualTo("ACTIVE");
        assertThat(loaded.getBoolean("onSale")).isTrue();
    }
}
