package com.seafood.order.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "创建订单请求")
public class CreateOrderRequest {
    
    @Schema(description = "用户ID", example = "user123")
    private String userId;
    
    @Schema(description = "订单商品列表")
    private List<OrderItemRequest> items;
    
    @Schema(description = "订单总金额", example = "199.99")
    private BigDecimal totalAmount;
    
    @Schema(description = "收货地址")
    private String shippingAddress;
    
    @Schema(description = "地址ID（关联地址表）")
    private String addressId;
    
    @Schema(description = "备注信息")
    private String remark;
    
    @Data
    @Schema(description = "订单商品项")
    public static class OrderItemRequest {
        
        @Schema(description = "商品ID", example = "product123")
        private String productId;
        
        @Schema(description = "商品名称", example = "新鲜三文鱼")
        private String productName;
        
        @Schema(description = "商品价格", example = "99.99")
        private BigDecimal price;
        
        @Schema(description = "商品数量", example = "2")
        private Integer quantity;
        
        @Schema(description = "商品图片URL")
        private String imageUrl;
    }
}