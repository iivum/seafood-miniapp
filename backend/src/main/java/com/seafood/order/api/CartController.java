package com.seafood.order.api;

import com.seafood.order.api.dto.CartItemRequest;
import com.seafood.order.api.dto.CartQuantityUpdateRequest;
import com.seafood.order.api.dto.CartResponse;
import com.seafood.order.application.CartService;
import com.seafood.shared.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
                                   @PathVariable String productId) {
        return carts.removeItem(me.getId(), productId);
    }

    /**
     * 整数替换某行数量(design D2:PUT 全量替换语义,不同于 {@link #addItem} 的合并语义)。
     * 行不存在时 {@link CartService#updateQuantity} 抛 {@code NotFoundException} → 404
     * (design D1),由 {@code GlobalExceptionHandler} 统一翻译,这里不用另接 catch。
     */
    @PutMapping("/items/{productId}")
    public CartResponse updateQuantity(@AuthenticationPrincipal UserPrincipal me,
                                       @PathVariable String productId,
                                       @Valid @RequestBody CartQuantityUpdateRequest req) {
        return carts.updateQuantity(me.getId(), productId, req.quantity());
    }

    /** 翻转某行 {@code selected}。行不存在时 404(design D1,同 {@link #updateQuantity})。 */
    @PatchMapping("/items/{productId}")
    public CartResponse toggleSelected(@AuthenticationPrincipal UserPrincipal me,
                                       @PathVariable String productId) {
        return carts.toggleSelected(me.getId(), productId);
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(@AuthenticationPrincipal UserPrincipal me) {
        carts.clear(me.getId());
        return ResponseEntity.noContent().build();
    }
}
