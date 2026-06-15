package com.seafood.bff.admin;

import com.seafood.bff.admin.dto.DashboardResponse;
import com.seafood.bff.admin.dto.OrderDetailResponse;
import com.seafood.order.api.dto.OrderResponse;
import com.seafood.order.application.OrderService;
import com.seafood.order.domain.OrderItem;
import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.api.dto.ProductStatsResponse;
import com.seafood.product.application.ProductQueryService;
import com.seafood.product.application.ProductService;
import com.seafood.product.domain.ProductStatus;
import com.seafood.shared.error.NotFoundException;
import com.seafood.shared.security.UserPrincipal;
import com.seafood.user.api.dto.UserResponse;
import com.seafood.user.application.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminBffServiceTest {

    private OrderService orders;
    private ProductService products;
    private UserService users;
    private ProductQueryService productStats;
    private AdminBffService bff;

    @BeforeEach
    void setUp() {
        orders = mock(OrderService.class);
        products = mock(ProductService.class);
        users = mock(UserService.class);
        productStats = mock(ProductQueryService.class);
        bff = new AdminBffService(orders, products, users, productStats);
    }

    private OrderResponse sampleOrder(String id, String userId, List<OrderItem> items, BigDecimal total) {
        return new OrderResponse(id, userId, items, total, "PENDING", null, null, null,
                Instant.parse("2026-06-03T00:00:00Z"), Instant.parse("2026-06-03T00:00:00Z"));
    }

    private OrderItem item(String productId, String name, int qty) {
        return new OrderItem(productId, name, new BigDecimal("99.00"), qty);
    }

    private ProductResponse sampleProduct(String id) {
        return sampleProduct(id, 10);
    }

    private ProductResponse sampleProduct(String id, int stock) {
        return new ProductResponse(id, "三文鱼", "x", new BigDecimal("99.00"), stock,
                "鱼类", "http://img", ProductStatus.ACTIVE, Instant.now(), Instant.now());
    }

    // ----- 6.1 orderDetail -----

    @Test
    void orderDetail_aggregatesOrderCustomerAndLineProducts() {
        OrderResponse order = sampleOrder("o1", "u1",
                List.of(item("p1", "三文鱼", 2), item("p2", "金枪鱼", 1)),
                new BigDecimal("297.00"));
        when(orders.get("o1")).thenReturn(order);
        when(users.get(eq("u1"), any(UserPrincipal.class)))
                .thenReturn(new UserResponse("u1", "open-1", "张三", "http://a",
                        "CUSTOMER", "13900000000", List.of(), Instant.parse("2026-06-01T00:00:00Z")));
        when(products.get("p1")).thenReturn(sampleProduct("p1"));
        when(products.get("p2")).thenReturn(sampleProduct("p2"));

        OrderDetailResponse detail = bff.orderDetail("o1");

        assertThat(detail.order().id()).isEqualTo("o1");
        assertThat(detail.customer().id()).isEqualTo("u1");
        assertThat(detail.items()).hasSize(2);
        assertThat(detail.items().get(0).product().id()).isEqualTo("p1");
        assertThat(detail.items().get(0).quantity()).isEqualTo(2);
    }

    @Test
    void orderDetail_keepsMissingProductRowWithNullProduct() {
        // 商品已删 → 行项保留(订单完整性),product 字段 null(前端按需显示"已下架")
        OrderResponse order = sampleOrder("o1", "u1",
                List.of(item("p1", "三文鱼", 2), item("gone", "已删", 1)),
                new BigDecimal("297.00"));
        when(orders.get("o1")).thenReturn(order);
        when(users.get(eq("u1"), any())).thenReturn(new UserResponse("u1", "open-1", "n", "u",
                "CUSTOMER", null, List.of(), Instant.now()));
        when(products.get("p1")).thenReturn(sampleProduct("p1"));
        when(products.get("gone")).thenThrow(new NotFoundException("商品不存在:gone"));

        OrderDetailResponse detail = bff.orderDetail("o1");

        assertThat(detail.items()).hasSize(2);
        assertThat(detail.items().get(0).productId()).isEqualTo("p1");
        assertThat(detail.items().get(0).product()).isNotNull();
        assertThat(detail.items().get(1).productId()).isEqualTo("gone");
        assertThat(detail.items().get(1).product()).isNull();
    }

    @Test
    void orderDetail_orderMissing_throws() {
        when(orders.get("missing")).thenThrow(new NotFoundException("订单不存在:missing"));
        assertThatThrownBy(() -> bff.orderDetail("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    // ----- 6.2 productStats -----

    @Test
    void productStats_delegatesToQueryService() {
        ProductStatsResponse stats = new ProductStatsResponse(50L, 45L, 5L,
                Map.of("鱼类", 12L, "虾蟹", 8L));
        when(productStats.stats()).thenReturn(stats);

        ProductStatsResponse res = bff.productStats();

        assertThat(res.total()).isEqualTo(50);
        assertThat(res.byCategory()).containsEntry("鱼类", 12L);
    }

    // ----- 6.3 dashboard -----

    @Test
    void dashboard_aggregatesAllFiveSections() {
        // 3 个 countCreatedSince(today/week/month) + 7 个 trend7d = 10 次调用
        // stub 返回 10 个值,3-arg 后是 trend7d
        when(orders.countCreatedSince(any())).thenReturn(
                3L,   // today
                18L,  // week
                70L,  // month
                // 7 个 trend7d cumulative(从 today-6 累计到 today-0,各桶) ↓
                100L, 120L, 150L, 200L, 250L, 280L, 300L);
        when(productStats.stats()).thenReturn(new ProductStatsResponse(50L, 45L, 5L,
                Map.of("鱼类", 12L)));
        when(productStats.lowStock(10)).thenReturn(List.of(
                sampleProduct("p-low-1", 3),
                sampleProduct("p-low-2", 7)));
        when(orders.findRecent(500)).thenReturn(List.of(
                sampleOrder("o1", "u1", List.of(item("p1", "三文鱼", 3)), new BigDecimal("297")),
                sampleOrder("o2", "u1", List.of(item("p1", "三文鱼", 2), item("p2", "金枪鱼", 5)),
                        new BigDecimal("693"))));
        // 2.21 recentOrders:RECENT_ORDERS_LIMIT = 10
        when(orders.findRecent(10)).thenReturn(List.of(
                sampleOrder("o1", "u1", List.of(item("p1", "三文鱼", 3)), new BigDecimal("297")),
                sampleOrder("o2", "u1", List.of(item("p1", "三文鱼", 2), item("p2", "金枪鱼", 5)),
                        new BigDecimal("693"))));
        when(products.get("p1")).thenReturn(sampleProduct("p1"));
        when(products.get("p2")).thenReturn(sampleProduct("p2"));

        DashboardResponse dash = bff.dashboard();

        assertThat(dash.orderStats().today()).isEqualTo(3L);
        assertThat(dash.orderStats().week()).isEqualTo(18L);
        assertThat(dash.orderStats().month()).isEqualTo(70L);
        assertThat(dash.productStats().total()).isEqualTo(50L);
        assertThat(dash.topProducts()).hasSize(2);
        // 销量:p1 = 3+2=5, p2 = 5 → p1 排前
        assertThat(dash.topProducts().get(0).product().id()).isEqualTo("p1");
        assertThat(dash.topProducts().get(0).totalQuantitySold()).isEqualTo(5L);
        assertThat(dash.topProducts().get(1).totalQuantitySold()).isEqualTo(5L);

        // 2.17 trend7d 7 个点(顺序 oldest→newest,length=7)
        assertThat(dash.trend7d()).hasSize(7);
        // today = cumulative[0] = 100(4th call stub,单调非递减首值)
        assertThat(dash.trend7d().get(6).count()).isEqualTo(100L);
        // 6 天前 = cumulative[6] - cumulative[5] = 300 - 280 = 20
        assertThat(dash.trend7d().get(0).count()).isEqualTo(20L);
        // 5 天前 = 280 - 250 = 30
        assertThat(dash.trend7d().get(1).count()).isEqualTo(30L);
        // 2.18 lowStock:2 个候选
        assertThat(dash.lowStock()).hasSize(2);
        assertThat(dash.lowStock().get(0).id()).isEqualTo("p-low-1");
        assertThat(dash.lowStock().get(0).stock()).isEqualTo(3);
        // 2.21 recentOrders:复用 findRecent(10) 返回最近 10 单
        assertThat(dash.recentOrders()).hasSize(2);
        assertThat(dash.recentOrders().get(0).id()).isEqualTo("o1");
    }

    @Test
    void dashboard_capsAt10() {
        when(orders.countCreatedSince(any())).thenReturn(0L);
        when(productStats.stats()).thenReturn(new ProductStatsResponse(0L, 0L, 0L, Map.of()));
        when(productStats.lowStock(10)).thenReturn(List.of());
        List<OrderResponse> many = java.util.stream.IntStream.range(0, 15)
                .mapToObj(i -> sampleOrder("o" + i, "u1",
                        List.of(item("p" + i, "p" + i, 1)), new BigDecimal("1")))
                .toList();
        when(orders.findRecent(500)).thenReturn(many);
        for (int i = 0; i < 15; i++) {
            when(products.get("p" + i)).thenReturn(sampleProduct("p" + i));
        }

        DashboardResponse dash = bff.dashboard();

        assertThat(dash.topProducts()).hasSize(10);
    }

    @Test
    void dashboard_emptyOrders_returnsEmptyTopList() {
        when(orders.countCreatedSince(any())).thenReturn(0L);
        when(productStats.stats()).thenReturn(new ProductStatsResponse(0L, 0L, 0L, Map.of()));
        when(productStats.lowStock(10)).thenReturn(List.of());
        when(orders.findRecent(500)).thenReturn(List.of());

        DashboardResponse dash = bff.dashboard();

        assertThat(dash.topProducts()).isEmpty();
        assertThat(dash.trend7d()).hasSize(7);
        assertThat(dash.trend7d()).allSatisfy(p -> assertThat(p.count()).isEqualTo(0L));
        assertThat(dash.lowStock()).isEmpty();
    }

    @Test
    void dashboard_lowStock_capsAt10() {
        // 2.18:低库存返回按 stock 升序 top 10
        when(orders.countCreatedSince(any())).thenReturn(0L);
        when(productStats.stats()).thenReturn(new ProductStatsResponse(0L, 0L, 0L, Map.of()));
        List<ProductResponse> many = java.util.stream.IntStream.range(0, 15)
                .mapToObj(i -> sampleProduct("low-" + i, i + 1)) // stock 1..15
                .toList();
        when(productStats.lowStock(10)).thenReturn(many);
        when(orders.findRecent(500)).thenReturn(List.of());

        DashboardResponse dash = bff.dashboard();

        // 2.18 截 top 10
        assertThat(dash.lowStock()).hasSize(10);
        assertThat(dash.lowStock().get(0).stock()).isEqualTo(1);
        assertThat(dash.lowStock().get(9).stock()).isEqualTo(10);
    }
}
