package com.seafood.order.interfaces.rest;

/**
 * Response DTO for cart price calculation
 */
public class CartPriceResponse {

    private double subtotal;
    private double shippingFee;
    private double total;
    private int totalItems;

    public CartPriceResponse() {
    }

    public CartPriceResponse(double subtotal, double shippingFee, double total, int totalItems) {
        this.subtotal = subtotal;
        this.shippingFee = shippingFee;
        this.total = total;
        this.totalItems = totalItems;
    }

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
