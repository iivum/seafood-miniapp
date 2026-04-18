package com.seafood.config.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the Config Server.
 *
 * The config server's standard endpoints (//{application}/{profile}/{label}) are public
 * so that client services can read their configuration.
 *
 * Management and encryption endpoints (/api/config/**) require authentication.
 */
@Configuration
@EnableWebSecurity
public class ConfigServerSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Spring Cloud Config standard endpoints - allow for client services
                        .requestMatchers("/" , "/health").permitAll()
                        // Actuator endpoints
                        .requestMatchers("/actuator/**").permitAll()
                        // Config management API - require authentication
                        .requestMatchers("/api/config/**").authenticated()
                        // All other requests
                        .anyRequest().permitAll()
                )
                .httpBasic(basic -> {});

        return http.build();
    }
}
