package com.seafood.admin.client;

import lombok.Data;

@Data
public class OrderHistoryResponse {
    private String id;
    private String orderId;
    private String status;
    private String description;
    private String timestamp;
}
