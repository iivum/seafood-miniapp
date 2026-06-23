package com.seafood.featureflag.infra;

import com.seafood.featureflag.domain.FeatureFlag;
import java.util.List;

/**
 * FeatureFlag domain ↔ FeatureFlagDocument infra 双向映射。
 *
 * <p>静态工具类，无状态，不注册为 Spring Bean（参考 ProductMapper 模式）。
 */
public class FeatureFlagMapper {

    private FeatureFlagMapper() {}

    public static FeatureFlag toDomain(FeatureFlagDocument doc) {
        return new FeatureFlag(
                doc.getFlagKey(),
                doc.isEnabled(),
                doc.getRolloutPercentage(),
                doc.getUserSegments() != null ? doc.getUserSegments() : List.of(),
                doc.getExpiresAt(),
                doc.getDescription(),
                doc.getCreatedBy(),
                doc.getCreatedAt(),
                doc.getUpdatedAt());
    }

    public static FeatureFlagDocument toDocument(FeatureFlag flag) {
        var doc = new FeatureFlagDocument();
        doc.setFlagKey(flag.flagKey());
        doc.setEnabled(flag.enabled());
        doc.setRolloutPercentage(flag.rolloutPercentage());
        doc.setUserSegments(flag.userSegments());
        doc.setExpiresAt(flag.expiresAt());
        doc.setDescription(flag.description());
        doc.setCreatedBy(flag.createdBy());
        doc.setCreatedAt(flag.createdAt());
        doc.setUpdatedAt(flag.updatedAt());
        return doc;
    }

    /**
     * update 场景专用重载：保留已有文档的 MongoDB _id，避免 save() 变成插入新文档。
     *
     * @param flag       domain 聚合根
     * @param existingId 数据库中该 flag 对应文档的 _id
     * @return 带 id 的 FeatureFlagDocument（save 时走 upsert，不会产生重复文档）
     */
    public static FeatureFlagDocument toDocument(FeatureFlag flag, String existingId) {
        var doc = toDocument(flag);
        doc.setId(existingId);
        return doc;
    }
}
