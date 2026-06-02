package com.seafood.order.api;

import com.seafood.order.api.dto.CartResponse;
import com.seafood.order.application.CartService;
import com.seafood.shared.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService service;

    public CartController(CartService service) {
        this.service = service;
    }

    @GetMapping
    public CartResponse get(@AuthenticationPrincipal UserPrincipal me) {
        return service.get(me.userId());
    }

    @PostMapping("/items")
    public CartResponse add(
        @AuthenticationPrincipal UserPrincipal me,
        @RequestBody Map<String, Object> body
    ) {
        String productId = (String) body.get("productId");
        Number qty = (Number) body.getOrDefault("quantity", 1);
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId is required");
        }
        return service.addItem(me.userId(), productId, qty.intValue());
    }

    @PutMapping("/items/{productId}")
    public CartResponse updateQty(
        @AuthenticationPrincipal UserPrincipal me,
        @PathVariable String productId,
        @RequestBody Map<String, Object> body
    ) {
        Number qty = (Number) body.get("quantity");
        if (qty == null) {
            throw new IllegalArgumentException("quantity is required");
        }
        return service.updateQuantity(me.userId(), productId, qty.intValue());
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse remove(
        @AuthenticationPrincipal UserPrincipal me,
        @PathVariable String productId
    ) {
        return service.removeItem(me.userId(), productId);
    }

    @PatchMapping("/items/{productId}/toggle")
    public CartResponse toggle(
        @AuthenticationPrincipal UserPrincipal me,
        @PathVariable String productId
    ) {
        return service.toggleSelection(me.userId(), productId);
    }
}
