package com.seafood.config.interfaces.rest;

import com.seafood.config.application.ConfigPropertyService;
import com.seafood.config.infrastructure.mongodb.ConfigPropertyDocument;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing configuration properties.
 * Provides CRUD operations for config properties stored in MongoDB.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigManagementController {

    private final ConfigPropertyService configService;

    public ConfigManagementController(ConfigPropertyService configService) {
        this.configService = configService;
    }

    /**
     * Get all properties for a service and profile.
     * GET /api/config?serviceName=gateway&profile=docker&label=main
     */
    @GetMapping
    public ResponseEntity<List<ConfigPropertyDocument>> getProperties(
            @RequestParam String serviceName,
            @RequestParam(defaultValue = "docker") String profile,
            @RequestParam(defaultValue = "main") String label) {

        List<ConfigPropertyDocument> properties = configService.getProperties(serviceName, profile, label);
        return ResponseEntity.ok(properties);
    }

    /**
     * Get a specific property.
     */
    @GetMapping("/{serviceName}/{profile}/{label}/{key}")
    public ResponseEntity<ConfigPropertyDocument> getProperty(
            @PathVariable String serviceName,
            @PathVariable String profile,
            @PathVariable String label,
            @PathVariable String key) {

        return configService.getProperty(serviceName, profile, label, key)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create or update a property.
     * POST /api/config
     * Body: { "serviceName": "gateway", "profile": "docker", "label": "main", "key": "server.port", "value": "8080", "encrypted": false }
     */
    @PostMapping
    public ResponseEntity<ConfigPropertyDocument> saveProperty(@RequestBody SavePropertyRequest request) {
        ConfigPropertyDocument saved = configService.saveProperty(
                request.serviceName(),
                request.profile(),
                request.label(),
                request.key(),
                request.value(),
                request.encrypted()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Delete a specific property.
     */
    @DeleteMapping("/{serviceName}/{profile}/{label}/{key}")
    public ResponseEntity<Void> deleteProperty(
            @PathVariable String serviceName,
            @PathVariable String profile,
            @PathVariable String label,
            @PathVariable String key) {

        configService.deleteProperty(serviceName, profile, label, key);
        return ResponseEntity.noContent().build();
    }

    /**
     * Delete all properties for a service and profile.
     */
    @DeleteMapping("/{serviceName}/{profile}/{label}")
    public ResponseEntity<Void> deleteAllProperties(
            @PathVariable String serviceName,
            @PathVariable String profile,
            @PathVariable String label) {

        configService.deleteAllProperties(serviceName, profile, label);
        return ResponseEntity.noContent().build();
    }

    /**
     * Request body for saving a property.
     */
    public record SavePropertyRequest(
            String serviceName,
            String profile,
            String label,
            String key,
            String value,
            boolean encrypted
    ) {}
}
