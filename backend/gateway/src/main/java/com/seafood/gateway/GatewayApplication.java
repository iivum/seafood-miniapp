package com.seafood.gateway;

import com.seafood.common.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(JwtProperties.class)
@ComponentScan({"com.seafood.gateway", "com.seafood.common", "com.seafood.gateway.aggregation"})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Bean
    public GatewayFilter authenticationFilterWrapper(AuthenticationFilter authenticationFilter) {
        // Wrap the gateway filter factory result as a bean
        return authenticationFilter.apply(new AuthenticationFilter.Config());
    }
}
