package com.seafood.user.application;

import com.seafood.shared.error.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 微信 code → openId 兑换(对齐 specs/mini-program §Authentication and session)。
 *
 * <p>真实实现:调用 https://api.weixin.qq.com/sns/jscode2session。
 * 这里默认实现:开发期接受 "dev-{any}" 形式,生产期通过 wechat.enabled=true 走真接口。
 */
@Component
public class WechatCodeExchanger {

    private final RestClient http = RestClient.builder()
            .baseUrl("https://api.weixin.qq.com")
            .build();

    @Value("${wechat.appid:dummy}")
    private String appid;
    @Value("${wechat.secret:dummy}")
    private String secret;
    @Value("${wechat.enabled:false}")
    private boolean enabled;

    public String exchange(String code) {
        if (!enabled) {
            // 开发期:接受 dev-* 直接当 openId
            if (code != null && code.startsWith("dev-")) {
                return code;
            }
            // 显式 fail-fast,避免上线时静默放行
            throw new DomainException("微信登录未启用(wechat.enabled=false),code 必须以 dev- 开头");
        }
        Map<?,?> body = http.get()
                .uri(uri -> uri.path("/sns/jscode2session")
                        .queryParam("appid", appid)
                        .queryParam("secret", secret)
                        .queryParam("js_code", code)
                        .queryParam("grant_type", "authorization_code")
                        .build())
                .retrieve()
                .body(Map.class);
        if (body == null || body.get("openid") == null) {
            throw new DomainException("微信 code 兑换失败:" + (body == null ? "empty" : body.get("errmsg")));
        }
        return (String) body.get("openid");
    }
}
