package com.seafood.config.interfaces.rest;

import com.seafood.config.application.ConfigPropertyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for encryption utilities.
 * Provides endpoints to encrypt/decrypt values for manual config management.
 */
@RestController
@RequestMapping("/api/config/encryption")
public class ConfigEncryptionController {

    private final ConfigPropertyService configService;

    public ConfigEncryptionController(ConfigPropertyService configService) {
        this.configService = configService;
    }

    /**
     * Encrypt a plaintext value.
     * POST /api/config/encryption/encrypt
     * Body: { "plaintext": "my-secret-password" }
     */
    @PostMapping("/encrypt")
    public ResponseEntity<Map<String, String>> encrypt(@RequestBody Map<String, String> request) {
        String plaintext = request.get("plaintext");
        if (plaintext == null || plaintext.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "plaintext is required"));
        }

        String encrypted = configService.encryptValue(plaintext);
        return ResponseEntity.ok(Map.of(
                "encrypted", encrypted,
                "note", "Store this value with encrypted=true in the config"
        ));
    }

    /**
     * Decrypt an encrypted value.
     * POST /api/config/encryption/decrypt
     * Body: { "encrypted": "{cipher}..." }
     */
    @PostMapping("/decrypt")
    public ResponseEntity<Map<String, String>> decrypt(@RequestBody Map<String, String> request) {
        String encrypted = request.get("encrypted");
        if (encrypted == null || encrypted.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "encrypted value is required"));
        }

        String decrypted = configService.decryptValue(encrypted);
        return ResponseEntity.ok(Map.of("decrypted", decrypted));
    }
}
