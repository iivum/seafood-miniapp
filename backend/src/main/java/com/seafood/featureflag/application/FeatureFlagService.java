package com.seafood.featureflag.application;

import com.seafood.featureflag.domain.FeatureFlag;
import com.seafood.featureflag.infra.FeatureFlagAuditDocument;
import com.seafood.featureflag.infra.FeatureFlagAuditRepository;
import com.seafood.featureflag.infra.FeatureFlagDocument;
import com.seafood.featureflag.infra.FeatureFlagMapper;
import com.seafood.featureflag.infra.FeatureFlagRepository;
import com.seafood.shared.error.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Feature flag 应用服务，协调 domain、infra、cache 三层，处理 flag 的查询与状态变更。
 *
 * <p>读路径：委托内存缓存（{@link FeatureFlagCache}），零 DB 往返。
 * <p>写路径：更新 DB → 写审计记录 → 立即刷新缓存（近实时）。
 */
@Service
public class FeatureFlagService {

    private final FeatureFlagRepository repository;
    private final FeatureFlagAuditRepository auditRepository;
    private final FeatureFlagCache cache;

    public FeatureFlagService(FeatureFlagRepository repository,
                               FeatureFlagAuditRepository auditRepository,
                               FeatureFlagCache cache) {
        this.repository = repository;
        this.auditRepository = auditRepository;
        this.cache = cache;
    }

    // ========== 读路径（走缓存）==========

    /**
     * 判断指定用户是否命中某 flag。
     * flag 不存在时默认返回 false（fail-closed）。
     */
    public boolean isEnabled(String flagKey, String userId) {
        return cache.get(flagKey)
                .map(f -> f.isEnabled(userId))
                .orElse(false);
    }

    /**
     * 小程序公共端点：返回所有 flag 的 flagKey + enabled（当前用户 = anonymous）。
     */
    public List<ClientFlagResponse> listClientFlags() {
        return repository.findAll().stream()
                .map(FeatureFlagMapper::toDomain)
                .map(f -> new ClientFlagResponse(f.flagKey(), f.isEnabled(null)))
                .toList();
    }

    // ========== 管理员读路径（走 DB 或 cache）==========

    /**
     * 管理员分页列表（所有 flag，含 disabled）。
     */
    public Page<FeatureFlagResponse> listAll(Pageable pageable) {
        return repository.findAll(pageable)
                .map(FeatureFlagMapper::toDomain)
                .map(this::toResponse);
    }

    /**
     * 管理员查单条详情。
     */
    public FeatureFlagResponse get(String flagKey) {
        FeatureFlagDocument doc = repository.findByFlagKey(flagKey)
                .orElseThrow(() -> new NotFoundException("feature flag not found: " + flagKey));
        return toResponse(FeatureFlagMapper.toDomain(doc));
    }

    /**
     * 分页查审计记录（按 timestamp 降序）。
     */
    public Page<FeatureFlagAuditDocument> getAuditLog(String flagKey, Pageable pageable) {
        return auditRepository.findByFlagKeyOrderByTimestampDesc(flagKey, pageable);
    }

    // ========== 写路径（DB → audit → cache.refresh）==========

    /**
     * 启用 flag。
     */
    public void enable(String flagKey, String actor) {
        FeatureFlagDocument doc = findOrThrow(flagKey);
        FeatureFlag before = FeatureFlagMapper.toDomain(doc);
        FeatureFlag updated = new FeatureFlag(
                before.flagKey(), true, before.rolloutPercentage(), before.userSegments(),
                before.expiresAt(), before.description(), before.createdBy(),
                before.createdAt(), Instant.now());
        repository.save(FeatureFlagMapper.toDocument(updated, doc.getId()));
        saveAudit(flagKey, AuditAction.ENABLE, before, updated, actor);
        cache.refresh();
    }

    /**
     * 禁用 flag。
     */
    public void disable(String flagKey, String actor) {
        FeatureFlagDocument doc = findOrThrow(flagKey);
        FeatureFlag before = FeatureFlagMapper.toDomain(doc);
        FeatureFlag updated = before.disable();
        repository.save(FeatureFlagMapper.toDocument(updated, doc.getId()));
        saveAudit(flagKey, AuditAction.DISABLE, before, updated, actor);
        cache.refresh();
    }

    /**
     * 更新灰度比例。
     */
    public void updateRollout(String flagKey, int percentage, String actor) {
        FeatureFlagDocument doc = findOrThrow(flagKey);
        FeatureFlag before = FeatureFlagMapper.toDomain(doc);
        FeatureFlag updated = before.updateRollout(percentage);
        repository.save(FeatureFlagMapper.toDocument(updated, doc.getId()));
        saveAudit(flagKey, AuditAction.PERCENTAGE_CHANGE, before, updated, actor);
        cache.refresh();
    }

    /**
     * 将 userId 加入白名单。
     */
    public void addToWhitelist(String flagKey, String userId, String actor) {
        FeatureFlagDocument doc = findOrThrow(flagKey);
        FeatureFlag before = FeatureFlagMapper.toDomain(doc);
        FeatureFlag updated = before.addToWhitelist(userId);
        repository.save(FeatureFlagMapper.toDocument(updated, doc.getId()));
        saveAudit(flagKey, AuditAction.WHITELIST_ADD, before, updated, actor);
        cache.refresh();
    }

    /**
     * 将 userId 移出白名单。
     */
    public void removeFromWhitelist(String flagKey, String userId, String actor) {
        FeatureFlagDocument doc = findOrThrow(flagKey);
        FeatureFlag before = FeatureFlagMapper.toDomain(doc);
        FeatureFlag updated = before.removeFromWhitelist(userId);
        repository.save(FeatureFlagMapper.toDocument(updated, doc.getId()));
        saveAudit(flagKey, AuditAction.WHITELIST_REMOVE, before, updated, actor);
        cache.refresh();
    }

    // ========== 私有工具方法 ==========

    private FeatureFlagDocument findOrThrow(String flagKey) {
        return repository.findByFlagKey(flagKey)
                .orElseThrow(() -> new NotFoundException("feature flag not found: " + flagKey));
    }

    private void saveAudit(String flagKey, AuditAction action,
                           FeatureFlag before, FeatureFlag after, String actor) {
        auditRepository.save(new FeatureFlagAuditDocument(
                flagKey, action.name(), before, after, actor, Instant.now()));
    }

    private FeatureFlagResponse toResponse(FeatureFlag flag) {
        return new FeatureFlagResponse(
                flag.flagKey(), flag.enabled(), flag.rolloutPercentage(),
                flag.userSegments(), flag.expiresAt(), flag.description(),
                flag.createdBy(), flag.createdAt(), flag.updatedAt());
    }
}
