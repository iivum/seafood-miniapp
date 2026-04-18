package com.seafood.config.application;

import com.seafood.config.infrastructure.encryption.EncryptionService;
import com.seafood.config.infrastructure.mongodb.ConfigPropertyDocument;
import com.seafood.config.infrastructure.mongodb.ConfigPropertyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Application service for managing configuration properties.
 * Handles CRUD operations and encryption/decryption.
 */
@Service
public class ConfigPropertyService {

    private static final Logger log = LoggerFactory.getLogger(ConfigPropertyService.class);

    private final ConfigPropertyRepository repository;
    private final EncryptionService encryptionService;

    public ConfigPropertyService(ConfigPropertyRepository repository, EncryptionService encryptionService) {
        this.repository = repository;
        this.encryptionService = encryptionService;
    }

    /**
     * Get all properties for a service and profile.
     */
    public List<ConfigPropertyDocument> getProperties(String serviceName, String profile, String label) {
        return repository.findByServiceNameAndProfileAndLabel(serviceName, profile, label);
    }

    /**
     * Get a specific property.
     */
    public Optional<ConfigPropertyDocument> getProperty(String serviceName, String profile, String label, String key) {
        return repository.findByServiceNameAndProfileAndLabelAndKey(serviceName, profile, label, key);
    }

    /**
     * Create or update a property.
     *
     * @param serviceName the service name
     * @param profile    the profile (e.g., "dev", "docker")
     * @param label      the label (e.g., "main")
     * @param key        the property key
     * @param value      the property value
     * @param encrypted  whether to encrypt the value
     * @return the saved document
     */
    public ConfigPropertyDocument saveProperty(String serviceName, String profile, String label,
                                             String key, String value, boolean encrypted) {
        log.info("Saving property: service={}, profile={}, label={}, key={}, encrypted={}",
                serviceName, profile, label, key, encrypted);

        // Encrypt value if needed
        String storedValue = value;
        if (encrypted && !encryptionService.isEncrypted(value)) {
            storedValue = encryptionService.encrypt(value);
        }

        // Find existing or create new
        Optional<ConfigPropertyDocument> existing = repository
                .findByServiceNameAndProfileAndLabelAndKey(serviceName, profile, label, key);

        ConfigPropertyDocument doc;
        if (existing.isPresent()) {
            doc = existing.get();
            doc.setValue(storedValue);
            doc.setEncrypted(encrypted);
            doc.setUpdatedAt(Instant.now());
        } else {
            doc = new ConfigPropertyDocument(serviceName, profile, label, key, storedValue, encrypted);
        }

        return repository.save(doc);
    }

    /**
     * Delete a property.
     */
    public void deleteProperty(String serviceName, String profile, String label, String key) {
        log.info("Deleting property: service={}, profile={}, label={}, key={}",
                serviceName, profile, label, key);
        repository.deleteByServiceNameAndProfileAndLabelAndKey(serviceName, profile, label, key);
    }

    /**
     * Delete all properties for a service and profile.
     */
    public void deleteAllProperties(String serviceName, String profile, String label) {
        log.info("Deleting all properties: service={}, profile={}, label={}",
                serviceName, profile, label);
        repository.deleteByServiceNameAndProfileAndLabel(serviceName, profile, label);
    }

    /**
     * Encrypt a value (utility method).
     */
    public String encryptValue(String plaintext) {
        return encryptionService.encrypt(plaintext);
    }

    /**
     * Decrypt a value (utility method).
     */
    public String decryptValue(String encryptedText) {
        return encryptionService.decrypt(encryptedText);
    }
}
