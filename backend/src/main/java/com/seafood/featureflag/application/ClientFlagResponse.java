package com.seafood.featureflag.application;

/**
 * 小程序公共端点返回的轻量 flag DTO（只含 flagKey + enabled，不暴露内部配置细节）。
 */
public record ClientFlagResponse(String flagKey, boolean enabled) {}
