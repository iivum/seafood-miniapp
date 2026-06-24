package com.seafood.featureflag.api;

import com.seafood.featureflag.application.ClientFlagResponse;
import com.seafood.featureflag.application.FeatureFlagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序公共 Feature Flag 端点（无鉴权，只返客户端可见字段：flagKey + enabled）。
 *
 * <p>SecurityConfig 中 {@code GET /api/featureflags} 已放行，无需 JWT。
 */
@RestController
@RequestMapping("/api/featureflags")
public class FeatureFlagController {

    private final FeatureFlagService service;

    public FeatureFlagController(FeatureFlagService service) {
        this.service = service;
    }

    /**
     * 返回所有 flag 的客户端视图（仅 flagKey + enabled），供小程序启动时批量拉取。
     */
    @GetMapping
    public List<ClientFlagResponse> listClientFlags() {
        return service.listClientFlags();
    }
}
