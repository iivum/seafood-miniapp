package com.seafood.user.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "微信手机号登录请求")
public class WeChatPhoneLoginRequest {

    @Schema(description = "微信登录code", required = true)
    @NotBlank(message = "code不能为空")
    private String code;

    @Schema(description = "加密手机号数据", required = true)
    @NotBlank(message = "encryptedData不能为空")
    private String encryptedData;

    @Schema(description = "解密偏移量", required = true)
    @NotBlank(message = "iv不能为空")
    private String iv;
}
