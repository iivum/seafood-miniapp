package com.seafood.config.infrastructure.mongodb;

import com.seafood.config.infrastructure.encryption.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MongoDB-backed EnvironmentRepository for Spring Cloud Config.
 * Retrieves configuration properties from MongoDB and returns them
 * as Spring Cloud Config Environment objects.
 */
@Service
public class MongoConfigService implements EnvironmentRepository {

    private static final Logger log = LoggerFactory.getLogger(MongoConfigService.class);
    private static final String DEFAULT_LABEL = "main";

    private final ConfigPropertyRepository repository;
    private final EncryptionService encryptionService;

    public MongoConfigService(ConfigPropertyRepository repository, EncryptionService encryptionService) {
        this.repository = repository;
        this.encryptionService = encryptionService;
    }

    @Override
    public Environment findOne(String application, String profile, String label) {
        return findOne(application, profile, label, false);
    }

    /**
     * Find configuration for a given application/service, profile, and label.
     *
     * @param application the service name (e.g., "gateway", "product-service")
     * @param profile    the environment profile (e.g., "dev", "docker", "prod")
     * @param label      the version label (e.g., "main", "develop")
     * @param includeRaw if true, include raw values (for management endpoints)
     * @return the Environment with all configuration properties
     */
    public Environment findOne(String application, String profile, String label, boolean includeRaw) {
        log.debug("Finding config for application={}, profile={}, label={}", application, profile, label);

        // Normalize inputs
        if (application == null || application.isBlank()) {
            application = "application";
        }
        if (profile == null || profile.isBlank()) {
            profile = "default";
        }
        if (label == null || label.isBlank()) {
            label = DEFAULT_LABEL;
        }

        // Split profiles (comma-separated) and process in order
        String[] profiles = profile.split(",");

        // Collect properties from all sources in order of precedence
        Map<String, Object> allProperties = new HashMap<>();

        for (String p : profiles) {
            p = p.trim();
            // First try exact match with label
            addPropertiesForServiceAndProfile(allProperties, application, p, label, includeRaw);

            // Then try with "main" label as fallback (if label wasn't already "main")
            if (!DEFAULT_LABEL.equals(label)) {
                addPropertiesForServiceAndProfile(allProperties, application, p, DEFAULT_LABEL, includeRaw);
            }

            // Also add "application" as shared config for all services
            if (!"application".equals(application)) {
                addPropertiesForServiceAndProfile(allProperties, "application", p, label, includeRaw);
                if (!DEFAULT_LABEL.equals(label)) {
                    addPropertiesForServiceAndProfile(allProperties, "application", p, DEFAULT_LABEL, includeRaw);
                }
            }
        }

        // Create PropertySource from collected properties
        String sourceName = application + "-" + profile;
        PropertySource propertySource = new PropertySource(sourceName, allProperties);

        Environment environment = new Environment(application, profiles);
        environment.setLabel(label);
        environment.addFirst(propertySource);

        log.debug("Returning environment with {} properties for {}", allProperties.size(), sourceName);
        return environment;
    }

    private void addPropertiesForServiceAndProfile(Map<String, Object> properties, String serviceName,
                                                   String profile, String label, boolean includeRaw) {
        try {
            List<ConfigPropertyDocument> docs = repository
                    .findByServiceNameAndProfileAndLabel(serviceName, profile, label);

            for (ConfigPropertyDocument doc : docs) {
                String value = doc.getValue();

                // Decrypt if encrypted and we're including raw values (for non-sensitive contexts)
                // Note: actual decryption is handled when properties are served to clients
                // Here we just mark encrypted values
                if (doc.isEncrypted() && !includeRaw) {
                    // For encrypted values, we need to decrypt on-the-fly when serving
                    // Store a marker that will be resolved later
                    value = encryptionService.decrypt(value);
                }

                // Later properties override earlier ones (for same key)
                if (value != null) {
                    properties.put(doc.getKey(), value);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load properties for service={}, profile={}, label={}: {}",
                    serviceName, profile, label, e.getMessage());
        }
    }

    /**
     * Check if the repository is healthy.
     */
    public boolean isHealthy() {
        try {
            repository.count();
            return true;
        } catch (Exception e) {
            log.error("Health check failed", e);
            return false;
        }
    }
}
