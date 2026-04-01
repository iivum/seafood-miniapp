package com.seafood.order.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Request DTO for creating order from cart
 * Validates input data for order creation
 */
public class CreateOrderRequest {

    @NotBlank(message = "User ID cannot be blank")
    @NotNull(message = "User ID cannot be null")
    private String userId;

    @NotBlank(message = "Cart ID cannot be blank")
    @NotNull(message = "Cart ID cannot be null")
    private String cartId;

    @NotBlank(message = "Address ID cannot be blank")
    @NotNull(message = "Address ID cannot be null")
    private String addressId;

    private List<String> selectedItemIds;

    public CreateOrderRequest() {
    }

    public CreateOrderRequest(String userId, String cartId, String addressId, List<String> selectedItemIds) {
        this.userId = userId;
        this.cartId = cartId;
        this.addressId = addressId;
        this.selectedItemIds = selectedItemIds;
    }

    // Getters and Setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCartId() {
        return cartId;
    }

    public void setCartId(String cartId) {
        this.cartId = cartId;
    }

    public String getAddressId() {
        return addressId;
    }

    public void setAddressId(String addressId) {
        this.addressId = addressId;
    }

    public List<String> getSelectedItemIds() {
        return selectedItemIds;
    }

    public void setSelectedItemIds(List<String> selectedItemIds) {
        this.selectedItemIds = selectedItemIds;
    }

    @Override
    public String toString() {
        return "CreateOrderRequest{" +
                "userId='" + userId + '\'' +
                ", cartId='" + cartId + '\'' +
                ", addressId='" + addressId + '\'' +
                ", selectedItemIds=" + selectedItemIds +
                '}';
    }
}