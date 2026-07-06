package com.seafood.user.infra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Testcontainers
class ProductViewRepositorySliceTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Autowired
    private ProductViewRepository repo;

    private ProductViewDocument doc(String userId, String productId, Instant viewedAt) {
        ProductViewDocument d = new ProductViewDocument();
        d.setUserId(userId);
        d.setProductId(productId);
        d.setViewedAt(viewedAt);
        return d;
    }

    @Test
    void findByUserIdAndProductId_returnsMatch() {
        repo.save(doc("u1", "p1", Instant.parse("2026-07-01T00:00:00Z")));

        Optional<ProductViewDocument> found = repo.findByUserIdAndProductId("u1", "p1");

        assertThat(found).isPresent();
        assertThat(found.get().getProductId()).isEqualTo("p1");
    }

    @Test
    void findByUserIdAndProductId_noMatch_returnsEmpty() {
        assertThat(repo.findByUserIdAndProductId("u1", "nope")).isEmpty();
    }

    @Test
    void findByUserIdOrderByViewedAtDesc_sortsNewestFirst() {
        repo.save(doc("u2", "p1", Instant.parse("2026-07-01T00:00:00Z")));
        repo.save(doc("u2", "p2", Instant.parse("2026-07-03T00:00:00Z")));
        repo.save(doc("u2", "p3", Instant.parse("2026-07-02T00:00:00Z")));

        List<ProductViewDocument> result = repo.findByUserIdOrderByViewedAtDesc("u2");

        assertThat(result).extracting(ProductViewDocument::getProductId)
                .containsExactly("p2", "p3", "p1");
    }

    @Test
    void countByUserId_countsOnlyThatUser() {
        repo.save(doc("u3", "p1", Instant.now()));
        repo.save(doc("u3", "p2", Instant.now()));
        repo.save(doc("u4", "p1", Instant.now()));

        assertThat(repo.countByUserId("u3")).isEqualTo(2);
    }
}
