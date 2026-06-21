package com.seafood.order.application;

import com.seafood.bff.admin.dto.BatchShipResponse;
import com.seafood.order.domain.Refund;
import com.seafood.order.infra.CartRepository;
import com.seafood.order.infra.OrderDocument;
import com.seafood.order.infra.OrderRepository;
import com.seafood.order.infra.RefundDocument;
import com.seafood.order.infra.RefundRepository;
import com.seafood.product.infra.ProductRepository;
import com.seafood.shared.error.NotFoundException;
import com.seafood.testsupport.builders.OrderBuilder;
import com.seafood.testsupport.builders.RefundBuilder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * OrderService direct unit test — covers edge cases + partial-failure paths
 * not exercised by the controller slice tests (Sprint 2 A). Goal: lift
 * Jacoco global line coverage from 75% to 80%+ (Sprint 3 A 续).
 *
 * <p>Uses 5-arg constructor (OrderRepository, CartRepository,
 * ProductRepository, RefundRepository, MeterRegistry) so refund methods
 * are available. MeterRegistry is mocked — listPublic-style counter
 * increments are no-ops in this slice.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceSliceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CartRepository cartRepository;
    @Mock private ProductRepository productRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private MeterRegistry meterRegistry;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        // Some methods (e.g. findRecent, listRefunds) don't use MeterRegistry
        // at all. Mark counter() stub as lenient so tests that don't exercise
        // the metrics path don't trip Mockito's UnnecessaryStubbingException.
        org.mockito.Mockito.lenient()
            .when(meterRegistry.counter(anyString(), any(String[].class)))
            .thenReturn(org.mockito.Mockito.mock(Counter.class));
        orderService = new OrderService(orderRepository, cartRepository,
            productRepository, refundRepository, meterRegistry);
    }

    private static OrderDocument docWithStatus(String id, String status) {
        var order = OrderBuilder.anOrder().withId(id).build();
        // Force status by transitioning through Order state machine.
        // For test convenience, use the document directly.
        OrderDocument d = new OrderDocument();
        d.setId(id);
        d.setUserId(order.userId());
        d.setItems(order.items());
        d.setTotalAmount(order.totalAmount());
        d.setStatus(status);
        d.setCreatedAt(order.createdAt());
        d.setUpdatedAt(order.updatedAt());
        return d;
    }

    @Test
    void batchShip_partialFailure_reportsCounts() {
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(docWithStatus("o-1", "PAID")));
        when(orderRepository.findById("o-missing")).thenReturn(Optional.empty());
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BatchShipResponse resp = orderService.batchShip(List.of("o-1", "o-missing"), "SF", "TRK");

        assertThat(resp.successCount()).isEqualTo(1);
        assertThat(resp.failedCount()).isEqualTo(1);
        assertThat(resp.successIds()).containsExactly("o-1");
        assertThat(resp.failed().get(0).orderId()).isEqualTo("o-missing");
    }

    @Test
    void batchShip_carrierAndTrackingMustBothBeSetOrBothEmpty() {
        // carrier set but trackingNumber null — should record a (global) failure
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(docWithStatus("o-1", "PAID")));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BatchShipResponse resp = orderService.batchShip(List.of("o-1"), "SF", null);

        assertThat(resp.failed()).anyMatch(f -> "（global）".equals(f.orderId()) || "(global)".equals(f.orderId()));
    }

    @Test
    void findRecent_underLimit_returnsAllStubbed() {
        OrderDocument d1 = docWithStatus("o-1", "PENDING");
        OrderDocument d2 = docWithStatus("o-2", "PENDING");
        when(orderRepository.findTop500ByOrderByCreatedAtDesc()).thenReturn(List.of(d1, d2));

        var resp = orderService.findRecent(10);

        assertThat(resp).hasSize(2);
    }

    @Test
    void listRefunds_emptyStatus_returnsAll() {
        Refund refund = RefundBuilder.aRefund().withId("r-1").build();
        Page<RefundDocument> page = new PageImpl<>(
            List.of(com.seafood.order.infra.RefundMapper.toDocument(refund)),
            PageRequest.of(0, 20), 1);
        // Empty/blank status → service routes to findAll (not findByStatus)
        when(refundRepository.findAll(any(Pageable.class))).thenReturn(page);

        var resp = orderService.listRefunds("", PageRequest.of(0, 20));

        assertThat(resp.getContent()).hasSize(1);
        assertThat(resp.getContent().get(0).id()).isEqualTo("r-1");
    }

    @Test
    void get_orderNotFound_throwsNotFoundException() {
        when(orderRepository.findById("o-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.get("o-missing"))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void ship_orderNotFound_throwsNotFoundException() {
        when(orderRepository.findById("o-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.ship("o-missing"))
            .isInstanceOf(NotFoundException.class);
    }

    // --- helpers below ---
}
