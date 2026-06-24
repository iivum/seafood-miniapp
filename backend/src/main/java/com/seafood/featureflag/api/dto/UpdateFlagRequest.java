package com.seafood.featureflag.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

/**
 * 管理员更新 flag 请求体（字段均为 nullable，只传需要变更的字段）。
 */
public record UpdateFlagRequest(
        Boolean enabled,
        @Min(0) @Max(100) Integer rolloutPercentage,
        List<String> addToWhitelist,
        List<String> removeFromWhitelist
) {}
