package com.seafood.config.infrastructure.encryption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM encryption service for sensitive configuration values.
 * Uses AES-256-GCM which provides both encryption and authentication.
 */
@Service
public class EncryptionService {

    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 128; // 128 bits

    private final SecretKeySpec secretKey;
    private final String keyId;
    private final SecureRandom secureRandom;

    public EncryptionService(
            @Value("${encryption.aes.key}") String encodedKey,
            @Value("${encryption.aes.key-id}") String keyId) {
        this.keyId = keyId;
        this.secureRandom = new SecureRandom();

        // Decode the Base64-encoded key
        byte[] keyBytes = Base64.getDecoder().decode(encodedKey);

        // Ensure key is 256 bits (32 bytes) for AES-256
        if (keyBytes.length < 32) {
            // Pad with zeros if key is shorter (for development only)
            byte[] paddedKey = new byte[32];
            System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, 32));
            keyBytes = paddedKey;
            log.warn("AES key shorter than 256 bits, padding with zeros. DO NOT use in production!");
        } else if (keyBytes.length > 32) {
            // Truncate if longer
            byte[] truncatedKey = new byte[32];
            System.arraycopy(keyBytes, 0, truncatedKey, 0, 32);
            keyBytes = truncatedKey;
        }

        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypt a plaintext value.
     *
     * @param plaintext the value to encrypt
     * @return Base64-encoded encrypted value with IV prepended
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }

        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // Encrypt
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Prepend IV to ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            return "{cipher}" + Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Failed to encrypt value", e);
        }
    }

    /**
     * Decrypt an encrypted value.
     *
     * @param encryptedText Base64-encoded encrypted value with IV prepended
     * @return the decrypted plaintext
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) {
            return encryptedText;
        }

        // Check if it's actually encrypted (has the prefix)
        if (!encryptedText.startsWith("{cipher}")) {
            return encryptedText;
        }

        // Remove prefix
        encryptedText = encryptedText.substring(8);

        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedText);

            // Extract IV
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);

            // Extract ciphertext
            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // Decrypt
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed for value starting with: {}",
                    encryptedText.substring(0, Math.min(20, encryptedText.length())), e);
            throw new RuntimeException("Failed to decrypt value", e);
        }
    }

    /**
     * Check if a value is encrypted.
     */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith("{cipher}");
    }

    /**
     * Get the key ID being used.
     */
    public String getKeyId() {
        return keyId;
    }
}
