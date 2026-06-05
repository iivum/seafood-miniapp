package com.seafood.testsupport;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers base class for MongoDB integration tests.
 *
 * <p>Boots a single shared {@code mongo:7} container per test JVM. Subclasses can
 * obtain a {@link MongoClient} via {@link #mongoClient()} to interact with the
 * container's replica set.
 *
 * <p>Tagged {@code docker} so a Docker-less run can skip via:
 * <pre>./gradlew test -PexcludeTags=docker</pre>
 *
 * <p>Note: Spring Boot 4.0.6's {@code spring-boot-starter-test} does not bundle
 * {@code @DataMongoTest} slice annotations, so this base class drives a raw
 * {@link MongoClient} instead. Subclasses that need Spring Data MongoDB can
 * autowire the container's URI via {@link #mongoUri()}.
 */
@Tag("docker")
@Testcontainers
public abstract class MongoIntegrationTest {

    @Container
    @SuppressWarnings("resource") // lifecycle managed by Testcontainers
    protected static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    private static MongoClient client;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
    }

    @AfterAll
    static void disconnect() {
        if (client != null) {
            client.close();
        }
    }

    protected static String mongoUri() {
        return MONGO.getReplicaSetUrl();
    }

    protected static MongoClient mongoClient() {
        return client;
    }

    protected static MongoDatabase database(String name) {
        return client.getDatabase(name);
    }
}
