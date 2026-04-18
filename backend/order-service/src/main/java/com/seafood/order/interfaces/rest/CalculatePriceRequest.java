package com.seafood.order.interfaces.rest;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Request DTO for calculating cart price
 */
public class CalculatePriceRequest {

    @NotNull(message = "Selected item IDs cannot be null")
    private List<String> selectedItemIds;

    public CalculatePriceRequest() {
    }

    public CalculatePriceRequest(List<String> selectedItemIds) {
        this.selectedItemIds = selectedItemIds;
    }

    public List<String> getSelectedItemIds() {
        return selectedItemIds;
    }

    public void setSelectedItemIds(List<String> selectedItemIds) {
        this.selectedItemIds = selectedItemIds;
    }
}
