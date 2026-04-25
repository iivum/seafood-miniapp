package com.seafood.gateway.aggregation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for aggregating data from multiple microservices.
 * Uses WebClient for parallel reactive calls with Redis caching.
 */
@Service
public class AggregationService {

    private static final Logger log = LoggerFactory.getLogger(AggregationService.class);

    private final WebClient.Builder webClientBuilder;
    private final RedisCacheService cacheService;
    private final AggregationProperties aggregationProperties;
    private final ObjectMapper objectMapper;

    @Value("${spring.cloud.gateway.discovery.locator.enabled:false}")
    private boolean discoveryLocatorEnabled;

    public AggregationService(WebClient.Builder webClientBuilder, RedisCacheService cacheService,
                              AggregationProperties aggregationProperties) {
        this.webClientBuilder = webClientBuilder;
        this.cacheService = cacheService;
        this.aggregationProperties = aggregationProperties;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get order detail with product and user information.
     * Uses parallel calls to order-service, product-service, and user-service.
     *
     * @param orderId the order ID
     * @return aggregated order detail
     */
    @CircuitBreaker(name = "gatewayCircuitBreaker", fallbackMethod = "getOrderDetailFallback")
    public Mono<Map<String, Object>> getOrderDetail(String orderId) {
        String cacheKey = "order:detail:" + orderId;

        return cacheService.get(cacheKey, Map.class)
                .flatMap(cached -> {
                    if (cached != null) {
                        log.debug("Cache hit for order detail: {}", orderId);
                        return Mono.just(cached);
                    }
                    return fetchOrderDetailFromServices(orderId)
                            .flatMap(result -> cacheService.set(cacheKey, result)
                                    .thenReturn(result));
                })
                .switchIfEmpty(Mono.defer(() -> fetchOrderDetailFromServices(orderId)
                        .flatMap(result -> cacheService.set(cacheKey, result).thenReturn(result))));
    }

    private Mono<Map<String, Object>> getOrderDetailFallback(String orderId, Throwable t) {
        log.warn("Circuit breaker fallback for getOrderDetail: {}, reason: {}", orderId, t.getMessage());
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("order", new HashMap<>());
        fallback.put("user", new HashMap<>());
        fallback.put("product", new HashMap<>());
        fallback.put("circuitBreakerFallback", true);
        return Mono.just(fallback);
    }

    private Mono<Map<String, Object>> fetchOrderDetailFromServices(String orderId) {
        log.debug("Fetching order detail from services for orderId: {}", orderId);

        // First fetch the order to get userId and productId
        Mono<Map> orderMono = fetchFromService("order-service", "/orders/" + orderId);

        return orderMono.flatMap(order -> {
            // Extract userId and productId from order
            String userId = extractStringValue(order, "userId");
            String productId = extractStringValue(order, "productId");

            log.debug("Order fetched, userId: {}, productId: {}", userId, productId);

            // Fetch user and product details in parallel using correct IDs
            Mono<Map> userMono = fetchUserWithCircuitBreaker(userId)
                    .onErrorResume(e -> {
                        log.warn("Failed to fetch user info: {}", e.getMessage());
                        return Mono.just(new HashMap<>());
                    });
            Mono<Map> productDetailsMono = fetchProductWithCircuitBreaker(productId)
                    .onErrorResume(e -> {
                        log.warn("Failed to fetch product info: {}", e.getMessage());
                        return Mono.just(new HashMap<>());
                    });

            return Mono.zip(userMono, productDetailsMono)
                    .map(tuple -> {
                        Map<String, Object> user = tuple.getT1();
                        Map<String, Object> product = tuple.getT2();

                        Map<String, Object> result = new HashMap<>();
                        result.put("order", order);
                        result.put("user", user);
                        result.put("product", product);
                        return result;
                    });
        });
    }

    @CircuitBreaker(name = "gatewayCircuitBreaker", fallbackMethod = "fetchUserFallback")
    private Mono<Map> fetchUserWithCircuitBreaker(String userId) {
        if (userId == null || userId.isBlank()) {
            return Mono.just(new HashMap<>());
        }
        return fetchFromService("user-service", "/users/" + userId);
    }

    private Mono<Map> fetchUserFallback(String userId, Throwable t) {
        log.warn("Circuit breaker fallback for user: {}, reason: {}", userId, t.getMessage());
        return Mono.just(new HashMap<>());
    }

    @CircuitBreaker(name = "gatewayCircuitBreaker", fallbackMethod = "fetchProductFallback")
    private Mono<Map> fetchProductWithCircuitBreaker(String productId) {
        if (productId == null || productId.isBlank()) {
            return Mono.just(new HashMap<>());
        }
        return fetchFromService("product-service", "/products/" + productId);
    }

    private Mono<Map> fetchProductFallback(String productId, Throwable t) {
        log.warn("Circuit breaker fallback for product: {}, reason: {}", productId, t.getMessage());
        return Mono.just(new HashMap<>());
    }

    private String extractStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    /**
     * Get product statistics for admin dashboard.
     *
     * @return aggregated product statistics
     */
    @CircuitBreaker(name = "gatewayCircuitBreaker", fallbackMethod = "getProductStatsFallback")
    public Mono<Map<String, Object>> getProductStats() {
        String cacheKey = "product:stats";

        return cacheService.get(cacheKey, Map.class)
                .switchIfEmpty(Mono.defer(() -> fetchProductStatsFromServices()
                        .flatMap(result -> cacheService.set(cacheKey, result).thenReturn(result))));
    }

    private Mono<Map<String, Object>> getProductStatsFallback(Throwable t) {
        log.warn("Circuit breaker fallback for getProductStats: {}", t.getMessage());
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("products", new HashMap<>());
        fallback.put("categories", new HashMap<>());
        fallback.put("circuitBreakerFallback", true);
        return Mono.just(fallback);
    }

    private Mono<Map<String, Object>> fetchProductStatsFromServices() {
        log.debug("Fetching product stats from services");

        Mono<Map> productsMono = fetchFromService("product-service", "/products")
                .map(this::extractProductStats)
                .onErrorResume(e -> {
                    log.warn("Failed to fetch products: {}", e.getMessage());
                    return Mono.just(new HashMap<>());
                });

        Mono<Map> categoriesMono = fetchFromService("product-service", "/products/all")
                .map(this::extractCategoryStats)
                .onErrorResume(e -> {
                    log.warn("Failed to fetch categories: {}", e.getMessage());
                    return Mono.just(new HashMap<>());
                });

        return Mono.zip(productsMono, categoriesMono)
                .map(tuple -> {
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("products", tuple.getT1());
                    stats.put("categories", tuple.getT2());
                    return stats;
                });
    }

    private Map<String, Object> extractProductStats(JsonNode node) {
        Map<String, Object> stats = new HashMap<>();
        if (node != null && node.has("totalProducts")) {
            stats.put("total", node.get("totalProducts").asLong());
        }
        if (node != null && node.has("products")) {
            stats.put("items", node.get("products"));
        }
        return stats;
    }

    private Map<String, Object> extractCategoryStats(JsonNode node) {
        Map<String, Object> stats = new HashMap<>();
        if (node != null && node.isArray()) {
            Map<String, Long> categoryCount = new HashMap<>();
            node.forEach(product -> {
                if (product.has("category")) {
                    String category = product.get("category").asText();
                    categoryCount.merge(category, 1L, Long::sum);
                }
            });
            stats.put("byCategory", categoryCount);
        }
        return stats;
    }

    /**
     * Get dashboard aggregated data.
     * Combines data from order-service, product-service, and user-service.
     *
     * @return aggregated dashboard data
     */
    @CircuitBreaker(name = "gatewayCircuitBreaker", fallbackMethod = "getDashboardDataFallback")
    public Mono<Map<String, Object>> getDashboardData() {
        String cacheKey = "dashboard:data";

        return cacheService.get(cacheKey, Map.class)
                .switchIfEmpty(Mono.defer(() -> fetchDashboardDataFromServices()
                        .flatMap(result -> cacheService.set(cacheKey, result).thenReturn(result))));
    }

    private Mono<Map<String, Object>> getDashboardDataFallback(Throwable t) {
        log.warn("Circuit breaker fallback for getDashboardData: {}", t.getMessage());
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("orders", new HashMap<>());
        fallback.put("products", new HashMap<>());
        fallback.put("users", new HashMap<>());
        fallback.put("circuitBreakerFallback", true);
        fallback.put("timestamp", System.currentTimeMillis());
        return Mono.just(fallback);
    }

    private Mono<Map<String, Object>> fetchDashboardDataFromServices() {
        log.debug("Fetching dashboard data from services");

        Mono<Map> orderStatsMono = fetchOrderStats();
        Mono<Map> productStatsMono = fetchProductStatsFromServices();
        Mono<Map> userStatsMono = fetchUserStats();

        return Mono.zip(orderStatsMono, productStatsMono, userStatsMono)
                .map(tuple -> {
                    Map<String, Object> dashboard = new HashMap<>();
                    dashboard.put("orders", tuple.getT1());
                    dashboard.put("products", tuple.getT2());
                    dashboard.put("users", tuple.getT3());
                    dashboard.put("timestamp", System.currentTimeMillis());
                    return dashboard;
                });
    }

    private Mono<Map> fetchOrderStats() {
        return fetchFromService("order-service", "/orders/all")
                .map(node -> {
                    Map<String, Object> stats = new HashMap<>();
                    if (node != null && node.isArray()) {
                        stats.put("totalOrders", node.size());
                        // Calculate revenue, pending orders, etc.
                        long revenue = 0;
                        int pending = 0;
                        int completed = 0;
                        int cancelled = 0;
                        for (JsonNode order : node) {
                            if (order.has("totalPrice")) {
                                revenue += order.get("totalPrice").asDouble();
                            }
                            if (order.has("status")) {
                                String status = order.get("status").asText();
                                switch (status) {
                                    case "PENDING_PAYMENT", "PAID", "PROCESSING" -> pending++;
                                    case "COMPLETED" -> completed++;
                                    case "CANCELLED", "REFUNDED" -> cancelled++;
                                }
                            }
                        }
                        stats.put("revenue", revenue);
                        stats.put("pendingOrders", pending);
                        stats.put("completedOrders", completed);
                        stats.put("cancelledOrders", cancelled);
                    }
                    return stats;
                })
                .onErrorResume(e -> {
                    log.warn("Failed to fetch order stats: {}", e.getMessage());
                    return Mono.just(new HashMap<>());
                });
    }

    private Mono<Map> fetchUserStats() {
        return fetchFromService("user-service", "/users")
                .map(node -> {
                    Map<String, Object> stats = new HashMap<>();
                    if (node != null && node.isArray()) {
                        stats.put("totalUsers", node.size());
                    }
                    return stats;
                })
                .onErrorResume(e -> {
                    log.warn("Failed to fetch user stats: {}", e.getMessage());
                    return Mono.just(new HashMap<>());
                });
    }

    /**
     * Generic method to fetch data from a microservice.
     *
     * @param serviceId the service ID in discovery
     * @param path the API path
     * @return JSON response as JsonNode
     */
    private Mono<JsonNode> fetchFromService(String serviceId, String path) {
        WebClient webClient = webClientBuilder
                .baseUrl("http://" + serviceId)
                .build();

        return webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofMillis(aggregationProperties.getTimeout().getMillis()))
                .doOnSuccess(data -> log.debug("Fetched from {}: {}", serviceId, path))
                .doOnError(e -> log.warn("Failed to fetch from {}: {}", serviceId, e.getMessage()));
    }

    /**
     * Invalidate cache for order detail.
     *
     * @param orderId the order ID
     * @return Mono completes when invalidation is done
     */
    public Mono<Void> invalidateOrderDetail(String orderId) {
        String cacheKey = "order:detail:" + orderId;
        return cacheService.delete(cacheKey).then();
    }

    /**
     * Invalidate dashboard cache.
     *
     * @return Mono completes when invalidation is done
     */
    public Mono<Void> invalidateDashboard() {
        return cacheService.delete("dashboard:data").then();
    }

    /**
     * Invalidate product stats cache.
     *
     * @return Mono completes when invalidation is done
     */
    public Mono<Void> invalidateProductStats() {
        return cacheService.delete("product:stats").then();
    }
}
