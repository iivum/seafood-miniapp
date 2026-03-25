package com.seafood.order.interfaces.rest;

import java.util.List;
import java.util.ArrayList;

/**
 * Response DTO for shopping cart
 * Represents the complete shopping cart with all items
 */
public class CartResponse {

    private String id;
    private String userId;
    private List<CartItemResponse> items;
    private double totalPrice;
    private int totalItems;
    private List<String> selectedItems;

    public CartResponse() {
        this.items = new ArrayList<>();
        this.selectedItems = new ArrayList<>();
    }

    public CartResponse(String id, String userId, List<CartItemResponse> items, double totalPrice, int totalItems, List<String> selectedItems) {
        this.id = id;
        this.userId = userId;
        this.items = items != null ? items : new ArrayList<>();
        this.totalPrice = totalPrice;
        this.totalItems = totalItems;
        this.selectedItems = selectedItems != null ? selectedItems : new ArrayList<>();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<CartItemResponse> getItems() {
        return items;
    }

    public void setItems(List<CartItemResponse> items) {
        this.items = items;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
    }

    public List<String> getSelectedItems() {
        return selectedItems;
    }

    public void setSelectedItems(List<String> selectedItems) {
        this.selectedItems = selectedItems;
    }

    public void addItem(CartItemResponse item) {
        this.items.add(item);
    }

    @Override
    public String toString() {
        return "CartResponse{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", items=" + items +
                ", totalPrice=" + totalPrice +
                ", totalItems=" + totalItems +
                ", selectedItems=" + selectedItems +
                '}';
    }
}