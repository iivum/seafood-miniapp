package com.seafood.order.infra;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.seafood.testsupport.MongoIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class RefundRepositorySliceTest extends MongoIntegrationTest {

    private static final String COLL = "refunds";
    private static final String DB = "seafood_test";
    private static final java.time.Instant T = java.time.Instant.parse("2026-06-19T00:00:00Z");

    private MongoCollection<Document> refunds() {
        return database(DB).getCollection(COLL);
    }

    @BeforeEach
    void clearCollection() {
        refunds().deleteMany(new Document());
    }

    private Document refundDoc(String id, String orderId, String status) {
        return new Document()
            .append("_id", id)
            .append("orderId", orderId)
            .append("userId", "u-1")
            .append("amount", 50.0)
            .append("reason", "不再需要")
            .append("status", status)
            .append("createdAt", Date.from(T))
            .append("updatedAt", Date.from(T));
    }

    @Test
    void save_thenFindById_returnsSameRefund() {
        refunds().insertOne(refundDoc("r-test-1", "o-1", "REQUESTED"));

        Document loaded = refunds().find(Filters.eq("_id", "r-test-1")).first();

        assertThat(loaded).isNotNull();
        assertThat(loaded.getString("orderId")).isEqualTo("o-1");
        assertThat(loaded.getString("status")).isEqualTo("REQUESTED");
        assertThat(loaded.getDouble("amount")).isEqualTo(50.0);
    }

    @Test
    void findByOrderId_returnsRefund() {
        refunds().insertOne(refundDoc("r-order", "o-X", "REQUESTED"));

        Document loaded = refunds().find(Filters.eq("orderId", "o-X")).first();

        assertThat(loaded).isNotNull();
        assertThat(loaded.getString("_id")).isEqualTo("r-order");
    }

    @Test
    void findByStatus_filtersByStringStatus() {
        refunds().insertOne(refundDoc("r-req", "o-1", "REQUESTED"));
        refunds().insertOne(refundDoc("r-app", "o-2", "APPROVED"));

        long requestedCount = refunds().countDocuments(Filters.eq("status", "REQUESTED"));

        assertThat(requestedCount).isEqualTo(1L);
    }

    @Test
    void deleteById_removesRefund() {
        refunds().insertOne(refundDoc("r-del", "o-1", "REQUESTED"));

        refunds().deleteOne(Filters.eq("_id", "r-del"));

        assertThat(refunds().find(Filters.eq("_id", "r-del")).first()).isNull();
    }
}
