package com.seafood.config.infrastructure.encryption;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EncryptionService Tests")
class EncryptionServiceTest {

    private EncryptionService encryptionService;

    // 256-bit key encoded in Base64 (32 bytes)
    private static final String TEST_KEY = "dGVzdC1rZXktZm9yLWVuY3J5cHRpb24tb25seS1kZXYtbm90LWZvci1wcm9k";
    private static final String TEST_KEY_ID = "test-key-id";

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService(TEST_KEY, TEST_KEY_ID);
    }

    @Test
    @DisplayName("encrypt should return encrypted value with {cipher} prefix")
    void encrypt_shouldReturnEncryptedValue() {
        String plaintext = "my-secret-password";

        String encrypted = encryptionService.encrypt(plaintext);

        assertThat(encrypted).isNotNull();
        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(encrypted).startsWith("{cipher}");
    }

    @Test
    @DisplayName("encrypt should produce different ciphertext each time (due to random IV)")
    void encrypt_shouldProduceDifferentCiphertextEachTime() {
        String plaintext = "same-value";

        String encrypted1 = encryptionService.encrypt(plaintext);
        String encrypted2 = encryptionService.encrypt(plaintext);

        // Same plaintext should produce different ciphertext due to random IV
        assertThat(encrypted1).isNotEqualTo(encrypted2);
    }

    @Test
    @DisplayName("decrypt should return original plaintext")
    void decrypt_shouldReturnOriginalPlaintext() {
        String plaintext = "my-secret-password";

        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("decrypt should handle non-encrypted values (no {cipher} prefix)")
    void decrypt_shouldHandleNonEncryptedValues() {
        String plainValue = "plain-text-value";

        String result = encryptionService.decrypt(plainValue);

        assertThat(result).isEqualTo(plainValue);
    }

    @Test
    @DisplayName("decrypt should return null for null input")
    void decrypt_shouldReturnNullForNullInput() {
        String result = encryptionService.decrypt(null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("decrypt should return empty string for empty input")
    void decrypt_shouldReturnEmptyStringForEmptyInput() {
        String result = encryptionService.decrypt("");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("encrypt should return null for null input")
    void encrypt_shouldReturnNullForNullInput() {
        String result = encryptionService.encrypt(null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("encrypt should return empty string for empty input")
    void encrypt_shouldReturnEmptyStringForEmptyInput() {
        String result = encryptionService.encrypt("");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("isEncrypted should return true for values with {cipher} prefix")
    void isEncrypted_shouldReturnTrueForEncryptedValues() {
        String encrypted = encryptionService.encrypt("test");

        assertThat(encryptionService.isEncrypted(encrypted)).isTrue();
    }

    @Test
    @DisplayName("isEncrypted should return false for plain values")
    void isEncrypted_shouldReturnFalseForPlainValues() {
        assertThat(encryptionService.isEncrypted("plain-text")).isFalse();
        assertThat(encryptionService.isEncrypted("")).isFalse();
        assertThat(encryptionService.isEncrypted(null)).isFalse();
    }

    @Test
    @DisplayName("getKeyId should return the configured key ID")
    void getKeyId_shouldReturnConfiguredKeyId() {
        assertThat(encryptionService.getKeyId()).isEqualTo(TEST_KEY_ID);
    }

    @Test
    @DisplayName("roundtrip encryption/decryption should work for various values")
    void roundtrip_shouldWorkForVariousValues() {
        String[] testValues = {
                "simple",
                "with spaces",
                "with/special@chars!",
                "中文测试",
                "🎉 emoji 🎊",
                "very-long-value-that-is-much-longer-than-the-typical-short-password-we-would-encrypt"
        };

        for (String value : testValues) {
            String encrypted = encryptionService.encrypt(value);
            String decrypted = encryptionService.decrypt(encrypted);
            assertThat(decrypted).as("Roundtrip failed for: " + value).isEqualTo(value);
        }
    }
}
