package com.seafood.order.application;

import com.seafood.order.api.dto.CartItemRequest;
import com.seafood.order.api.dto.CartResponse;
import com.seafood.order.domain.Cart;
import com.seafood.order.infra.CartDocument;
import com.seafood.order.infra.CartRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 购物车服务(参见 specs/backend-api §Customer cart operations)。
 * 所有方法都接收 userId 参数(由 Controller 从 SecurityContext 注入),
 * 不接受 query/path 中的 userId → 防止越权。
 */
@Service
public class CartService {

    private final CartRepository carts;

    public CartService(CartRepository carts) {
        this.carts = carts;
    }

    public CartResponse get(String userId) {
        return CartResponse.from(loadOrEmpty(userId));
    }

    public CartResponse addItem(String userId, CartItemRequest req) {
        Cart current = loadOrEmpty(userId);
        Cart updated = current.addItem(req.productId(), req.quantity());
        return CartResponse.from(persist(updated));
    }

    public CartResponse removeItem(String userId, String productId) {
        Cart current = loadOrEmpty(userId);
        Cart updated = current.removeItem(productId);
        return CartResponse.from(persist(updated));
    }

    public void clear(String userId) {
        carts.deleteById(userId);
    }

    // ----- helpers -----

    private Cart loadOrEmpty(String userId) {
        return carts.findById(userId)
                .map(d -> new Cart(d.getUserId(), d.getItems(), d.getUpdatedAt()))
                .orElseGet(() -> Cart.empty(userId));
    }

    private Cart persist(Cart c) {
        CartDocument d = new CartDocument();
        d.setUserId(c.userId());
        d.setItems(c.items());
        d.setUpdatedAt(c.updatedAt() == null ? Instant.now() : c.updatedAt());
        carts.save(d);
        return c;
    }
}
