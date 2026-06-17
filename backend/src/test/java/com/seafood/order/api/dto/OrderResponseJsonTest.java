package com.seafood.order.api.dto;

import com.seafood.order.domain.Order;
import com.seafood.order.domain.OrderItem;
import com.seafood.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 路线图 4.20:OrderResponse 序列化往返测试。
 *
 * <p>本测试在 estimatedDelivery 字段**未加**时是 RED,作为 mp-09 order detail 实现的
 * TDD 第 1 步(feature missing)。Task 2 加 estimatedDelivery 字段 + withEstimatedDelivery
 * 命名方法后,本测试变 GREEN。
 */
class OrderResponseJsonTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final OrderItem item = new OrderItem("p1", "三文鱼", new BigDecimal("99.00"), 2);
    private final Instant t0 = Instant.parse("2026-06-18T10:00:00Z");

    @Test
    void estimatedDeliveryRoundtrip() {
        var o = new Order(
            "o1", "u1", List.of(item), new BigDecimal("198.00"),
            new OrderStatus.Pending(), null, null, null, null, t0, t0);
        var eta = Instant.parse("2026-06-19T10:00:00Z");
        var withEstimate = o.withEstimatedDelivery(eta);
        var resp = OrderResponse.from(withEstimate);

        assertThat(resp.estimatedDelivery()).isEqualTo(eta);

        // JSON 序列化往返不应丢失字段
        String json = objectMapper.writeValueAsString(resp);
        var back = objectMapper.readValue(json, OrderResponse.class);
        assertThat(back.estimatedDelivery()).isEqualTo(eta);
    }
}
