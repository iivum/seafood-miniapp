package com.seafood.shared.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 微信小程序配置(Sprint 2 §1.3)。
 *
 * <p>开发期 {@code wechat.enabled=false},appid/secret 可缺,登录 code 必须以 {@code dev-}
 * 开头(参见 {@code WechatCodeExchanger})。
 * 一旦 {@code enabled=true} 切到真实微信接口,必须同时提供 appid + secret;启动期由
 * {@link #isCredentialsPresentWhenEnabled()} 的 {@code @AssertTrue} 保证 fail-fast。
 */
@ConfigurationProperties(prefix = "wechat")
@Validated
public class WechatProperties {

    /** 默认 false:开发期接受 dev- code,生产部署需显式打开。 */
    private boolean enabled = false;
    private String appid;
    private String secret;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getAppid() { return appid; }
    public void setAppid(String appid) { this.appid = appid; }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    /**
     * 跨字段校验:启用后必须同时有 appid + secret(任一缺失即拒绝启动)。
     */
    @AssertTrue(message = "wechat.enabled=true requires non-blank wechat.appid and wechat.secret")
    public boolean isCredentialsPresentWhenEnabled() {
        if (!enabled) {
            return true;
        }
        return appid != null && !appid.isBlank() && secret != null && !secret.isBlank();
    }
}
