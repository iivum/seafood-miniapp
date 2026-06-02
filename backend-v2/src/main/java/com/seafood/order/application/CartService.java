package com.seafood.order.application;

import com.seafood.order.api.dto.CartResponse;
import com.seafood.order.domain.Cart;
import com.seafood.order.infra.CartMongoRepository;
import com.seafood.order.infra.ProductStockPort;
import com.seafood.product.domain.Product;
import com.seafood.shared.error.DomainException;
import com.seafood.shared.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class CartService {

    private final CartMongoRepository carts;
    private final ProductStockPort productPort;

    public CartService(CartMongoRepository carts, ProductStockPort productPort) {
        this.carts = carts;
        this.productPort = productPort;
    }

    public CartResponse get(String userId) {
        Cart cart = carts.findById(userId).orElseGet(() -> Cart.empty(userId));
        return toResponse(cart);
    }

    public CartResponse addItem(String userId, String productId, int quantity) {
        if (quantity < 1) {
            throw new DomainException(ErrorCode.VALIDATION, "quantity 必须 >= 1");
        }
        Product product = productPort.get(productId);
        if (!product.onSale()) {
            throw new DomainException(ErrorCode.VALIDATION, "商品已下架: " + product.name());
        }
        Cart cart = carts.findById(userId).orElseGet(() -> Cart.empty(userId));
        List<Cart.CartItem> items = new ArrayList<>(cart.items());
        boolean found = false;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).productId().equals(productId)) {
                int newQty = items.get(i).quantity() + quantity;
                if (newQty > product.stock()) {
                    throw new DomainException(ErrorCode.CONFLICT, "超过库存");
                }
                items.set(i, new Cart.CartItem(productId, newQty, items.get(i).selected(), items.get(i).addedAt()));
                found = true;
                break;
            }
        }
        if (!found) {
            if (quantity > product.stock()) {
                throw new DomainException(ErrorCode.CONFLICT, "超过库存");
            }
            items.add(new Cart.CartItem(productId, quantity, true, Instant.now()));
        }
        Cart saved = carts.save(new Cart(userId, List.copyOf(items), Instant.now()));
        return toResponse(saved);
    }

    public CartResponse updateQuantity(String userId, String productId, int quantity) {
        if (quantity < 1) {
            return removeItem(userId, productId);
        }
        Product product = productPort.get(productId);
        if (quantity > product.stock()) {
            throw new DomainException(ErrorCode.CONFLICT, "超过库存");
        }
        Cart cart = carts.findById(userId).orElseGet(() -> Cart.empty(userId));
        List<Cart.CartItem> items = cart.items().stream()
            .map(ci -> ci.productId().equals(productId)
                ? new Cart.CartItem(productId, quantity, ci.selected(), ci.addedAt())
                : ci)
            .toList();
        return toResponse(carts.save(new Cart(userId, items, Instant.now())));
    }

    public CartResponse removeItem(String userId, String productId) {
        Cart cart = carts.findById(userId).orElseGet(() -> Cart.empty(userId));
        List<Cart.CartItem> items = cart.items().stream()
            .filter(ci -> !ci.productId().equals(productId))
            .toList();
        return toResponse(carts.save(new Cart(userId, items, Instant.now())));
    }

    public CartResponse toggleSelection(String userId, String productId) {
        Cart cart = carts.findById(userId).orElseGet(() -> Cart.empty(userId));
        List<Cart.CartItem> items = cart.items().stream()
            .map(ci -> ci.productId().equals(productId)
                ? new Cart.CartItem(productId, ci.quantity(), !ci.selected(), ci.addedAt())
                : ci)
            .toList();
        return toResponse(carts.save(new Cart(userId, items, Instant.now())));
    }

    private CartResponse toResponse(Cart cart) {
        List<CartResponse.CartItemResponse> items = cart.items().stream()
            .map(ci -> new CartResponse.CartItemResponse(
                ci.productId(), ci.quantity(), ci.selected(), ci.addedAt()))
            .toList();
        return new CartResponse(cart.userId(), items, cart.updatedAt());
    }
}
