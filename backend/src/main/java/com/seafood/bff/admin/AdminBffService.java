package com.seafood.bff.admin;

import com.seafood.bff.admin.dto.DashboardResponse;
import com.seafood.bff.admin.dto.OrderDetailResponse;
import com.seafood.bff.admin.dto.OrderStatsResponse;
import com.seafood.bff.admin.dto.TopProductResponse;
import com.seafood.bff.admin.dto.TrendPointResponse;
import com.seafood.order.api.dto.OrderResponse;
import com.seafood.order.application.OrderService;
import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.api.dto.ProductStatsResponse;
import com.seafood.product.application.ProductService;
import com.seafood.shared.error.NotFoundException;
import com.seafood.user.api.dto.UserResponse;
import com.seafood.user.application.UserService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private static final int LOW_STOCK_THRESHOLD = 10;
    private static final int TREND_DAYS = 7;
    private static final int RECENT_ORDERS_LIMIT = 10;
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
        Instant startOfToday = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
        Instant startOfWeek = LocalDate.now(ZONE)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(ZONE).toInstant();
        Instant startOfMonth = LocalDate.now(ZONE)
                .withDayOfMonth(1)
                .atStartOfDay(ZONE).toInstant();

        // countCreatedSince 走 DB 计数,sumTotalAmountCreatedSince 走内存扫描,两次调用非事务一致
        long todayCount = orders.countCreatedSince(startOfToday);
        BigDecimal gmvToday = orders.sumTotalAmountCreatedSince(startOfToday);
        BigDecimal avgOrderToday = todayCount > 0
                ? gmvToday.divide(BigDecimal.valueOf(todayCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        OrderStatsResponse orderStats = new OrderStatsResponse(
                todayCount,
                orders.countCreatedSince(startOfWeek),
                orders.countCreatedSince(startOfMonth),
                gmvToday,
                avgOrderToday);
        ProductStatsResponse prodStats = productStats.stats();
        List<TopProductResponse> top = topProducts();
        List<TrendPointResponse> trend7d = trend7d();
        List<ProductResponse> lowStock = productStats.lowStock(LOW_STOCK_THRESHOLD).stream()
                .limit(TOP_N)
                .toList();
        // 路线图 2.21:近期订单流(最近 10 单,按 createdAt 倒序,findRecent 内部已排序)
        List<com.seafood.order.api.dto.OrderResponse> recentOrders = orders.findRecent(RECENT_ORDERS_LIMIT);
        return new DashboardResponse(orderStats, prodStats, top, trend7d, lowStock, recentOrders);
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

    /**
     * 路线图 2.17:7 天订单数折线(今日 + 前 6 天,UTC+8 当日为界)。
     *
     * <p>实现:对每一天的起点跑 {@code countCreatedSince(startOfDay)} — 拿到「从 startOfDay
     * 到现在的累计订单数」。相邻两天累计数相减 = 当日新增订单数。
     * 共 7 次 DB 读(顺序);7 天趋势刷新 P99 应 < 100ms(admin 仪表盘非热路径)。
     *
     * <p>性能优化方向:用 MongoDB aggregation pipeline 一次返回 7 桶;
     * 万级订单时再做(2.5 Sprint 1 末 spike 同款 P99 监控)。
     */
    private List<TrendPointResponse> trend7d() {
        LocalDate today = LocalDate.now(ZONE);
        long[] cumulative = new long[TREND_DAYS];
        // cumulative[i] = 从 (today - i) 天 0 点至今的累计订单数(单调非递减)
        // i=0 → 今天 0 点(最小,只含今天); i=6 → 6 天前 0 点(最大,含 7 天)
        for (int i = 0; i < TREND_DAYS; i++) {
            Instant from = today.minusDays(i).atStartOfDay(ZONE).toInstant();
            cumulative[i] = orders.countCreatedSince(from);
        }
        // 倒序产出 oldest → newest,与折线图 X 轴方向一致
        List<TrendPointResponse> out = new ArrayList<>(TREND_DAYS);
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            long perDay;
            if (i == 0) {
                perDay = cumulative[0]; // 今天 0 点至今 = 今天新增
            } else {
                // cumulative[i] 包含 (today-i) 当天 + 之后;
                // cumulative[i-1] 不含 (today-i) 当天(只含 (today-i+1) 之后);
                // 差 = (today-i) 当天新增
                perDay = cumulative[i] - cumulative[i - 1];
            }
            out.add(new TrendPointResponse(today.minusDays(i), perDay));
        }
        return out;
    }
}
