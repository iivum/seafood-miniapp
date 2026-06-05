package com.seafood.shared.config;

import com.seafood.shared.security.AdminRateLimitFilter;
import com.seafood.shared.security.AdminRateLimitProperties;
import com.seafood.shared.security.JwtAuthenticationFilter;
import com.seafood.shared.security.JwtProperties;
import com.seafood.shared.security.SecurityHeadersFilter;
import com.seafood.shared.security.SecurityHeadersProperties;
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
 * <p>Sprint 2 §2.8 在原有 filter 链基础上插入:
 * <ul>
 *   <li>{@link SecurityHeadersFilter} — 最早,先写响应头</li>
 *   <li>{@link AdminRateLimitFilter} — 在 JwtAuthenticationFilter 之后,这样限流能拿到
 *       {@code authentication.principal} 拼出 bucket key</li>
 * </ul>
 *
 * <p>本类只做 URL 级粗粒度放行/拒绝,细粒度(CUSTOMER 只能动自己)在 Controller 方法上用
 * {@code @PreAuthorize("hasRole('CUSTOMER') and #userId == authentication.principal.id")}。
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({
        JwtProperties.class,
        SecurityHeadersProperties.class,
        AdminRateLimitProperties.class
})
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtAuthenticationFilter jwtFilter,
                                            SecurityHeadersFilter headersFilter,
                                            AdminRateLimitFilter rateLimitFilter) throws Exception {
        // SecurityHeadersFilter 自身用 @Order(Ordered.HIGHEST_PRECEDENCE + 100) 标注;
        // 这里再显式加一次确保 order 在所有自定义 filter 中最早,即便后续 Spring Security
        // 改默认 order 也不影响(specs/runtime-security 行为)。
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
            // 1) SecurityHeadersFilter 最早:任何错误/正常响应都带安全头
            .addFilterBefore(headersFilter, org.springframework.security.web.context.SecurityContextHolderFilter.class)
            // 2) JwtAuthenticationFilter 在 SecurityContextHolderFilter 之后设置 SecurityContext
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            // 3) AdminRateLimitFilter 在鉴权之后:能拿到 account 拼 key;但应在 Controller 之前
            .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);
        return http.build();
    }
}
