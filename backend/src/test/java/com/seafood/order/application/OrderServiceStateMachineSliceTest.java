package com.seafood.order.application;

import com.seafood.order.infra.CartRepository;
import com.seafood.order.infra.OrderDocument;
import com.seafood.order.infra.OrderRepository;
import com.seafood.order.infra.RefundRepository;
import com.seafood.product.infra.ProductRepository;
import com.seafood.testsupport.builders.OrderBuilder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceStateMachineSliceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CartRepository cartRepository;
    @Mock private ProductRepository productRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private MeterRegistry meterRegistry;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        lenient().when(meterRegistry.counter(anyString(), any(String[].class)))
            .thenReturn(org.mockito.Mockito.mock(Counter.class));
        orderService = new OrderService(orderRepository, cartRepository,
            productRepository, refundRepository, meterRegistry);
    }

    private static OrderDocument doc(String id, String status) {
        var order = OrderBuilder.anOrder().withId(id).build();
        OrderDocument d = new OrderDocument();
        d.setId(order.id());
        d.setUserId(order.userId());
        d.setItems(order.items());
        d.setTotalAmount(order.totalAmount());
        d.setStatus(status);
        d.setCreatedAt(order.createdAt());
        d.setUpdatedAt(order.updatedAt());
        return d;
    }

    @Test
    void cancel_paidOrder_succeeds() {
        when(orderRepository.findById("o-1"))
            .thenReturn(Optional.of(doc("o-1", "PAID")));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resp = orderService.cancel("o-1", "user changed mind");

        assertThat(resp.status()).isEqualTo("CANCELLED");
        assertThat(resp.cancelReason()).isEqualTo("user changed mind");
    }

    @Test
    void markPaid_pendingOrder_succeeds() {
        when(orderRepository.findById("o-1"))
            .thenReturn(Optional.of(doc("o-1", "PENDING")));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resp = orderService.markPaid("o-1");

        assertThat(resp.status()).isEqualTo("PAID");
    }

    @Test
    void confirmReceive_shippedOrder_succeeds() {
        when(orderRepository.findById("o-1"))
            .thenReturn(Optional.of(doc("o-1", "SHIPPED")));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resp = orderService.confirmReceive("o-1");

        assertThat(resp.status()).isNotEqualTo("SHIPPED");
    }

    @Test
    void rebuy_completedOrder_returnsCartItems() {
        // REBUY allowed from COMPLETED / CANCELLED / REFUNDED (per OrderAction rules)
        when(orderRepository.findById("o-1"))
            .thenReturn(Optional.of(doc("o-1", "COMPLETED")));

        var resp = orderService.rebuy("o-1");

        assertThat(resp).isNotEmpty();
    }
}
