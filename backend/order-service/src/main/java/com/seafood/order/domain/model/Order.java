package com.seafood.order.domain.model;

import com.seafood.common.domain.AggregateRoot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Document(collection = "orders")
public class Order extends AggregateRoot<String> {
    private String userId;
    private List<OrderItem> items;
    private BigDecimal totalPrice;
    private OrderStatus status;
    private String shippingAddress;

    public Order(String userId, List<OrderItem> items, String shippingAddress) {
        super();
        this.userId = userId;
        this.items = items;
        this.shippingAddress = shippingAddress;
        this.status = OrderStatus.PENDING_PAYMENT;
        this.totalPrice = calculateTotalPrice();
    }

    private BigDecimal calculateTotalPrice() {
        return items.stream()
                .map(item -> item.getPrice().multiply(new BigDecimal(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void pay() {
        if (this.status != OrderStatus.PENDING_PAYMENT) {
            throw new RuntimeException("Order cannot be paid in current status: " + this.status);
        }
        this.status = OrderStatus.PAID;
    }

    public void cancel() {
        if (this.status != OrderStatus.PENDING_PAYMENT) {
            throw new RuntimeException("Order cannot be cancelled in current status: " + this.status);
        }
        this.status = OrderStatus.CANCELLED;
    }

    public void ship() {
        if (this.status != OrderStatus.PAID) {
            throw new RuntimeException("Order must be paid before shipping");
        }
        this.status = OrderStatus.SHIPPED;
    }

    public void complete() {
        if (this.status != OrderStatus.SHIPPED) {
            throw new RuntimeException("Order must be shipped before completion");
        }
        this.status = OrderStatus.COMPLETED;
    }
}
