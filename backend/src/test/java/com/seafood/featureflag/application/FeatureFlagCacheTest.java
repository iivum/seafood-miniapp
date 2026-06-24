package com.seafood.featureflag.application;

import com.seafood.featureflag.infra.FeatureFlagDocument;
import com.seafood.featureflag.infra.FeatureFlagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureFlagCacheTest {

    private FeatureFlagRepository repository;
    private FeatureFlagCache cache;

    @BeforeEach
    void setUp() {
        repository = mock(FeatureFlagRepository.class);
        cache = new FeatureFlagCache(repository);
    }

    @Test
    void load_populatesCacheOnPostConstruct() {
        when(repository.findAll()).thenReturn(List.of(flagDoc("new-ui", true)));

        cache.load();

        assertThat(cache.get("new-ui")).isPresent();
        assertThat(cache.get("new-ui").get().enabled()).isTrue();
    }

    @Test
    void refresh_updatesCache() {
        when(repository.findAll()).thenReturn(List.of(flagDoc("new-ui", true)));
        cache.load();

        // 模拟 flag 被 disable 后刷新
        when(repository.findAll()).thenReturn(List.of(flagDoc("new-ui", false)));
        cache.refresh();

        Optional<com.seafood.featureflag.domain.FeatureFlag> updated = cache.get("new-ui");
        assertThat(updated).isPresent();
        assertThat(updated.get().enabled()).isFalse();
    }

    @Test
    void get_returnsEmpty_whenFlagNotInCache() {
        when(repository.findAll()).thenReturn(List.of());
        cache.load();

        assertThat(cache.get("non-existent")).isEmpty();
    }

    @Test
    void load_throwsException_whenMongoUnavailable() {
        when(repository.findAll()).thenThrow(new RuntimeException("MongoDB connection refused"));

        assertThatThrownBy(() -> cache.load())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("MongoDB connection refused");
    }

    // ========== 工具方法 ==========

    private FeatureFlagDocument flagDoc(String key, boolean enabled) {
        FeatureFlagDocument doc = new FeatureFlagDocument();
        doc.setFlagKey(key);
        doc.setEnabled(enabled);
        doc.setRolloutPercentage(100);
        doc.setUserSegments(List.of());
        doc.setCreatedBy("admin");
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        return doc;
    }
}
