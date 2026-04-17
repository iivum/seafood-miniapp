package com.seafood.admin.client;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OrderHistoryResponse {
    private String id;
    private String orderId;
    private String status;
    private String description;
    private LocalDateTime timestamp;
}
