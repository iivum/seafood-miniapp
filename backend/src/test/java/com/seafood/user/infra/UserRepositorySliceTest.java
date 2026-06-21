package com.seafood.user.infra;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.seafood.testsupport.MongoIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositorySliceTest extends MongoIntegrationTest {

    private static final String COLL = "users";
    private static final String DB = "seafood_test";
    private static final java.time.Instant T = java.time.Instant.parse("2026-06-19T00:00:00Z");

    private MongoCollection<Document> users() {
        return database(DB).getCollection(COLL);
    }

    @BeforeEach
    void clearCollection() {
        users().deleteMany(new Document());
    }

    private Document userDoc(String id, String openId, String nickname) {
        return new Document()
            .append("_id", id)
            .append("openId", openId)
            .append("nickname", nickname)
            .append("role", "CUSTOMER")
            .append("createdAt", Date.from(T));
    }

    @Test
    void save_thenFindById_returnsSameUser() {
        users().insertOne(userDoc("u-test-1", "open-abc", "测试用户"));

        Document loaded = users().find(Filters.eq("_id", "u-test-1")).first();

        assertThat(loaded).isNotNull();
        assertThat(loaded.getString("openId")).isEqualTo("open-abc");
        assertThat(loaded.getString("nickname")).isEqualTo("测试用户");
        assertThat(loaded.getString("role")).isEqualTo("CUSTOMER");
    }

    @Test
    void findByOpenId_returnsUser() {
        users().insertOne(userDoc("u-open-1", "open-xyz", "Xyz"));

        Document loaded = users().find(Filters.eq("openId", "open-xyz")).first();

        assertThat(loaded).isNotNull();
        assertThat(loaded.getString("_id")).isEqualTo("u-open-1");
    }

    @Test
    void findByOpenId_unknownReturnsNull() {
        assertThat(users().find(Filters.eq("openId", "missing")).first()).isNull();
    }

    @Test
    void deleteById_removesUser() {
        users().insertOne(userDoc("u-del", "open-del", "Del"));

        users().deleteOne(Filters.eq("_id", "u-del"));

        assertThat(users().find(Filters.eq("_id", "u-del")).first()).isNull();
    }
}
