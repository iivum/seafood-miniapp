package com.seafood.bff.admin;

import com.seafood.bff.admin.dto.DashboardResponse;
import com.seafood.bff.admin.dto.OrderDetailResponse;
import com.seafood.bff.admin.dto.OrderStatsResponse;
import com.seafood.bff.admin.dto.TopProductResponse;
import com.seafood.order.api.dto.OrderResponse;
import com.seafood.order.application.OrderService;
import com.seafood.order.domain.OrderItem;
import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.api.dto.ProductStatsResponse;
import com.seafood.product.application.ProductQueryService;
import com.seafood.product.application.ProductService;
import com.seafood.product.domain.ProductStatus;
import com.seafood.shared.error.NotFoundException;
import com.seafood.shared.security.Role;
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
        return new OrderResponse(id, userId, items, total, "PENDING", null,
                Instant.parse("2026-06-03T00:00:00Z"), Instant.parse("2026-06-03T00:00:00Z"));
    }

    private OrderItem item(String productId, String name, int qty) {
        return new OrderItem(productId, name, new BigDecimal("99.00"), qty);
    }

    private ProductResponse sampleProduct(String id) {
        return new ProductResponse(id, "三文鱼", "x", new BigDecimal("99.00"), 10,
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
    void dashboard_aggregatesAllThreeSections() {
        when(orders.countCreatedSince(any())).thenReturn(3L, 18L, 70L);
        when(productStats.stats()).thenReturn(new ProductStatsResponse(50L, 45L, 5L,
                Map.of("鱼类", 12L)));
        when(orders.findRecent(500)).thenReturn(List.of(
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
    }

    @Test
    void dashboard_capsAt10() {
        when(orders.countCreatedSince(any())).thenReturn(0L);
        when(productStats.stats()).thenReturn(new ProductStatsResponse(0L, 0L, 0L, Map.of()));
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
        when(orders.findRecent(500)).thenReturn(List.of());

        DashboardResponse dash = bff.dashboard();

        assertThat(dash.topProducts()).isEmpty();
    }
}
