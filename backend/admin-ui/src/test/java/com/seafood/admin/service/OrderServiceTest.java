package com.seafood.admin.service;

import com.seafood.admin.client.OrderClient;
import com.seafood.admin.client.OrderResponse;
import com.seafood.admin.client.ShipOrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderClient orderClient;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderClient);
    }

    @Test
    @DisplayName("getAllOrders returns list of orders")
    void getAllOrders_returnsOrders() {
        OrderResponse order = new OrderResponse();
        order.setId("1");
        when(orderClient.getAllOrders()).thenReturn(List.of(order));

        List<OrderResponse> result = orderService.getAllOrders();

        assertThat(result).hasSize(1);
        verify(orderClient).getAllOrders();
    }

    @Test
    @DisplayName("getAllOrders returns empty list on error")
    void getAllOrders_returnsEmptyListOnError() {
        when(orderClient.getAllOrders()).thenThrow(new RuntimeException("Network error"));

        List<OrderResponse> result = orderService.getAllOrders();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("updateOrderStatus throws exception for blank id")
    void updateOrderStatus_throwsExceptionForBlankId() {
        assertThatThrownBy(() -> orderService.updateOrderStatus("", "PAID"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Order ID is required");
    }

    @Test
    @DisplayName("updateOrderStatus throws exception for blank status")
    void updateOrderStatus_throwsExceptionForBlankStatus() {
        assertThatThrownBy(() -> orderService.updateOrderStatus("1", ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Status is required");
    }

    @Test
    @DisplayName("shipOrder calls client with valid request")
    void shipOrder_callsClientWithValidRequest() {
        ShipOrderRequest request = new ShipOrderRequest("SF", "顺丰", "SF123456");
        OrderResponse response = new OrderResponse();
        response.setId("1");
        when(orderClient.shipOrder(eq("1"), any())).thenReturn(response);

        OrderResponse result = orderService.shipOrder("1", request);

        assertThat(result.getId()).isEqualTo("1");
        verify(orderClient).shipOrder(eq("1"), any());
    }

    @Test
    @DisplayName("shipOrder throws exception for null request")
    void shipOrder_throwsExceptionForNullRequest() {
        assertThatThrownBy(() -> orderService.shipOrder("1", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("request cannot be null");
    }

    @Test
    @DisplayName("countByStatus returns correct count")
    void countByStatus_returnsCorrectCount() {
        OrderResponse paid1 = new OrderResponse();
        paid1.setStatus("PAID");
        OrderResponse paid2 = new OrderResponse();
        paid2.setStatus("PAID");
        OrderResponse shipped = new OrderResponse();
        shipped.setStatus("SHIPPED");
        when(orderClient.getAllOrders()).thenReturn(List.of(paid1, paid2, shipped));

        long count = orderService.countByStatus("PAID");

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("calculateRevenue sums totalPrice for PAID, SHIPPED, COMPLETED orders")
    void calculateRevenue_sumsCorrectOrders() {
        OrderResponse paid = new OrderResponse();
        paid.setStatus("PAID");
        paid.setTotalPrice(BigDecimal.valueOf(100));
        OrderResponse shipped = new OrderResponse();
        shipped.setStatus("SHIPPED");
        shipped.setTotalPrice(BigDecimal.valueOf(200));
        OrderResponse pending = new OrderResponse();
        pending.setStatus("PENDING");
        pending.setTotalPrice(BigDecimal.valueOf(50));
        when(orderClient.getAllOrders()).thenReturn(List.of(paid, shipped, pending));

        BigDecimal revenue = orderService.calculateRevenue();

        assertThat(revenue).isEqualByComparingTo(BigDecimal.valueOf(300));
    }
}
