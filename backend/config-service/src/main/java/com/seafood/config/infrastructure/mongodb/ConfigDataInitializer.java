package com.seafood.config.infrastructure.mongodb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Initializes default configuration data for all services.
 * Runs on application startup when the config_properties collection is empty.
 */
@Component
@Order(1)
public class ConfigDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ConfigDataInitializer.class);

    private final ConfigPropertyRepository repository;

    public ConfigDataInitializer(ConfigPropertyRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        // Only seed if collection is empty
        if (repository.count() > 0) {
            log.info("Config properties already exist, skipping initialization");
            return;
        }

        log.info("Initializing default configuration data...");

        // Gateway defaults
        saveProperty("gateway", "docker", "main", "server.port", "8080", false);
        saveProperty("gateway", "docker", "main", "eureka.client.service-url.defaultZone", "http://discovery-service:8761/eureka/", false);

        // Product Service defaults
        saveProperty("product-service", "docker", "main", "server.port", "8081", false);
        saveProperty("product-service", "docker", "main", "spring.data.mongodb.uri", "mongodb://mongodb:27017/seafood_product", false);
        saveProperty("product-service", "docker", "main", "eureka.client.service-url.defaultZone", "http://discovery-service:8761/eureka/", false);

        // Order Service defaults
        saveProperty("order-service", "docker", "main", "server.port", "8082", false);
        saveProperty("order-service", "docker", "main", "spring.data.mongodb.uri", "mongodb://mongodb:27017/seafood_order", false);
        saveProperty("order-service", "docker", "main", "eureka.client.service-url.defaultZone", "http://discovery-service:8761/eureka/", false);

        // User Service defaults
        saveProperty("user-service", "docker", "main", "server.port", "8083", false);
        saveProperty("user-service", "docker", "main", "spring.data.mongodb.uri", "mongodb://mongodb:27017/seafood_user", false);
        saveProperty("user-service", "docker", "main", "spring.data.redis.host", "redis", false);
        saveProperty("user-service", "docker", "main", "eureka.client.service-url.defaultZone", "http://discovery-service:8761/eureka/", false);

        // Admin UI defaults
        saveProperty("admin-ui", "docker", "main", "server.port", "8084", false);
        saveProperty("admin-ui", "docker", "main", "eureka.client.service-url.defaultZone", "http://discovery-service:8761/eureka/", false);

        // Shared application defaults (all services can inherit these)
        saveProperty("application", "docker", "main", "logging.level.root", "INFO", false);
        saveProperty("application", "docker", "main", "spring.cloud.config.enabled", "true", false);

        // Example encrypted values (for demonstration)
        // Note: In production, use POST /api/config/encryption/encrypt to generate real encrypted values
        saveProperty("application", "docker", "main", "jwt.secret", "{cipher}example-encrypted-jwt-secret", true);
        saveProperty("application", "docker", "main", "spring.datasource.password", "{cipher}example-encrypted-db-password", true);

        log.info("Configuration data initialization completed. {} properties loaded.",
                repository.count());
    }

    private void saveProperty(String serviceName, String profile, String label, String key, String value, boolean encrypted) {
        var existing = repository.findByServiceNameAndProfileAndLabelAndKey(serviceName, profile, label, key);
        if (existing.isEmpty()) {
            var doc = new ConfigPropertyDocument(serviceName, profile, label, key, value, encrypted);
            doc.setCreatedAt(Instant.now());
            doc.setUpdatedAt(Instant.now());
            repository.save(doc);
            log.debug("Saved config property: {}/{}/{}/{} = {}", serviceName, profile, label, key,
                    encrypted ? "[ENCRYPTED]" : value);
        }
    }
}
