package com.seafood.shared.config;

import com.seafood.shared.security.JwtAuthenticationFilter;
import com.seafood.shared.security.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置(对齐 design.md §4.2-4.3,specs/auth §Filter chain ordering §Endpoint access matrix)。
 *
 * <p>本类只做 URL 级粗粒度放行/拒绝,细粒度(CUSTOMER 只能动自己)在 Controller 方法上用
 * {@code @PreAuthorize("hasRole('CUSTOMER') and #userId == authentication.principal.id")}。
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 公共读
                .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                // 鉴权端点
                .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/wechat-login").permitAll()
                .requestMatchers("/api/admin/auth/login", "/api/admin/auth/refresh").permitAll()
                // 静态资源(管理后台 SPA)
                .requestMatchers("/admin/**", "/actuator/health", "/actuator/info").permitAll()
                // 写商品 = ADMIN
                .requestMatchers(HttpMethod.POST, "/api/products", "/api/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")
                // 管理后台聚合 = ADMIN
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // 购物车/订单:粗粒度放行(CUSTOMER/ADMIN),细粒度在方法 @PreAuthorize
                .requestMatchers("/api/cart/**", "/api/orders/**").authenticated()
                .requestMatchers("/api/users/**").authenticated()
                // 兜底
                .anyRequest().denyAll()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
