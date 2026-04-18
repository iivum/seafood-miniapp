// MongoDB initialization script for config-service
// Usage: mongosh mongodb://localhost:27017/seafood < init-config.js

// Create the config_properties collection with compound index
db.config_properties.createIndex(
    { "serviceName": 1, "profile": 1, "label": 1, "key": 1 },
    { unique: true, name: "service_profile_label_key" }
);

print("Created index on config_properties collection");

// Gateway defaults
db.config_properties.insertMany([
    { serviceName: "gateway", profile: "docker", label: "main", key: "server.port", value: "8080", encrypted: false, createdAt: new Date(), updatedAt: new Date() },
    { serviceName: "gateway", profile: "docker", label: "main", key: "eureka.client.service-url.defaultZone", value: "http://discovery-service:8761/eureka/", encrypted: false, createdAt: new Date(), updatedAt: new Date() }
]);

// Product Service defaults
db.config_properties.insertMany([
    { serviceName: "product-service", profile: "docker", label: "main", key: "server.port", value: "8081", encrypted: false, createdAt: new Date(), updatedAt: new Date() },
    { serviceName: "product-service", profile: "docker", label: "main", key: "spring.data.mongodb.uri", value: "mongodb://mongodb:27017/seafood_product", encrypted: false, createdAt: new Date(), updatedAt: new Date() },
    { serviceName: "product-service", profile: "docker", label: "main", key: "eureka.client.service-url.defaultZone", value: "http://discovery-service:8761/eureka/", encrypted: false, createdAt: new Date(), updatedAt: new Date() }
]);

// Order Service defaults
db.config_properties.insertMany([
    { serviceName: "order-service", profile: "docker", label: "main", key: "server.port", value: "8082", encrypted: false, createdAt: new Date(), updatedAt: new Date() },
    { serviceName: "order-service", profile: "docker", label: "main", key: "spring.data.mongodb.uri", value: "mongodb://mongodb:27017/seafood_order", encrypted: false, createdAt: new Date(), updatedAt: new Date() },
    { serviceName: "order-service", profile: "docker", label: "main", key: "eureka.client.service-url.defaultZone", value: "http://discovery-service:8761/eureka/", encrypted: false, createdAt: new Date(), updatedAt: new Date() }
]);

// User Service defaults
db.config_properties.insertMany([
    { serviceName: "user-service", profile: "docker", label: "main", key: "server.port", value: "8083", encrypted: false, createdAt: new Date(), updatedAt: new Date() },
    { serviceName: "user-service", profile: "docker", label: "main", key: "spring.data.mongodb.uri", value: "mongodb://mongodb:27017/seafood_user", encrypted: false, createdAt: new Date(), updatedAt: new Date() },
    { serviceName: "user-service", profile: "docker", label: "main", key: "spring.data.redis.host", value: "redis", encrypted: false, createdAt: new Date(), updatedAt: new Date() },
    { serviceName: "user-service", profile: "docker", label: "main", key: "eureka.client.service-url.defaultZone", value: "http://discovery-service:8761/eureka/", encrypted: false, createdAt: new Date(), updatedAt: new Date() }
]);

// Admin UI defaults
db.config_properties.insertMany([
    { serviceName: "admin-ui", profile: "docker", label: "main", key: "server.port", value: "8084", encrypted: false, createdAt: new Date(), updatedAt: new Date() },
    { serviceName: "admin-ui", profile: "docker", label: "main", key: "eureka.client.service-url.defaultZone", value: "http://discovery-service:8761/eureka/", encrypted: false, createdAt: new Date(), updatedAt: new Date() }
]);

// Shared application defaults
db.config_properties.insertMany([
    { serviceName: "application", profile: "docker", label: "main", key: "logging.level.root", value: "INFO", encrypted: false, createdAt: new Date(), updatedAt: new Date() },
    { serviceName: "application", profile: "docker", label: "main", key: "spring.cloud.config.enabled", value: "true", encrypted: false, createdAt: new Date(), updatedAt: new Date() }
]);

print("Initialized " + db.config_properties.countDocuments() + " config properties");
