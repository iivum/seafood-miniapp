package com.seafood.user.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request payload for WeChat phone number authorization login.
 * The phone number is obtained through WeChat's button component
 * which returns encrypted data that must be decrypted server-side.
 *
 * <p>The typical flow:
 * <ol>
 *   <li>Front-end calls wx.login() to get a code</li>
 *   <li>User taps the phone number button and grants permission</li>
 *   <li>Front-end receives encrypted data and iv via the button callback</li>
 *   <li>Back-end exchanges code for session key, then decrypts phone number</li>
 * </ol>
 * </p>
 *
 * @see WeChatLoginRequest
 */
@Data
@Schema(description = "微信手机号登录请求")
public class WeChatPhoneLoginRequest {

    @Schema(description = "微信登录code（通过wx.login获取）", required = true)
    @NotBlank(message = "code不能为空")
    private String code;

    @Schema(description = "加密手机号数据（通过button组件获取）", required = true)
    @NotBlank(message = "encryptedData不能为空")
    private String encryptedData;

    @Schema(description = "解密偏移量（通过button组件获取）", required = true)
    @NotBlank(message = "iv不能为空")
    private String iv;
}
