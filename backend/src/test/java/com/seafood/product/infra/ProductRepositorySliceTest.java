package com.seafood.product.infra;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.seafood.testsupport.MongoIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRepositorySliceTest extends MongoIntegrationTest {

    private static final String COLL = "products";
    private static final String DB = "seafood_test";
    private static final java.time.Instant T = java.time.Instant.parse("2026-06-19T00:00:00Z");

    private MongoCollection<Document> products() {
        return database(DB).getCollection(COLL);
    }

    @BeforeEach
    void clearCollection() {
        products().deleteMany(new Document());
    }

    private Document productDoc(String id, String name, String category, String status, boolean onSale) {
        return new Document()
            .append("_id", id)
            .append("name", name)
            .append("description", "desc")
            .append("price", 10.0)
            .append("stock", 1)
            .append("category", category)
            .append("imageUrl", "http://img")
            .append("onSale", onSale)
            .append("status", status)
            .append("createdAt", Date.from(T))
            .append("updatedAt", Date.from(T));
    }

    @Test
    void save_thenFindById_returnsSameProduct() {
        products().insertOne(productDoc("p-test-1", "三文鱼", "鱼类", "ACTIVE", true));

        Document loaded = products().find(Filters.eq("_id", "p-test-1")).first();

        assertThat(loaded).isNotNull();
        assertThat(loaded.getString("name")).isEqualTo("三文鱼");
        assertThat(loaded.getString("category")).isEqualTo("鱼类");
        assertThat(loaded.getBoolean("onSale")).isTrue();
    }

    @Test
    void findByCategory_filtersResults() {
        products().insertOne(productDoc("p-fish", "鱼1", "鱼类", "ACTIVE", true));
        products().insertOne(productDoc("p-shrimp", "虾1", "虾蟹", "ACTIVE", true));

        long fishCount = products().countDocuments(Filters.eq("category", "鱼类"));

        assertThat(fishCount).isEqualTo(1L);
    }

    @Test
    void countByStatus_returnsActiveCount() {
        products().insertOne(productDoc("p-active", "在售", "鱼类", "ACTIVE", true));
        products().insertOne(productDoc("p-discontinued", "下架", "鱼类", "DISCONTINUED", false));

        long activeCount = products().countDocuments(Filters.eq("status", "ACTIVE"));

        assertThat(activeCount).isEqualTo(1L);
    }

    @Test
    void deleteById_removesProduct() {
        products().insertOne(productDoc("p-del", "删", "鱼类", "ACTIVE", true));

        products().deleteOne(Filters.eq("_id", "p-del"));

        assertThat(products().find(Filters.eq("_id", "p-del")).first()).isNull();
    }
}
