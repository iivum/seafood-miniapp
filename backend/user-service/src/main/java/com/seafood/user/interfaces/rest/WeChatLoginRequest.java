package com.seafood.user.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "微信登录请求")
public class WeChatLoginRequest {
    
    @Schema(description = "微信OpenID（直接登录时使用）", example = "oV6Dt123456789")
    private String openId;
    
    @Schema(description = "微信登录凭证code（需后端兑换openId）", example = "071XXXXXXXX")
    private String code;
    
    @Schema(description = "微信昵称", example = "微信昵称")
    private String nickname;
    
    @Schema(description = "微信头像URL", example = "https://example.com/avatar.jpg")
    private String avatarUrl;
    
    @Schema(description = "手机号", example = "13800138000")
    private String phone;
}