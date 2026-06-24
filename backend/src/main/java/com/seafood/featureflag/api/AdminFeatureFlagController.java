package com.seafood.featureflag.api;

import com.seafood.featureflag.api.dto.UpdateFlagRequest;
import com.seafood.featureflag.application.FeatureFlagAuditResponse;
import com.seafood.featureflag.application.FeatureFlagResponse;
import com.seafood.featureflag.application.FeatureFlagService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import com.seafood.shared.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 Feature Flag 管理端点（全部 ADMIN-only）。
 *
 * <p>ArchUnit 守护：Controller 不可持有 *Repository 字段。所有 DB 操作通过 {@link FeatureFlagService}。
 */
@RestController
@RequestMapping("/api/admin/feature-flags")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFeatureFlagController {

    private final FeatureFlagService service;

    public AdminFeatureFlagController(FeatureFlagService service) {
        this.service = service;
    }

    /**
     * 分页列出所有 flag（含 disabled）。
     */
    @GetMapping
    public Page<FeatureFlagResponse> listFlags(Pageable pageable) {
        return service.listAll(pageable);
    }

    /**
     * 查单条 flag 详情。
     */
    @GetMapping("/{flagKey}")
    public FeatureFlagResponse getFlag(@PathVariable String flagKey) {
        return service.get(flagKey);
    }

    /**
     * 更新 flag（enable/disable/rollout/whitelist 均通过此端点，按字段 null 判断操作类型）。
     */
    @PutMapping("/{flagKey}")
    public ResponseEntity<Void> updateFlag(
            @PathVariable String flagKey,
            @Valid @RequestBody UpdateFlagRequest req,
            @AuthenticationPrincipal UserPrincipal me) {
        String actor = me.getId();

        if (req.enabled() != null) {
            if (req.enabled()) {
                service.enable(flagKey, actor);
            } else {
                service.disable(flagKey, actor);
            }
        }

        if (req.rolloutPercentage() != null) {
            service.updateRollout(flagKey, req.rolloutPercentage(), actor);
        }

        if (req.addToWhitelist() != null) {
            req.addToWhitelist().forEach(userId -> service.addToWhitelist(flagKey, userId, actor));
        }

        if (req.removeFromWhitelist() != null) {
            req.removeFromWhitelist().forEach(userId -> service.removeFromWhitelist(flagKey, userId, actor));
        }

        return ResponseEntity.ok().build();
    }

    /**
     * 分页查审计记录（按 timestamp 降序）。
     */
    @GetMapping("/{flagKey}/audit")
    public Page<FeatureFlagAuditResponse> getAuditLog(
            @PathVariable String flagKey,
            Pageable pageable) {
        return service.getAuditLog(flagKey, pageable);
    }
}
