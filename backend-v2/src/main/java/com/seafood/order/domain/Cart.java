package com.seafood.order.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "carts")
public record Cart(
    @Id String userId,
    List<CartItem> items,
    Instant updatedAt
) {
    public record CartItem(String productId, int quantity, boolean selected, Instant addedAt) {}

    public static Cart empty(String userId) {
        return new Cart(userId, List.of(), Instant.now());
    }
}
