package com.seafood.featureflag.domain;

import com.seafood.shared.error.DomainException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * FeatureFlag 聚合根（零 Spring import，纯 Java 25 record）。
 *
 * <p>灰度逻辑：
 * <ol>
 *   <li>flag 禁用 → false</li>
 *   <li>已过期（expiresAt 非 null 且在过去）→ false</li>
 *   <li>userId 在白名单 → true</li>
 *   <li>MurmurHash3(userId:flagKey) % 100 &lt; rolloutPercentage → true</li>
 * </ol>
 */
public record FeatureFlag(
        String flagKey,
        boolean enabled,
        int rolloutPercentage,
        List<String> userSegments,
        Instant expiresAt,
        String description,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public FeatureFlag {
        if (flagKey == null || flagKey.isBlank()) {
            throw new DomainException("flagKey must not be blank");
        }
        if (rolloutPercentage < 0 || rolloutPercentage > 100) {
            throw new DomainException("rolloutPercentage must be 0-100");
        }
        userSegments = Collections.unmodifiableList(
                new ArrayList<>(userSegments == null ? List.of() : userSegments));
    }

    /**
     * 求值指定用户是否命中该 flag，并携带命中原因（便于调试日志）。
     *
     * @param userId 当前用户 id，null 表示匿名用户（走灰度分桶，不走白名单）
     * @return {@link FlagValue}，包含 enabled 结论与 {@link FlagValue.EvalReason}
     */
    public FlagValue evaluate(String userId) {
        if (!enabled) return new FlagValue(false, FlagValue.EvalReason.DISABLED);
        if (expiresAt != null && Instant.now().isAfter(expiresAt))
            return new FlagValue(false, FlagValue.EvalReason.EXPIRED);
        if (userId != null && userSegments.contains(userId))
            return new FlagValue(true, FlagValue.EvalReason.WHITELIST);
        boolean inRollout = MurmurHash3Util.hash32(
                (userId != null ? userId : "anonymous") + ":" + flagKey) % 100 < rolloutPercentage;
        return new FlagValue(inRollout, FlagValue.EvalReason.ROLLOUT);
    }

    /**
     * 判断指定用户是否命中该 flag。
     *
     * @param userId 当前用户 id，null 表示匿名用户（走灰度分桶，不走白名单）
     * @return true 表示 flag 对该用户生效
     */
    public boolean isEnabled(String userId) {
        return evaluate(userId).enabled();
    }

    /** 禁用 flag，返回新实例。 */
    public FeatureFlag disable() {
        return new FeatureFlag(flagKey, false, rolloutPercentage, userSegments,
                expiresAt, description, createdBy, createdAt, Instant.now());
    }

    /** 更新灰度比例，返回新实例。 */
    public FeatureFlag updateRollout(int newPct) {
        return new FeatureFlag(flagKey, enabled, newPct, userSegments,
                expiresAt, description, createdBy, createdAt, Instant.now());
    }

    /** 加入白名单，返回新实例（幂等）。 */
    public FeatureFlag addToWhitelist(String userId) {
        List<String> updated = new ArrayList<>(userSegments);
        if (!updated.contains(userId)) updated.add(userId);
        return new FeatureFlag(flagKey, enabled, rolloutPercentage, updated,
                expiresAt, description, createdBy, createdAt, Instant.now());
    }

    /** 移出白名单，返回新实例。 */
    public FeatureFlag removeFromWhitelist(String userId) {
        List<String> updated = new ArrayList<>(userSegments);
        updated.remove(userId);
        return new FeatureFlag(flagKey, enabled, rolloutPercentage, updated,
                expiresAt, description, createdBy, createdAt, Instant.now());
    }
}
