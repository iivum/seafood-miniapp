package com.seafood.user.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户信息响应")
public class UserInfoResponse {
    
    @Schema(description = "用户ID", example = "507f1f77bcf86cd799439011")
    private String id;
    
    @Schema(description = "微信OpenID", example = "oV6Dt123456789")
    private String openId;
    
    @Schema(description = "用户昵称", example = "用户昵称")
    private String nickname;
    
    @Schema(description = "用户头像URL", example = "https://example.com/avatar.jpg")
    private String avatarUrl;
    
    @Schema(description = "手机号", example = "13800138000")
    private String phone;
    
    @Schema(description = "用户角色", example = "USER")
    private String role;
}