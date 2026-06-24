package com.seafood.featureflag.application;

import java.time.Instant;

/**
 * Feature flag 审计记录 DTO（供 application → api 层传递，不直接暴露 infra Document）。
 */
public record FeatureFlagAuditResponse(
        String flagKey,
        String action,
        Object before,
        Object after,
        String actor,
        Instant timestamp
) {}
