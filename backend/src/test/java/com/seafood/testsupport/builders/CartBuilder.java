package com.seafood.testsupport.builders;

import com.seafood.order.domain.Cart;
import com.seafood.order.domain.CartItem;

import java.time.Instant;
import java.util.List;

/**
 * CartBuilder — D1 5 builder 之一。
 *
 * <p>核心字段:userId / items。updatedAt 默认 now,需要时 withUpdatedAt()。
 * 注:Cart 是 immutable 集合(record),build() 用 CartItem 列表,不在 builder 内
 * 累积添加 — 用 withItems(List.of(...)) 一次性传入。
 */
public final class CartBuilder {

    private static final Instant DEFAULT_T = Instant.parse("2026-06-01T00:00:00Z");

    private String userId = "u-test";
    private List<CartItem> items = List.of();
    private Instant updatedAt = DEFAULT_T;

    private CartBuilder() {}

    public static CartBuilder aCart() {
        return new CartBuilder();
    }

    public CartBuilder withUserId(String userId) { this.userId = userId; return this; }
    public CartBuilder withItems(List<CartItem> items) { this.items = items; return this; }
    public CartBuilder withUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

    public Cart build() {
        return new Cart(userId, items, updatedAt);
    }
}
