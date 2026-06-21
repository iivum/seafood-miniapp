package com.seafood.order.infra;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.seafood.testsupport.MongoIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRepositorySliceTest extends MongoIntegrationTest {

    private static final String COLL = "orders";
    private static final String DB = "seafood_test";
    private static final java.time.Instant T = java.time.Instant.parse("2026-06-19T00:00:00Z");

    private MongoCollection<Document> orders() {
        return database(DB).getCollection(COLL);
    }

    @BeforeEach
    void clearCollection() {
        orders().deleteMany(new Document());
    }

    private Document orderDoc(String id, String userId, String status) {
        return new Document()
            .append("_id", id)
            .append("userId", userId)
            .append("status", status)
            .append("items", List.of())
            .append("totalAmount", 0)
            .append("createdAt", Date.from(T))
            .append("updatedAt", Date.from(T));
    }

    @Test
    void save_thenFindById_returnsSameOrder() {
        orders().insertOne(orderDoc("o-test-1", "u-1", "PENDING")
            .append("items", List.of(
                new Document("productId", "p-1")
                    .append("productName", "三文鱼")
                    .append("unitPrice", 99.00)
                    .append("quantity", 2)))
            .append("totalAmount", 198.00));

        Document loaded = orders().find(Filters.eq("_id", "o-test-1")).first();

        assertThat(loaded).isNotNull();
        assertThat(loaded.getString("userId")).isEqualTo("u-1");
        assertThat(loaded.getString("status")).isEqualTo("PENDING");
        assertThat(loaded.getList("items", Document.class)).hasSize(1);
        assertThat(loaded.getList("items", Document.class).get(0).getString("productId")).isEqualTo("p-1");
        assertThat(loaded.getDouble("totalAmount")).isEqualTo(198.00);
    }

    @Test
    void findById_unknown_returnsEmpty() {
        assertThat(orders().find(Filters.eq("_id", "nonexistent")).first()).isNull();
    }

    @Test
    void deleteById_removesOrder() {
        orders().insertOne(orderDoc("o-del", "u-1", "PENDING"));

        orders().deleteOne(Filters.eq("_id", "o-del"));

        assertThat(orders().find(Filters.eq("_id", "o-del")).first()).isNull();
    }

    @Test
    void findByUserId_filtersByUserId() {
        orders().insertOne(orderDoc("o-u-1", "u-1", "PENDING"));
        orders().insertOne(orderDoc("o-u-2", "u-2", "PENDING"));

        long count = orders().countDocuments(Filters.eq("userId", "u-1"));

        assertThat(count).isEqualTo(1L);
    }
}
