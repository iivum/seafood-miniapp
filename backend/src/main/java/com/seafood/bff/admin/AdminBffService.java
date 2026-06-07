package com.seafood.bff.admin;

import com.seafood.bff.admin.dto.DashboardResponse;
import com.seafood.bff.admin.dto.OrderDetailResponse;
import com.seafood.bff.admin.dto.OrderStatsResponse;
import com.seafood.bff.admin.dto.TopProductResponse;
import com.seafood.order.api.dto.OrderResponse;
import com.seafood.order.application.OrderService;
import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.api.dto.ProductStatsResponse;
import com.seafood.product.application.ProductService;
import com.seafood.shared.error.NotFoundException;
import com.seafood.user.api.dto.UserResponse;
import com.seafood.user.application.UserService;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理后台 BFF 服务(参见 design.md §5.1,specs/backend-api §Admin BFF aggregation)。
 *
 * <p>约束:
 * <ul>
 *   <li>只通过 ApplicationService 跨模块交互,绝不直接碰 Repository(design §1.3)</li>
 *   <li>不缓存(决策 4) — 后续 P99 > 500ms 时再加 Caffeine</li>
 *   <li>ADMIN-only,由 SecurityConfig URL 规则 + Controller @PreAuthorize 双重防护</li>
 * </ul>
 */
@Service
public class AdminBffService {

    private static final int TOP_N = 10;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final OrderService orders;
    private final ProductService products;
    private final UserService users;
    private final com.seafood.product.application.ProductQueryService productStats;

    public AdminBffService(OrderService orders,
                           ProductService products,
                           UserService users,
                           com.seafood.product.application.ProductQueryService productStats) {
        this.orders = orders;
        this.products = products;
        this.users = users;
        this.productStats = productStats;
    }

    // ----- 6.1 order detail -----

    public OrderDetailResponse orderDetail(String orderId) {
        OrderResponse order = orders.get(orderId);
        // 用户查看:借 ADMIN 越权(仅本方法内)— UserService 校验 ADMIN 即放行
        UserResponse customer = users.get(order.userId(),
                new com.seafood.shared.security.UserPrincipal("__bff__",
                        com.seafood.shared.security.Role.ADMIN));

        List<OrderDetailResponse.ItemWithProduct> items = new ArrayList<>(order.items().size());
        for (var item : order.items()) {
            ProductResponse product;
            try {
                product = products.get(item.productId());
            } catch (NotFoundException ignore) {
                // 商品已删;订单行仍展示,商品字段 null — 前端按需显示"已下架"
                product = null;
            }
            items.add(new OrderDetailResponse.ItemWithProduct(
                    item.productId(), item.productName(),
                    item.unitPrice(), item.quantity(), product));
        }
        return new OrderDetailResponse(order, customer, items);
    }

    // ----- 6.2 product stats(直接复用 ProductStatsResponse)-----

    public ProductStatsResponse productStats() {
        return productStats.stats();
    }

    // ----- 6.3 dashboard -----

    public DashboardResponse dashboard() {
        Instant now = Instant.now();
        Instant startOfToday = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
        Instant startOfWeek = LocalDate.now(ZONE)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(ZONE).toInstant();
        Instant startOfMonth = LocalDate.now(ZONE)
                .withDayOfMonth(1)
                .atStartOfDay(ZONE).toInstant();

        OrderStatsResponse orderStats = new OrderStatsResponse(
                orders.countCreatedSince(startOfToday),
                orders.countCreatedSince(startOfWeek),
                orders.countCreatedSince(startOfMonth));
        ProductStatsResponse prodStats = productStats.stats();
        List<TopProductResponse> top = topProducts();
        return new DashboardResponse(orderStats, prodStats, top);
    }

    private List<TopProductResponse> topProducts() {
        // 拉最近 500 单,在内存聚合
        List<OrderResponse> recent = orders.findRecent(500);
        Map<String, Long> qtyByProduct = new HashMap<>();
        for (OrderResponse o : recent) {
            for (var it : o.items()) {
                qtyByProduct.merge(it.productId(), (long) it.quantity(), Long::sum);
            }
        }
        if (qtyByProduct.isEmpty()) {
            return List.of();
        }
        // 取 top 10 productId,再批量拉 product 元数据
        List<Map.Entry<String, Long>> top = qtyByProduct.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_N)
                .toList();
        Set<String> ids = top.stream().map(Map.Entry::getKey).collect(Collectors.toSet());
        Map<String, ProductResponse> productById = new HashMap<>();
        for (String id : ids) {
            try {
                productById.put(id, products.get(id));
            } catch (NotFoundException ignore) {
                // 商品已删,跳过元数据,销量仍保留
            }
        }
        return top.stream()
                .map(e -> new TopProductResponse(productById.get(e.getKey()), e.getValue()))
                .filter(t -> t.product() != null)
                .sorted(Comparator.comparingLong(TopProductResponse::totalQuantitySold).reversed())
                .toList();
    }
}
