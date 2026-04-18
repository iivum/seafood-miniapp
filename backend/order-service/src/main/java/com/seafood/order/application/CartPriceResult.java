package com.seafood.order.application;

/**
 * Result DTO for cart price calculation
 * Contains the price breakdown for selected items in the cart
 */
public class CartPriceResult {

    private double subtotal;
    private double shippingFee;
    private double total;
    private int totalItems;

    public CartPriceResult() {
    }

    public CartPriceResult(double subtotal, double shippingFee, double total, int totalItems) {
        this.subtotal = subtotal;
        this.shippingFee = shippingFee;
        this.total = total;
        this.totalItems = totalItems;
    }

    // Getters and Setters
    public double getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(double subtotal) {
        this.subtotal = subtotal;
    }

    public double getShippingFee() {
        return shippingFee;
    }

    public void setShippingFee(double shippingFee) {
        this.shippingFee = shippingFee;
    }

    public double getTotal() {
        return total;
    }

    public void setTotal(double total) {
        this.total = total;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
    }
}
