package com.seafood.order.domain;

import com.seafood.shared.error.DomainException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 购物车聚合 — 以 userId 作为 _id 的单文档(参见 design.md §6.1)。
 *
 * <p>操作:
 * <ul>
 *   <li>{@link #addItem} — upsert 同一 productId 的行(quantity 累加)</li>
 *   <li>{@link #removeItem} — 删除某行</li>
 *   <li>{@link #clear} — 清空</li>
 *   <li>{@link #requireNonEmptySelected} — 下单前校验</li>
 * </ul>
 */
public record Cart(
        String userId,
        List<CartItem> items,
        Instant updatedAt
) {

    public Cart {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static Cart empty(String userId) {
        return new Cart(userId, List.of(), Instant.now());
    }

    public Cart addItem(String productId, int quantity) {
        if (quantity <= 0) {
            throw new DomainException("加入数量必须大于 0");
        }
        List<CartItem> next = new ArrayList<>(items);
        boolean merged = false;
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).productId().equals(productId)) {
                int q = next.get(i).quantity() + quantity;
                next.set(i, new CartItem(productId, q, true, next.get(i).addedAt()));
                merged = true;
                break;
            }
        }
        if (!merged) {
            next.add(new CartItem(productId, quantity, true, Instant.now()));
        }
        next.sort(Comparator.comparing(CartItem::addedAt));
        return new Cart(userId, next, Instant.now());
    }

    /**
     * 把指定行的数量替换成 {@code quantity}(design D2:整数替换,不是累加 —— 区别于
     * {@link #addItem} 的 upsert-merge 语义)。行不存在时抛 {@link CartItemNotFoundException}
     * (design D1:read-modify-write 操作对不存在的行返回查找失败,不像 {@link #removeItem}
     * 那样静默 no-op)。
     */
    public Cart updateQuantity(String productId, int quantity) {
        if (quantity <= 0) {
            throw new DomainException("数量必须大于 0");
        }
        int idx = indexOf(productId);
        List<CartItem> next = new ArrayList<>(items);
        CartItem existing = next.get(idx);
        next.set(idx, new CartItem(productId, quantity, existing.selected(), existing.addedAt()));
        return new Cart(userId, next, Instant.now());
    }

    /**
     * 翻转指定行的 {@code selected}。行不存在时抛 {@link CartItemNotFoundException}
     * (design D1,同 {@link #updateQuantity})。
     */
    public Cart toggleSelected(String productId) {
        int idx = indexOf(productId);
        List<CartItem> next = new ArrayList<>(items);
        CartItem existing = next.get(idx);
        next.set(idx, new CartItem(productId, existing.quantity(), !existing.selected(), existing.addedAt()));
        return new Cart(userId, next, Instant.now());
    }

    private int indexOf(String productId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).productId().equals(productId)) {
                return i;
            }
        }
        throw new CartItemNotFoundException(productId);
    }

    public Cart removeItem(String productId) {
        List<CartItem> next = items.stream()
                .filter(i -> !i.productId().equals(productId))
                .toList();
        return new Cart(userId, next, Instant.now());
    }

    public Cart clear() {
        return new Cart(userId, List.of(), Instant.now());
    }

    public Cart requireNonEmptySelected() {
        if (items.isEmpty()) {
            throw new DomainException("购物车为空");
        }
        if (items.stream().noneMatch(CartItem::selected)) {
            throw new DomainException("请至少选择一件商品");
        }
        return this;
    }
}
