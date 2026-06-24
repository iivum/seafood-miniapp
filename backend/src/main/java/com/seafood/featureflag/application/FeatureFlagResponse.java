package com.seafood.featureflag.application;

import java.time.Instant;
import java.util.List;

/**
 * Feature flag 管理视图 DTO（全字段，仅供 application → api 层传递）。
 */
public record FeatureFlagResponse(
        String flagKey,
        boolean enabled,
        int rolloutPercentage,
        List<String> userSegments,
        Instant expiresAt,
        String description,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {}
