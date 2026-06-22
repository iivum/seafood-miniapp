package com.seafood.bff.admin;

import com.seafood.bff.admin.dto.DashboardResponse;
import com.seafood.bff.admin.dto.OrderStatsResponse;
import com.seafood.bff.admin.dto.TopProductResponse;
import com.seafood.bff.admin.dto.TrendPointResponse;
import com.seafood.order.application.OrderService;
import com.seafood.order.api.dto.OrderResponse;
import com.seafood.order.domain.Order;
import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.api.dto.ProductStatsResponse;
import com.seafood.product.application.ProductQueryService;
import com.seafood.product.application.ProductService;
import com.seafood.product.domain.ProductStatus;
import com.seafood.shared.error.NotFoundException;
import com.seafood.testsupport.builders.OrderBuilder;
import com.seafood.testsupport.builders.ProductBuilder;
import com.seafood.user.application.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBffDashboardSliceTest {

    @Mock private OrderService orderService;
    @Mock private ProductService productService;
    @Mock private UserService userService;
    @Mock private ProductQueryService productQueryService;

    private AdminBffService bffService;

    @BeforeEach
    void setUp() {
        bffService = new AdminBffService(orderService, productService, userService, productQueryService);
    }

    @Test
    void dashboard_topProducts_emptyOrders_returnsEmpty() {
        when(orderService.findRecent(500)).thenReturn(List.of());
        when(orderService.countCreatedSince(any())).thenReturn(1L);
        when(orderService.sumTotalAmountCreatedSince(any())).thenReturn(BigDecimal.ZERO);
        when(productQueryService.stats()).thenReturn(
            new ProductStatsResponse(0L, 0L, 0L, Map.of()));
        when(productQueryService.lowStock(10)).thenReturn(List.of());

        DashboardResponse resp = bffService.dashboard();

        assertThat(resp.topProducts()).isEmpty();
        assertThat(resp.orderStats().today()).isEqualTo(1L);
    }

    @Test
    void dashboard_topProducts_skipsMissingProduct() {
        Order order = OrderBuilder.anOrder().withId("o-1").build();
        when(orderService.findRecent(500)).thenReturn(List.of(OrderResponse.from(order)));
        when(orderService.countCreatedSince(any())).thenReturn(1L);
        when(orderService.sumTotalAmountCreatedSince(any())).thenReturn(BigDecimal.ZERO);
        when(productQueryService.stats()).thenReturn(
            new ProductStatsResponse(1L, 1L, 0L, Map.of()));
        when(productQueryService.lowStock(10)).thenReturn(List.of());
        when(productService.get(any())).thenThrow(new NotFoundException("商品不存在"));

        DashboardResponse resp = bffService.dashboard();

        // No items in order → topProducts empty; NotFoundException is catch-all guard
        assertThat(resp.topProducts()).isEmpty();
    }

    @Test
    void dashboard_trend7d_computes7Points() {
        when(orderService.findRecent(anyInt())).thenReturn(List.of());
        when(orderService.countCreatedSince(any())).thenReturn(0L);
        when(orderService.sumTotalAmountCreatedSince(any())).thenReturn(BigDecimal.ZERO);
        when(productQueryService.stats()).thenReturn(
            new ProductStatsResponse(0L, 0L, 0L, Map.of()));
        when(productQueryService.lowStock(10)).thenReturn(List.of());

        DashboardResponse resp = bffService.dashboard();

        // trend7d() computes 7 cumulative points internally
        assertThat(resp.trend7d()).hasSize(7);
    }

    @Test
    void dashboard_lowStock_returnsFromQueryService() {
        var p1 = ProductBuilder.aProduct().withId("p-low-1").withStock(2).build();
        var p2 = ProductBuilder.aProduct().withId("p-low-2").withStock(3).build();
        var p3 = ProductBuilder.aProduct().withId("p-low-3").withStock(1).build();

        when(orderService.findRecent(anyInt())).thenReturn(List.of());
        when(orderService.countCreatedSince(any())).thenReturn(0L);
        when(orderService.sumTotalAmountCreatedSince(any())).thenReturn(BigDecimal.ZERO);
        when(productQueryService.stats()).thenReturn(
            new ProductStatsResponse(3L, 0L, 3L, Map.of()));
        when(productQueryService.lowStock(10)).thenReturn(List.of(
            ProductResponse.from(p1), ProductResponse.from(p2), ProductResponse.from(p3)));

        DashboardResponse resp = bffService.dashboard();

        assertThat(resp.lowStock()).hasSize(3);
    }

    @Test
    void dashboard_orderStats_includesGmvAndAvgOrder() {
        when(orderService.findRecent(500)).thenReturn(List.of());
        when(orderService.findRecent(10)).thenReturn(List.of());
        when(orderService.countCreatedSince(any())).thenReturn(2L, 10L, 40L); // today / week / month
        when(orderService.sumTotalAmountCreatedSince(any())).thenReturn(new BigDecimal("200.00"));
        when(productQueryService.stats()).thenReturn(
            new ProductStatsResponse(0L, 0L, 0L, Map.of()));
        when(productQueryService.lowStock(10)).thenReturn(List.of());

        DashboardResponse resp = bffService.dashboard();

        assertThat(resp.orderStats().gmvToday()).isEqualByComparingTo("200.00");
        assertThat(resp.orderStats().avgOrderToday()).isEqualByComparingTo("100.00"); // 200 / 2
    }
}
