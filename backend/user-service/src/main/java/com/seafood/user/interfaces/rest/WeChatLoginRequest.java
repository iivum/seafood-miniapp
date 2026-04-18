package com.seafood.user.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Request payload for WeChat-based authentication.
 * Supports both direct OpenID login and code exchange flow.
 *
 * <p>When using the code flow, the code is obtained from WeChat mini program
 * via wx.login() and must be exchanged for session information on the server.
 * The nickname and avatarUrl fields are obtained from the WeChat API
 * after successful authentication.</p>
 *
 * @see WeChatPhoneLoginRequest
 */
@Data
@Schema(description = "微信登录请求")
public class WeChatLoginRequest {

    @Schema(description = "微信OpenID（直接登录时使用，绕过code兑换）", example = "oV6Dt123456789")
    private String openId;

    @Schema(description = "微信登录凭证code（需后端兑换openId）", example = "071XXXXXXXX")
    private String code;

    @Schema(description = "微信昵称", example = "微信昵称")
    private String nickname;

    @Schema(description = "微信头像URL", example = "https://example.com/avatar.jpg")
    private String avatarUrl;

    @Schema(description = "手机号（微信获取手机号授权时使用）", example = "13800138000")
    private String phone;
}