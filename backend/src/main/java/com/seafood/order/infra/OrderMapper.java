package com.seafood.order.infra;

import com.seafood.order.domain.Order;
import com.seafood.order.domain.OrderStatus;


public final class OrderMapper {

    private OrderMapper() {}

    public static Order toDomain(OrderDocument d) {
        if (d == null) return null;
        return new Order(
                d.getId(),
                d.getUserId(),
                d.getItems(),
                d.getTotalAmount(),
                OrderStatus.of(d.getStatus()),
                d.getCancelReason(),
                d.getTracking(),
                d.getRefundId(),
                null, // estimatedDelivery: OrderDocument 未显式声明字段,Spring Data Mongo 写入/读出均不带,这里显式传 null
                d.getCreatedAt(),
                d.getUpdatedAt());
    }

    public static OrderDocument toDocument(Order o) {
        OrderDocument d = new OrderDocument();
        d.setId(o.id());
        d.setUserId(o.userId());
        d.setItems(o.items());
        d.setTotalAmount(o.totalAmount());
        d.setStatus(o.status().code());
        d.setCancelReason(o.cancelReason());
        d.setTracking(o.tracking());
        d.setRefundId(o.refundId());
        d.setCreatedAt(o.createdAt());
        d.setUpdatedAt(o.updatedAt());
        return d;
    }
}
