package com.seafood.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.admin")
public record AdminBootstrap(String bootstrapUsername, String bootstrapPassword) {}
