package com.seafood.featureflag.application;

import com.seafood.featureflag.domain.FeatureFlag;
import com.seafood.featureflag.infra.FeatureFlagAuditRepository;
import com.seafood.featureflag.infra.FeatureFlagDocument;
import com.seafood.featureflag.infra.FeatureFlagRepository;
import com.seafood.shared.error.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeatureFlagServiceTest {

    private FeatureFlagRepository repository;
    private FeatureFlagAuditRepository auditRepository;
    private FeatureFlagCache cache;
    private FeatureFlagService service;

    @BeforeEach
    void setUp() {
        repository = mock(FeatureFlagRepository.class);
        auditRepository = mock(FeatureFlagAuditRepository.class);
        cache = mock(FeatureFlagCache.class);
        service = new FeatureFlagService(repository, auditRepository, cache);
    }

    @Test
    void isEnabled_delegatesToCache() {
        FeatureFlag flag = flag("new-ui", true, 100);
        when(cache.get("new-ui")).thenReturn(Optional.of(flag));

        assertThat(service.isEnabled("new-ui", "user1")).isTrue();
        verify(cache).get("new-ui");
    }

    @Test
    void isEnabled_returnsFalse_whenFlagNotFound() {
        when(cache.get("unknown-flag")).thenReturn(Optional.empty());

        assertThat(service.isEnabled("unknown-flag", "user1")).isFalse();
    }

    @Test
    void enable_setsEnabledTrue_andSavesAudit() {
        FeatureFlagDocument doc = flagDoc("new-ui", false, "doc-id-1");
        when(repository.findByFlagKey("new-ui")).thenReturn(Optional.of(doc));

        service.enable("new-ui", "admin");

        ArgumentCaptor<FeatureFlagDocument> savedDoc = ArgumentCaptor.forClass(FeatureFlagDocument.class);
        verify(repository).save(savedDoc.capture());
        assertThat(savedDoc.getValue().isEnabled()).isTrue();

        verify(auditRepository).save(any(com.seafood.featureflag.infra.FeatureFlagAuditDocument.class));
        verify(cache).refresh();
    }

    @Test
    void disable_setsEnabledFalse_andSavesAudit() {
        FeatureFlagDocument doc = flagDoc("new-ui", true, "doc-id-1");
        when(repository.findByFlagKey("new-ui")).thenReturn(Optional.of(doc));

        service.disable("new-ui", "admin");

        ArgumentCaptor<FeatureFlagDocument> savedDoc = ArgumentCaptor.forClass(FeatureFlagDocument.class);
        verify(repository).save(savedDoc.capture());
        assertThat(savedDoc.getValue().isEnabled()).isFalse();

        verify(auditRepository).save(any(com.seafood.featureflag.infra.FeatureFlagAuditDocument.class));
        verify(cache).refresh();
    }

    @Test
    void updateRollout_updatesPercentage_andSavesAudit() {
        FeatureFlagDocument doc = flagDoc("new-ui", true, "doc-id-1");
        doc.setRolloutPercentage(50);
        when(repository.findByFlagKey("new-ui")).thenReturn(Optional.of(doc));

        service.updateRollout("new-ui", 80, "admin");

        ArgumentCaptor<FeatureFlagDocument> savedDoc = ArgumentCaptor.forClass(FeatureFlagDocument.class);
        verify(repository).save(savedDoc.capture());
        assertThat(savedDoc.getValue().getRolloutPercentage()).isEqualTo(80);

        verify(auditRepository).save(any(com.seafood.featureflag.infra.FeatureFlagAuditDocument.class));
        verify(cache).refresh();
    }

    @Test
    void addToWhitelist_addsUserId_andSavesAudit() {
        FeatureFlagDocument doc = flagDoc("new-ui", true, "doc-id-1");
        when(repository.findByFlagKey("new-ui")).thenReturn(Optional.of(doc));

        service.addToWhitelist("new-ui", "user-vip", "admin");

        ArgumentCaptor<FeatureFlagDocument> savedDoc = ArgumentCaptor.forClass(FeatureFlagDocument.class);
        verify(repository).save(savedDoc.capture());
        assertThat(savedDoc.getValue().getUserSegments()).contains("user-vip");

        verify(auditRepository).save(any(com.seafood.featureflag.infra.FeatureFlagAuditDocument.class));
        verify(cache).refresh();
    }

    @Test
    void removeFromWhitelist_removesUserId_andSavesAudit() {
        FeatureFlagDocument doc = flagDoc("new-ui", true, "doc-id-1");
        doc.setUserSegments(new java.util.ArrayList<>(List.of("user-vip")));
        when(repository.findByFlagKey("new-ui")).thenReturn(Optional.of(doc));

        service.removeFromWhitelist("new-ui", "user-vip", "admin");

        ArgumentCaptor<FeatureFlagDocument> savedDoc = ArgumentCaptor.forClass(FeatureFlagDocument.class);
        verify(repository).save(savedDoc.capture());
        assertThat(savedDoc.getValue().getUserSegments()).doesNotContain("user-vip");

        verify(auditRepository).save(any(com.seafood.featureflag.infra.FeatureFlagAuditDocument.class));
        verify(cache).refresh();
    }

    @Test
    void update_throwsNotFound_whenFlagKeyNotExist() {
        when(repository.findByFlagKey("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enable("ghost", "admin"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void listAll_returnsPaged() {
        Pageable pageable = PageRequest.of(0, 10);
        FeatureFlagDocument doc = flagDoc("new-ui", true, "doc-id-1");
        Page<FeatureFlagDocument> page = new PageImpl<>(List.of(doc), pageable, 1);
        when(repository.findAll(pageable)).thenReturn(page);

        Page<FeatureFlagResponse> result = service.listAll(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).flagKey()).isEqualTo("new-ui");
    }

    @Test
    void getAuditLog_returnsPaged() {
        Pageable pageable = PageRequest.of(0, 10);
        com.seafood.featureflag.infra.FeatureFlagAuditDocument audit =
                new com.seafood.featureflag.infra.FeatureFlagAuditDocument(
                        "new-ui", AuditAction.ENABLE.name(), null, null, "admin", Instant.now());
        Page<com.seafood.featureflag.infra.FeatureFlagAuditDocument> page =
                new PageImpl<>(List.of(audit), pageable, 1);
        when(auditRepository.findByFlagKeyOrderByTimestampDesc(eq("new-ui"), eq(pageable))).thenReturn(page);

        Page<FeatureFlagAuditResponse> result = service.getAuditLog("new-ui", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).flagKey()).isEqualTo("new-ui");
    }

    // ========== 工具方法 ==========

    private FeatureFlag flag(String key, boolean enabled, int pct) {
        return new FeatureFlag(key, enabled, pct, List.of(), null, "desc",
                "admin", Instant.now(), Instant.now());
    }

    private FeatureFlagDocument flagDoc(String key, boolean enabled, String id) {
        FeatureFlagDocument doc = new FeatureFlagDocument();
        doc.setId(id);
        doc.setFlagKey(key);
        doc.setEnabled(enabled);
        doc.setRolloutPercentage(100);
        doc.setUserSegments(new java.util.ArrayList<>());
        doc.setCreatedBy("admin");
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        return doc;
    }
}
