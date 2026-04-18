package com.seafood.config.infrastructure.mongodb;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for configuration properties.
 */
@Repository
public interface ConfigPropertyRepository extends MongoRepository<ConfigPropertyDocument, String> {

    /**
     * Find all properties for a given service, profile, and label.
     */
    List<ConfigPropertyDocument> findByServiceNameAndProfileAndLabel(String serviceName, String profile, String label);

    /**
     * Find a specific property by service, profile, label, and key.
     */
    Optional<ConfigPropertyDocument> findByServiceNameAndProfileAndLabelAndKey(
            String serviceName, String profile, String label, String key);

    /**
     * Find all properties for a given service and profile (any label).
     */
    List<ConfigPropertyDocument> findByServiceNameAndProfile(String serviceName, String profile);

    /**
     * Find all properties for a given service (any profile or label).
     */
    List<ConfigPropertyDocument> findByServiceName(String serviceName);

    /**
     * Delete all properties for a given service, profile, and label.
     */
    void deleteByServiceNameAndProfileAndLabel(String serviceName, String profile, String label);

    /**
     * Delete a specific property.
     */
    void deleteByServiceNameAndProfileAndLabelAndKey(String serviceName, String profile, String label, String key);
}
