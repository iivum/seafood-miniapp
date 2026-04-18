package com.seafood.order.interfaces.rest;

/**
 * Response DTO for freight calculation
 */
public class FreightResponse {

    private double shippingFee;
    private String message;

    public FreightResponse() {
    }

    public FreightResponse(double shippingFee) {
        this.shippingFee = shippingFee;
        this.message = shippingFee == 0 ? "免运费" : "运费计算完成";
    }

    public double getShippingFee() {
        return shippingFee;
    }

    public void setShippingFee(double shippingFee) {
        this.shippingFee = shippingFee;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
