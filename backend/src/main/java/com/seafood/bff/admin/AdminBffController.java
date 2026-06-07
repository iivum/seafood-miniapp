package com.seafood.bff.admin;

import com.seafood.bff.admin.dto.DashboardResponse;
import com.seafood.bff.admin.dto.OrderDetailResponse;
import com.seafood.product.api.dto.ProductStatsResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 BFF 端点(参见 design.md §5.1,specs/backend-api §Admin BFF aggregation)。
 *
 * <p>所有端点 ADMIN-only(SecurityConfig 也限制了 URL 层级)。
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBffController {

    private final AdminBffService bff;

    public AdminBffController(AdminBffService bff) {
        this.bff = bff;
    }

    @GetMapping("/orders/{id}/detail")
    public OrderDetailResponse orderDetail(@PathVariable String id) {
        return bff.orderDetail(id);
    }

    @GetMapping("/products/stats")
    public ProductStatsResponse productStats() {
        return bff.productStats();
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return bff.dashboard();
    }
}
