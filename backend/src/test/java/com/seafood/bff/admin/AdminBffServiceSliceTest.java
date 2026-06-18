package com.seafood.bff.admin;

import com.seafood.bff.admin.dto.OrderDetailResponse;
import com.seafood.order.application.OrderService;
import com.seafood.order.api.dto.OrderResponse;
import com.seafood.product.api.dto.ProductResponse;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * AdminBffService direct unit test — covers orderDetail path and productStats
 * delegation. Dashboard is a heavier aggregation tested implicitly via the
 * existing AdminBffControllerSliceTest; here we focus on the helpers.
 */
@ExtendWith(MockitoExtension.class)
class AdminBffServiceSliceTest {

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
    void productStats_delegatesToProductQueryService() {
        var expected = new com.seafood.product.api.dto.ProductStatsResponse(
            10L, 7L, 3L, Map.of("鱼类", 5L));
        when(productQueryService.stats()).thenReturn(expected);

        var resp = bffService.productStats();

        assertThat(resp).isSameAs(expected);
        assertThat(resp.total()).isEqualTo(10L);
        assertThat(resp.byCategory()).containsEntry("鱼类", 5L);
    }

    @Test
    void orderDetail_orderNotFound_throwsNotFound() {
        when(orderService.get("o-missing"))
            .thenThrow(new NotFoundException("订单不存在"));

        assertThatThrownBy(() -> bffService.orderDetail("o-missing"))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void orderDetail_returnsAssembledDetail() {
        var order = OrderBuilder.anOrder().withId("o-1").build();
        when(orderService.get("o-1")).thenReturn(OrderResponse.from(order));
        var product = ProductBuilder.aProduct().withId("p-1").build();
        when(productService.get(any())).thenReturn(
            new ProductResponse("p-1", product.name(), product.description(),
                product.price(), product.stock(), "鱼类", product.imageUrl(),
                ProductStatus.ACTIVE, Instant.now(), Instant.now()));
        when(userService.get(any(), any())).thenReturn(
            new com.seafood.user.api.dto.UserResponse(
                "u-1", "open-1", "test", null, "CUSTOMER", null, java.util.List.of(),
                java.time.Instant.parse("2026-06-19T00:00:00Z")));

        OrderDetailResponse resp = bffService.orderDetail("o-1");

        assertThat(resp.order().id()).isEqualTo("o-1");
        assertThat(resp.customer().id()).isEqualTo("u-1");
    }
}
