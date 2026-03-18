package com.seafood.user.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "微信登录请求")
public class WeChatLoginRequest {
    
    @Schema(description = "微信OpenID", example = "oV6Dt123456789", required = true)
    private String openId;
    
    @Schema(description = "微信昵称", example = "微信昵称", required = true)
    private String nickname;
    
    @Schema(description = "微信头像URL", example = "https://example.com/avatar.jpg", required = true)
    private String avatarUrl;
    
    @Schema(description = "手机号", example = "13800138000")
    private String phone;
}