package com.seafood.config.infrastructure.mongodb;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document for configuration properties.
 * Each document represents a single key-value pair for a specific service and profile.
 */
@Document(collection = "config_properties")
@CompoundIndex(name = "service_profile_label_key", def = "{'serviceName': 1, 'profile': 1, 'label': 1, 'key': 1}", unique = true)
public class ConfigPropertyDocument {

    @Id
    private String id;

    /**
     * Target service name (e.g., "gateway", "product-service", "order-service")
     */
    private String serviceName;

    /**
     * Environment profile (e.g., "dev", "docker", "prod", "native")
     */
    private String profile;

    /**
     * Git-like label for versioning (default: "main")
     */
    private String label = "main";

    /**
     * Configuration property key (e.g., "server.port", "spring.datasource.password")
     */
    private String key;

    /**
     * Configuration property value (plain text or encrypted with "encrypted:" prefix)
     */
    private String value;

    /**
     * Flag indicating if the value is encrypted
     */
    private boolean encrypted = false;

    /**
     * Creation timestamp
     */
    private Instant createdAt;

    /**
     * Last update timestamp
     */
    private Instant updatedAt;

    public ConfigPropertyDocument() {
    }

    public ConfigPropertyDocument(String serviceName, String profile, String label, String key, String value, boolean encrypted) {
        this.serviceName = serviceName;
        this.profile = profile;
        this.label = label;
        this.key = key;
        this.value = value;
        this.encrypted = encrypted;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
