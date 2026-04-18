package com.seafood.config.infrastructure.mongodb;

import com.seafood.config.infrastructure.encryption.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.config.environment.Environment;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MongoConfigService Tests")
class MongoConfigServiceTest {

    @Mock
    private ConfigPropertyRepository repository;

    @Mock
    private EncryptionService encryptionService;

    private MongoConfigService mongoConfigService;

    @BeforeEach
    void setUp() {
        mongoConfigService = new MongoConfigService(repository, encryptionService);
    }

    @Test
    @DisplayName("findOne should return Environment with properties from MongoDB")
    void findOne_shouldReturnEnvironmentWithProperties() {
        String serviceName = "gateway";
        String profile = "docker";
        String label = "main";

        List<ConfigPropertyDocument> docs = List.of(
                new ConfigPropertyDocument(serviceName, profile, label, "server.port", "8080", false),
                new ConfigPropertyDocument(serviceName, profile, label, "eureka.client.service-url.defaultZone", "http://localhost:8761/eureka/", false)
        );
        when(repository.findByServiceNameAndProfileAndLabel(serviceName, profile, label))
                .thenReturn(docs);

        Environment environment = mongoConfigService.findOne(serviceName, profile, label);

        assertThat(environment.getName()).isEqualTo(serviceName);
        assertThat(environment.getPropertySources()).isNotEmpty();
    }

    @Test
    @DisplayName("findOne should handle null application name")
    void findOne_shouldHandleNullApplicationName() {
        Environment environment = mongoConfigService.findOne(null, "docker", "main");

        assertThat(environment.getName()).isEqualTo("application");
    }

    @Test
    @DisplayName("findOne should handle blank profile")
    void findOne_shouldHandleBlankProfile() {
        when(repository.findByServiceNameAndProfileAndLabel(anyString(), eq("default"), anyString()))
                .thenReturn(Collections.emptyList());

        Environment environment = mongoConfigService.findOne("gateway", "", "main");

        assertThat(environment).isNotNull();
    }

    @Test
    @DisplayName("findOne should handle null label")
    void findOne_shouldHandleNullLabel() {
        when(repository.findByServiceNameAndProfileAndLabel(anyString(), anyString(), eq("main")))
                .thenReturn(Collections.emptyList());

        Environment environment = mongoConfigService.findOne("gateway", "docker", null);

        assertThat(environment).isNotNull();
        assertThat(environment.getLabel()).isEqualTo("main");
    }

    @Test
    @DisplayName("findOne should decrypt encrypted values")
    void findOne_shouldDecryptEncryptedValues() {
        String serviceName = "gateway";
        String profile = "docker";
        String label = "main";
        String encryptedValue = "{cipher}abc123";
        String decryptedValue = "my-secret";

        ConfigPropertyDocument doc = new ConfigPropertyDocument(serviceName, profile, label, "jwt.secret", encryptedValue, true);
        when(repository.findByServiceNameAndProfileAndLabel(serviceName, profile, label))
                .thenReturn(List.of(doc));
        when(encryptionService.decrypt(encryptedValue)).thenReturn(decryptedValue);

        Environment environment = mongoConfigService.findOne(serviceName, profile, label);

        assertThat(environment.getPropertySources()).isNotEmpty();
        verify(encryptionService).decrypt(encryptedValue);
    }

    @Test
    @DisplayName("findOne should include shared application properties")
    void findOne_shouldIncludeSharedApplicationProperties() {
        String serviceName = "gateway";
        String profile = "docker";
        String label = "main";

        // Service-specific property
        List<ConfigPropertyDocument> serviceDocs = List.of(
                new ConfigPropertyDocument(serviceName, profile, label, "server.port", "8080", false)
        );
        // Shared application property
        List<ConfigPropertyDocument> sharedDocs = List.of(
                new ConfigPropertyDocument("application", profile, label, "logging.level.root", "INFO", false)
        );

        when(repository.findByServiceNameAndProfileAndLabel(serviceName, profile, label))
                .thenReturn(serviceDocs);
        when(repository.findByServiceNameAndProfileAndLabel("application", profile, label))
                .thenReturn(sharedDocs);

        Environment environment = mongoConfigService.findOne(serviceName, profile, label);

        assertThat(environment).isNotNull();
    }

    @Test
    @DisplayName("findOne should return empty Environment when no properties found")
    void findOne_shouldReturnEmptyEnvironmentWhenNoPropertiesFound() {
        when(repository.findByServiceNameAndProfileAndLabel(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        Environment environment = mongoConfigService.findOne("unknown-service", "unknown", "unknown");

        assertThat(environment.getPropertySources()).isNotNull();
    }

    @Test
    @DisplayName("isHealthy should return true when repository is accessible")
    void isHealthy_shouldReturnTrueWhenRepositoryAccessible() {
        when(repository.count()).thenReturn(10L);

        boolean healthy = mongoConfigService.isHealthy();

        assertThat(healthy).isTrue();
    }

    @Test
    @DisplayName("isHealthy should return false when repository throws exception")
    void isHealthy_shouldReturnFalseWhenRepositoryThrowsException() {
        when(repository.count()).thenThrow(new RuntimeException("Connection failed"));

        boolean healthy = mongoConfigService.isHealthy();

        assertThat(healthy).isFalse();
    }

    @Test
    @DisplayName("findOne should handle multiple profiles (comma-separated)")
    void findOne_shouldHandleMultipleProfiles() {
        when(repository.findByServiceNameAndProfileAndLabel(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        Environment environment = mongoConfigService.findOne("gateway", "docker,native", "main");

        assertThat(environment).isNotNull();
        assertThat(environment.getProfiles()).contains("docker", "native");
    }
}
