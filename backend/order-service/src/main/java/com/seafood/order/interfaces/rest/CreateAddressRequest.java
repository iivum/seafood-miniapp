package com.seafood.order.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "创建地址请求")
public class CreateAddressRequest {
    
    @Schema(description = "收件人姓名", example = "张三", required = true)
    private String name;
    
    @Schema(description = "手机号", example = "13800138000", required = true)
    private String phone;
    
    @Schema(description = "省份", example = "上海市", required = true)
    private String province;
    
    @Schema(description = "城市", example = "上海市", required = true)
    private String city;
    
    @Schema(description = "区县", example = "浦东新区", required = true)
    private String district;
    
    @Schema(description = "详细地址", example = "某某路123号401室", required = true)
    private String detailAddress;
    
    @Schema(description = "是否设为默认地址", example = "false")
    private boolean isDefault = false;
}