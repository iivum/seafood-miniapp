package com.seafood.admin.client;

import lombok.Data;

@Data
public class ShipOrderRequest {
    private String carrierCode;
    private String carrierName;
    private String trackingNumber;

    public ShipOrderRequest() {}

    public ShipOrderRequest(String carrierCode, String carrierName, String trackingNumber) {
        this.carrierCode = carrierCode;
        this.carrierName = carrierName;
        this.trackingNumber = trackingNumber;
    }
}
