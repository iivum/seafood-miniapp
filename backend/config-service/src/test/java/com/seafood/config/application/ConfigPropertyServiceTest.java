package com.seafood.config.application;

import com.seafood.config.infrastructure.encryption.EncryptionService;
import com.seafood.config.infrastructure.mongodb.ConfigPropertyDocument;
import com.seafood.config.infrastructure.mongodb.ConfigPropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigPropertyService Tests")
class ConfigPropertyServiceTest {

    @Mock
    private ConfigPropertyRepository repository;

    @Mock
    private EncryptionService encryptionService;

    private ConfigPropertyService configPropertyService;

    @BeforeEach
    void setUp() {
        configPropertyService = new ConfigPropertyService(repository, encryptionService);
    }

    @Test
    @DisplayName("getProperties should return properties for given service, profile, and label")
    void getProperties_shouldReturnProperties() {
        String serviceName = "gateway";
        String profile = "docker";
        String label = "main";
        List<ConfigPropertyDocument> expected = List.of(
                new ConfigPropertyDocument(serviceName, profile, label, "server.port", "8080", false)
        );
        when(repository.findByServiceNameAndProfileAndLabel(serviceName, profile, label))
                .thenReturn(expected);

        List<ConfigPropertyDocument> result = configPropertyService.getProperties(serviceName, profile, label);

        assertThat(result).isEqualTo(expected);
        verify(repository).findByServiceNameAndProfileAndLabel(serviceName, profile, label);
    }

    @Test
    @DisplayName("getProperty should return property when exists")
    void getProperty_shouldReturnPropertyWhenExists() {
        String serviceName = "gateway";
        String profile = "docker";
        String label = "main";
        String key = "server.port";
        ConfigPropertyDocument expected = new ConfigPropertyDocument(serviceName, profile, label, key, "8080", false);
        when(repository.findByServiceNameAndProfileAndLabelAndKey(serviceName, profile, label, key))
                .thenReturn(Optional.of(expected));

        Optional<ConfigPropertyDocument> result = configPropertyService.getProperty(serviceName, profile, label, key);

        assertThat(result).isPresent();
        assertThat(result.get().getValue()).isEqualTo("8080");
    }

    @Test
    @DisplayName("getProperty should return empty when not exists")
    void getProperty_shouldReturnEmptyWhenNotExists() {
        when(repository.findByServiceNameAndProfileAndLabelAndKey(any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        Optional<ConfigPropertyDocument> result = configPropertyService.getProperty("any", "any", "any", "any");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("saveProperty should create new property when not exists")
    void saveProperty_shouldCreateNewProperty() {
        String serviceName = "gateway";
        String profile = "docker";
        String label = "main";
        String key = "server.port";
        String value = "8080";

        when(repository.findByServiceNameAndProfileAndLabelAndKey(serviceName, profile, label, key))
                .thenReturn(Optional.empty());
        when(repository.save(any(ConfigPropertyDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConfigPropertyDocument result = configPropertyService.saveProperty(serviceName, profile, label, key, value, false);

        assertThat(result.getServiceName()).isEqualTo(serviceName);
        assertThat(result.getProfile()).isEqualTo(profile);
        assertThat(result.getLabel()).isEqualTo(label);
        assertThat(result.getKey()).isEqualTo(key);
        assertThat(result.getValue()).isEqualTo(value);
        assertThat(result.isEncrypted()).isFalse();

        verify(repository).save(any(ConfigPropertyDocument.class));
    }

    @Test
    @DisplayName("saveProperty should update existing property")
    void saveProperty_shouldUpdateExistingProperty() {
        String serviceName = "gateway";
        String profile = "docker";
        String label = "main";
        String key = "server.port";

        ConfigPropertyDocument existing = new ConfigPropertyDocument(serviceName, profile, label, key, "8080", false);
        when(repository.findByServiceNameAndProfileAndLabelAndKey(serviceName, profile, label, key))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(ConfigPropertyDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConfigPropertyDocument result = configPropertyService.saveProperty(serviceName, profile, label, key, "9090", false);

        assertThat(result.getValue()).isEqualTo("9090");
    }

    @Test
    @DisplayName("saveProperty should encrypt value when encrypted flag is true")
    void saveProperty_shouldEncryptValue() {
        String serviceName = "gateway";
        String profile = "docker";
        String label = "main";
        String key = "jwt.secret";
        String plainValue = "my-secret";
        String encryptedValue = "{cipher}encrypted-data";

        when(encryptionService.isEncrypted(plainValue)).thenReturn(false);
        when(encryptionService.encrypt(plainValue)).thenReturn(encryptedValue);
        when(repository.findByServiceNameAndProfileAndLabelAndKey(serviceName, profile, label, key))
                .thenReturn(Optional.empty());
        when(repository.save(any(ConfigPropertyDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConfigPropertyService service = new ConfigPropertyService(repository, encryptionService);
        ConfigPropertyDocument result = service.saveProperty(serviceName, profile, label, key, plainValue, true);

        assertThat(result.getValue()).isEqualTo(encryptedValue);
        assertThat(result.isEncrypted()).isTrue();
    }

    @Test
    @DisplayName("deleteProperty should call repository delete method")
    void deleteProperty_shouldCallRepository() {
        String serviceName = "gateway";
        String profile = "docker";
        String label = "main";
        String key = "server.port";

        configPropertyService.deleteProperty(serviceName, profile, label, key);

        verify(repository).deleteByServiceNameAndProfileAndLabelAndKey(serviceName, profile, label, key);
    }

    @Test
    @DisplayName("deleteAllProperties should call repository delete method")
    void deleteAllProperties_shouldCallRepository() {
        String serviceName = "gateway";
        String profile = "docker";
        String label = "main";

        configPropertyService.deleteAllProperties(serviceName, profile, label);

        verify(repository).deleteByServiceNameAndProfileAndLabel(serviceName, profile, label);
    }

    @Test
    @DisplayName("encryptValue should delegate to EncryptionService")
    void encryptValue_shouldDelegateToEncryptionService() {
        String plaintext = "my-secret";
        String encrypted = "{cipher}encrypted";
        when(encryptionService.encrypt(plaintext)).thenReturn(encrypted);

        String result = configPropertyService.encryptValue(plaintext);

        assertThat(result).isEqualTo(encrypted);
        verify(encryptionService).encrypt(plaintext);
    }

    @Test
    @DisplayName("decryptValue should delegate to EncryptionService")
    void decryptValue_shouldDelegateToEncryptionService() {
        String encrypted = "{cipher}encrypted";
        String decrypted = "my-secret";
        when(encryptionService.decrypt(encrypted)).thenReturn(decrypted);

        String result = configPropertyService.decryptValue(encrypted);

        assertThat(result).isEqualTo(decrypted);
        verify(encryptionService).decrypt(encrypted);
    }
}
