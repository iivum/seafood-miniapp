package com.seafood.order.api;

import com.seafood.order.api.dto.CartItemRequest;
import com.seafood.order.api.dto.CartResponse;
import com.seafood.order.application.CartService;
import com.seafood.shared.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 购物车 API — userId 始终从 SecurityContext 取,绝不接受 query/path 参数(防越权)。
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService carts;

    public CartController(CartService carts) {
        this.carts = carts;
    }

    @GetMapping
    public CartResponse get(@AuthenticationPrincipal UserPrincipal me) {
        return carts.get(me.getId());
    }

    @PostMapping("/items")
    public CartResponse addItem(@AuthenticationPrincipal UserPrincipal me,
                                @Valid @RequestBody CartItemRequest req) {
        return carts.addItem(me.getId(), req);
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(@AuthenticationPrincipal UserPrincipal me,
                                   @org.springframework.web.bind.annotation.PathVariable String productId) {
        return carts.removeItem(me.getId(), productId);
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(@AuthenticationPrincipal UserPrincipal me) {
        carts.clear(me.getId());
        return ResponseEntity.noContent().build();
    }
}
