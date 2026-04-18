package com.seafood.user.infrastructure.wechat;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.config.WxMaConfig;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for WeChat Mini Program Java SDK (WxJava).
 * Sets up the required beans for interacting with WeChat APIs.
 *
 * <p>The configuration reads WeChat app credentials from application
 * properties and creates the shared services needed for WeChat
 * authentication operations.</p>
 *
 * @see WxMaService
 * @see <a href="https://github.com/Wechat-Group/WxJava">WxJava SDK</a>
 */
@Configuration
public class WxJavaConfig {

    /** WeChat Mini Program AppID from configuration */
    @Value("${wx.appid}")
    private String appId;

    /** WeChat Mini Program AppSecret from configuration */
    @Value("${wx.secret}")
    private String appSecret;

    /**
     * Creates the WeChat Mini Program configuration bean.
     * The config stores the app credentials needed for API calls.
     *
     * @return the WeChat configuration with app credentials
     */
    @Bean
    public WxMaConfig wxMaConfig() {
        WxMaDefaultConfigImpl config = new WxMaDefaultConfigImpl();
        config.setAppid(appId);
        config.setSecret(appSecret);
        return config;
    }

    /**
     * Creates the WeChat Mini Program service bean.
     * This is the main entry point for all WeChat API operations.
     *
     * @param wxMaConfig the WeChat configuration to use
     * @return the WeChat service instance
     */
    @Bean
    public WxMaService wxMaService(WxMaConfig wxMaConfig) {
        WxMaService service = new WxMaServiceImpl();
        service.setWxMaConfig(wxMaConfig);
        return service;
    }
}
