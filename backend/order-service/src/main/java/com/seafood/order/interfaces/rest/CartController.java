package com.seafood.order.interfaces.rest;

import com.seafood.order.application.CartApplicationService;
import com.seafood.order.domain.model.Cart;
import com.seafood.order.domain.model.CartItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Cart REST API controller
 * Provides endpoints for shopping cart operations
 * Follows RESTful principles and handles request/response mapping
 */
@RestController
@RequestMapping("/cart")
public class CartController {

    private final CartApplicationService cartApplicationService;

    @Autowired
    public CartController(CartApplicationService cartApplicationService) {
        this.cartApplicationService = cartApplicationService;
    }

    /**
     * GET /api/cart - Get user's cart
     *
     * @param userId the user ID (from authentication)
     * @return CartResponse with cart details
     */
    @GetMapping
    public ResponseEntity<CartResponse> getCart(@RequestAttribute("userId") String userId) {
        try {
            Cart cart = cartApplicationService.getOrCreateCart(userId);
            CartResponse response = convertToResponse(cart);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * POST /api/cart - Add item to cart
     *
     * @param userId the user ID
     * @param request the add to cart request
     * @return CartResponse with updated cart
     */
    @PostMapping
    public ResponseEntity<CartResponse> addToCart(
            @RequestAttribute("userId") String userId,
            @Valid @RequestBody AddToCartRequest request) {
        try {
            Cart cart = cartApplicationService.addItemToCart(
                    userId,
                    request.getProductId(),
                    request.getQuantity(),
                    request.getName(),
                    request.getPrice(),
                    request.getImageUrl()
            );
            CartResponse response = convertToResponse(cart);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * PUT /api/cart/items - Update cart item quantity
     *
     * @param userId the user ID
     * @param request the update request
     * @return CartResponse with updated cart
     */
    @PutMapping("/items")
    public ResponseEntity<CartResponse> updateCartItem(
            @RequestAttribute("userId") String userId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        try {
            Cart cart = cartApplicationService.updateItemQuantity(
                    userId,
                    request.getItemId(),
                    request.getQuantity()
            );
            CartResponse response = convertToResponse(cart);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * DELETE /api/cart/items/{productId} - Remove item from cart
     *
     * @param userId the user ID
     * @param productId the product ID to remove
     * @return CartResponse with updated cart
     */
    @DeleteMapping("/items/{productId}")
    public ResponseEntity<CartResponse> removeItemFromCart(
            @RequestAttribute("userId") String userId,
            @PathVariable @NotBlank String productId) {
        try {
            Cart cart = cartApplicationService.removeItemFromCart(userId, productId);
            CartResponse response = convertToResponse(cart);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * DELETE /api/cart - Clear all items from cart
     *
     * @param userId the user ID
     * @return CartResponse with empty cart
     */
    @DeleteMapping
    public ResponseEntity<CartResponse> clearCart(@RequestAttribute("userId") String userId) {
        try {
            Cart cart = cartApplicationService.clearCart(userId);
            CartResponse response = convertToResponse(cart);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * PATCH /api/cart/items/{productId}/toggle-selection - Toggle item selection
     *
     * @param userId the user ID
     * @param productId the product ID to toggle selection
     * @return CartResponse with updated cart
     */
    @PatchMapping("/items/{productId}/toggle-selection")
    public ResponseEntity<CartResponse> toggleItemSelection(
            @RequestAttribute("userId") String userId,
            @PathVariable @NotBlank String productId) {
        try {
            Cart cart = cartApplicationService.toggleItemSelection(userId, productId);
            CartResponse response = convertToResponse(cart);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * POST /api/cart/select-all - Select all items in cart
     *
     * @param userId the user ID
     * @return CartResponse with updated cart
     */
    @PostMapping("/select-all")
    public ResponseEntity<CartResponse> selectAllItems(@RequestAttribute("userId") String userId) {
        try {
            Cart cart = cartApplicationService.selectAllItems(userId);
            CartResponse response = convertToResponse(cart);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * POST /api/cart/deselect-all - Deselect all items in cart
     *
     * @param userId the user ID
     * @return CartResponse with updated cart
     */
    @PostMapping("/deselect-all")
    public ResponseEntity<CartResponse> deselectAllItems(@RequestAttribute("userId") String userId) {
        try {
            Cart cart = cartApplicationService.deselectAllItems(userId);
            CartResponse response = convertToResponse(cart);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Utility method to convert Cart domain object to CartResponse DTO
     *
     * @param cart the cart to convert
     * @return the cart response
     */
    public static CartResponse convertToResponse(Cart cart) {
        if (cart == null) {
            return null;
        }

        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(item -> convertToResponse(item, cart.getSelectedItems().contains(item.getProductId())))
                .collect(Collectors.toList());

        return new CartResponse(
                cart.getId(),
                cart.getUserId(),
                itemResponses,
                cart.getTotalPrice(),
                cart.getTotalItems(),
                new ArrayList<>(cart.getSelectedItems())
        );
    }

    /**
     * Utility method to convert CartItem domain object to CartItemResponse DTO
     *
     * @param item the cart item to convert
     * @param selected whether the item is selected
     * @return the cart item response
     */
    public static CartItemResponse convertToResponse(CartItem item, boolean selected) {
        if (item == null) {
            return null;
        }

        return new CartItemResponse(
                item.getId(),
                item.getProductId(),
                item.getName(),
                item.getPrice(),
                item.getQuantity(),
                item.getImageUrl(),
                selected
        );
    }
}