package com.seafood.user.application;

import com.seafood.shared.error.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

/**
 * 微信手机号绑定 code → 手机号(对齐 specs/backend-api §Customer phone number binding)。
 *
 * <p>真实实现:先用 {@code cgi-bin/token} 换 access_token(内存缓存,避免撞微信限流),
 * 再调 {@code wxa/business/getuserphonenumber} 用 code 换手机号。
 * 与 {@link WechatCodeExchanger} 一致的开发期约定:{@code wechat.enabled=false} 时
 * 接受 {@code dev-*} 前缀 code,派生一个确定性测试手机号,不发起真实请求。
 */
@Component
public class WechatPhoneNumberExchanger {

    private final RestClient http;

    @Value("${wechat.appid:dummy}")
    private String appid;
    @Value("${wechat.secret:dummy}")
    private String secret;
    @Value("${wechat.enabled:false}")
    private boolean enabled;
    @Value("${wechat.phone-binding.enabled:false}")
    private boolean phoneBindingEnabled;

    private volatile String cachedAccessToken;
    private volatile Instant accessTokenExpiresAt = Instant.MIN;

    public WechatPhoneNumberExchanger() {
        this(RestClient.builder().baseUrl("https://api.weixin.qq.com").build());
    }

    // 测试专用:注入已绑定 MockRestServiceServer 的 RestClient(不经 Spring 容器,
    // 因为 RestClient.Builder 在本项目里不是自动装配 bean —— 同 WechatCodeExchanger
    // 的既有约定:自己 new,不依赖框架提供的 RestClient.Builder)。
    WechatPhoneNumberExchanger(RestClient http) {
        this.http = http;
    }

    public String exchange(String code) {
        if (!enabled) {
            if (code != null && code.startsWith("dev-")) {
                return devPhoneFor(code);
            }
            throw new DomainException("微信手机号绑定未启用(wechat.enabled=false),code 必须以 dev- 开头");
        }
        if (!phoneBindingEnabled) {
            throw new DomainException("手机号绑定功能暂未开放(wechat.phone-binding.enabled=false)");
        }
        String accessToken = accessToken();
        Map<?, ?> body = http.post()
                .uri(uri -> uri.path("/wxa/business/getuserphonenumber")
                        .queryParam("access_token", accessToken)
                        .build())
                .body(Map.of("code", code))
                .retrieve()
                .body(Map.class);
        Object phoneInfoObj = body == null ? null : body.get("phone_info");
        if (!(phoneInfoObj instanceof Map<?, ?> phoneInfo) || phoneInfo.get("phoneNumber") == null) {
            throw new DomainException("微信手机号换取失败:" + (body == null ? "empty" : body.get("errmsg")));
        }
        return (String) phoneInfo.get("phoneNumber");
    }

    private synchronized String accessToken() {
        Instant now = Instant.now();
        if (cachedAccessToken != null && now.isBefore(accessTokenExpiresAt)) {
            return cachedAccessToken;
        }
        Map<?, ?> body = http.get()
                .uri(uri -> uri.path("/cgi-bin/token")
                        .queryParam("grant_type", "client_credential")
                        .queryParam("appid", appid)
                        .queryParam("secret", secret)
                        .build())
                .retrieve()
                .body(Map.class);
        if (body == null || body.get("access_token") == null) {
            throw new DomainException("微信 access_token 获取失败:" + (body == null ? "empty" : body.get("errmsg")));
        }
        cachedAccessToken = (String) body.get("access_token");
        int expiresIn = body.get("expires_in") instanceof Number n ? n.intValue() : 7200;
        // 提前 60s 过期,避免请求发出瞬间恰好撞上失效边界
        accessTokenExpiresAt = now.plusSeconds(Math.max(expiresIn - 60, 0));
        return cachedAccessToken;
    }

    private static String devPhoneFor(String code) {
        int hash = Math.abs(code.hashCode()) % 100000000;
        return String.format("138%08d", hash);
    }
}
