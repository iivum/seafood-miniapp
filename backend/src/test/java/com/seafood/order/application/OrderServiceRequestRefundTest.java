package com.seafood.order.application;

import com.seafood.order.api.dto.RefundResponse;
import com.seafood.order.infra.CartRepository;
import com.seafood.order.infra.OrderDocument;
import com.seafood.order.infra.OrderRepository;
import com.seafood.order.infra.RefundDocument;
import com.seafood.order.infra.RefundRepository;
import com.seafood.product.infra.ProductRepository;
import com.seafood.shared.error.DomainException;
import com.seafood.testsupport.builders.OrderBuilder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceRequestRefundTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CartRepository cartRepository;
    @Mock private ProductRepository productRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private MeterRegistry meterRegistry;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        lenient().when(meterRegistry.counter(anyString(), any(String[].class)))
            .thenReturn(mock(Counter.class));
        orderService = new OrderService(orderRepository, cartRepository,
            productRepository, refundRepository, meterRegistry);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String userId, com.seafood.shared.security.Role role) {
        var principal = new com.seafood.shared.security.UserPrincipal(userId, role);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static OrderDocument orderDoc(String id, String userId, String status) {
        var order = OrderBuilder.anOrder().withId(id).withUserId(userId).build();
        OrderDocument d = new OrderDocument();
        d.setId(id);
        d.setUserId(userId);
        d.setItems(order.items());
        d.setTotalAmount(order.totalAmount());
        d.setStatus(status);
        d.setCreatedAt(order.createdAt());
        d.setUpdatedAt(order.updatedAt());
        return d;
    }

    private static RefundDocument refundDoc(String refundId, String orderId, String userId) {
        RefundDocument d = new RefundDocument();
        d.setId(refundId);
        d.setOrderId(orderId);
        d.setUserId(userId);
        d.setAmount(new BigDecimal("198.00"));
        d.setReason("质量问题");
        d.setStatus("REQUESTED");
        d.setCreatedAt(Instant.now());
        d.setUpdatedAt(Instant.now());
        return d;
    }

    // ── COMPLETED → REFUNDING 成功路径 ──────────────────────────────────

    @Test
    void requestRefund_completedOrder_createsRefundAndTransitionsToRefunding() {
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(orderDoc("o-1", "u1", "COMPLETED")));
        when(refundRepository.findByOrderId("o-1")).thenReturn(Optional.empty());
        when(refundRepository.save(any())).thenReturn(refundDoc("ref-1", "o-1", "u1"));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefundResponse resp = orderService.requestRefund("o-1", new BigDecimal("198.00"), "质量问题");

        assertThat(resp.status()).isEqualTo("REQUESTED");
        assertThat(resp.orderId()).isEqualTo("o-1");
        assertThat(resp.userId()).isEqualTo("u1");
    }

    // ── 状态校验:PENDING 不允许申请 ──────────────────────────────────────

    @Test
    void requestRefund_pendingOrder_throwsDomainException() {
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(orderDoc("o-1", "u1", "PENDING")));

        assertThatThrownBy(() -> orderService.requestRefund("o-1", new BigDecimal("50.00"), "理由"))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining("不允许申请退款");
    }
}
