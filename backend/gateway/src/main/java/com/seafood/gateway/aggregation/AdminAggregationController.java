package com.seafood.gateway.aggregation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Admin Aggregation Controller (BFF Layer)
 * 
 * Provides aggregated API endpoints for the Vue 3 admin frontend.
 * Combines data from multiple microservices to reduce client-side requests.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminAggregationController {

    private static final Logger log = LoggerFactory.getLogger(AdminAggregationController.class);

    private final AggregationService aggregationService;

    public AdminAggregationController(AggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    /**
     * GET /api/admin/orders/{id}/detail
     * 
     * Returns aggregated order detail including:
     * - Order information (from order-service)
     * - User information (from user-service)
     * - Product information (from product-service)
     *
     * @param id the order ID
     * @return aggregated order detail
     */
    @GetMapping("/orders/{id}/detail")
    public Mono<ResponseEntity<Map<String, Object>>> getOrderDetail(@PathVariable("id") String id) {
        log.info("Fetching aggregated order detail for orderId: {}", id);
        return aggregationService.getOrderDetail(id)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error fetching order detail for orderId: {}", id, e);
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    /**
     * GET /api/admin/products/stats
     * 
     * Returns aggregated product statistics:
     * - Total products count
     * - Products by category
     * - Low stock alerts
     * - On-sale products count
     *
     * @return aggregated product statistics
     */
    @GetMapping("/products/stats")
    public Mono<ResponseEntity<Map<String, Object>>> getProductStats() {
        log.info("Fetching aggregated product statistics");
        return aggregationService.getProductStats()
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error fetching product stats", e);
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    /**
     * GET /api/admin/dashboard
     * 
     * Returns aggregated dashboard data:
     * - Order statistics (total, revenue, by status)
     * - Product statistics (total, by category)
     * - User statistics (total)
     * - Recent activity
     *
     * @return aggregated dashboard data
     */
    @GetMapping("/dashboard")
    public Mono<ResponseEntity<Map<String, Object>>> getDashboard() {
        log.info("Fetching aggregated dashboard data");
        return aggregationService.getDashboardData()
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error fetching dashboard data", e);
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    /**
     * POST /api/admin/cache/invalidate
     * 
     * Invalidates aggregation caches.
     * Used when data is updated and fresh data is needed.
     *
     * @param type cache type to invalidate (order, product, dashboard, or all)
     * @param id optional ID for order-specific invalidation
     * @return success message
     */
    @PostMapping("/cache/invalidate")
    public Mono<ResponseEntity<Map<String, String>>> invalidateCache(
            @RequestParam(value = "type", defaultValue = "all") String type,
            @RequestParam(value = "id", required = false) String id) {
        log.info("Invalidating cache: type={}, id={}", type, id);

        Mono<Void> invalidation = switch (type.toLowerCase()) {
            case "order" -> id != null
                    ? aggregationService.invalidateOrderDetail(id)
                    : Mono.empty();
            case "product" -> aggregationService.invalidateProductStats();
            case "dashboard" -> aggregationService.invalidateDashboard();
            default -> aggregationService.invalidateDashboard()
                    .then(aggregationService.invalidateProductStats());
        };

        return invalidation.thenReturn(ResponseEntity.ok(Map.of("status", "success", "message", "Cache invalidated")));
    }
}
