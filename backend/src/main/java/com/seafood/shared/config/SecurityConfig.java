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
        // 三个自定义 filter 的链内位置由下面三个 addFilterBefore/After 显式控制。
        // PR review I1:删除原"SecurityHeadersFilter 用 @Order(HIGHEST_PRECEDENCE+100)"
        // 的注释 — SecurityHeadersFilter 已无 @Order 注解(Push-sweep #25),此处再说就矛盾。
        // 唯一权威定义就是 addFilterBefore/After 这一段。
        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // fix-error-contract-denyall:未被 GlobalExceptionHandler 兜底捕获的异常
                // （filter 层异常、404 无 handler 匹配等）会内部转发到 /error；该路径本身
                // 不含业务数据，只做错误渲染，放行不构成安全问题——放行前它会被
                // anyRequest().denyAll() 拦成语义不相关的 403 空 body。
                .requestMatchers("/error").permitAll()
                // 公共读
                .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                // feature flag 公共端点（小程序匿名读，只含 flagKey + enabled）
                .requestMatchers(HttpMethod.GET, "/api/featureflags").permitAll()
                // banner:admin 全量列表必须排在公共 GET 之前(first-match 生效)
                .requestMatchers(HttpMethod.GET, "/api/banners/all").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/banners", "/api/banners/*").permitAll()
                // 鉴权端点
                .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/wechat-login").permitAll()
                .requestMatchers("/api/admin/auth/login", "/api/admin/auth/refresh", "/api/admin/auth/cookie-login", "/api/admin/auth/csrf").permitAll()
                // 5.14-5.17 上传图片公开读(URL 写在商品/订单详情里,前端匿名访问)
                .requestMatchers("/api/static/uploads/**").permitAll()
                // 静态资源(管理后台 SPA)
                // 注意:4 条 actuator 路径<em>显式</em>列白名单(不用 /actuator/**
                // 通配符)—— 安全姿态"已知白名单" > "全通配";未来 yml 加
                // 新 actuator 端点(例如 /actuator/env)若想免 JWT,需在
                // SecurityConfig 显式 permit(白名单收缩),否则 anyRequest
                // .denyAll() 兜底返 403(原 PR #2 known limitation 2.7.5)。
                //
                // OpenSpec setup-observability-stack PR #2 / design §D2:`/actuator/prometheus`
                // permitAll — management port 9090 是 cluster-internal 隔离(design §D2),
                // 物理上不暴露到外网,Prometheus scrape 用 k8s sidecar / cluster-internal
                // service 访问,无需 JWT。
                //
                // 设计意图:8080 上 /actuator/{health,info,prometheus} 返 404
                // (management context 与 main context 分离,8080 context 没
                // 注册 actuator handler → NoHandlerFoundException → 404),
                // 不是 403。`permitAll` 让 Security 不挡,交给 Spring 路由
                // 层报 404。MetricsEndpointIT .prometheusEndpointAbsentFromBusinessPort
                // + businessPortHasNoActuatorRoutes 期望 404 守此契约。
                .requestMatchers("/admin/**", "/assets/**", "/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                // 写商品 = ADMIN
                .requestMatchers(HttpMethod.POST, "/api/products", "/api/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")
                // 写 banner = ADMIN(读已在上面公共放行 / all 已 ADMIN)
                .requestMatchers(HttpMethod.POST, "/api/banners", "/api/banners/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/banners/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/banners/**").hasRole("ADMIN")
                // 管理后台聚合 = ADMIN
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // 购物车/订单:粗粒度放行(CUSTOMER/ADMIN),细粒度在方法 @PreAuthorize
                .requestMatchers("/api/cart/**", "/api/orders/**").authenticated()
                .requestMatchers("/api/users/**").authenticated()
                // 地址 self-scoped 门面(身份取自 JWT principal,细粒度在 @PreAuthorize)
                .requestMatchers("/api/addresses/**").authenticated()
                // 收藏 / 浏览足迹 self-scoped 门面(同 /api/addresses 惯例——task-4 全分支
                // review 发现:FavoriteController/ProductViewController 新增时漏配了这两条,
                // 两边方法级 @PreAuthorize("isAuthenticated()") 从未真正生效,anyRequest()
                // .denyAll() 兜底先一步把请求全部拒了(403)——同 2026-06-21 /api/addresses
                // 那次一模一样的漏配类型,SecurityFilterChainOrderIT 已加对应回归测试)
                .requestMatchers("/api/favorites/**", "/api/product-views/**").authenticated()
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
