package com.seafood.order.interfaces.rest;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for shipping an order.
 */
public class ShipOrderRequest {

    @NotBlank(message = "Carrier code is required")
    private String carrierCode;

    @NotBlank(message = "Carrier name is required")
    private String carrierName;

    @NotBlank(message = "Tracking number is required")
    private String trackingNumber;

    public ShipOrderRequest() {
    }

    public ShipOrderRequest(String carrierCode, String carrierName, String trackingNumber) {
        this.carrierCode = carrierCode;
        this.carrierName = carrierName;
        this.trackingNumber = trackingNumber;
    }

    public String getCarrierCode() {
        return carrierCode;
    }

    public String getCarrierName() {
        return carrierName;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }
}
