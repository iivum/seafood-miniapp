package com.seafood.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;

/**
 * 管理后台 SPA 静态资源路由(参见 specs/backend-api §Static admin asset hosting,
 * design §"静态资源服务(托管 admin-ui 构建产物)")。
 *
 * <p>行为:
 * <ul>
 *   <li>GET /admin/ → /admin/index.html</li>
 *   <li>GET /admin/&lt;existing-asset&gt; → 静态文件(JS/CSS/img)</li>
 *   <li>GET /admin/&lt;unknown-route&gt; → /admin/index.html(SPA history-API fallback)</li>
 *   <li>GET /admin/missing-asset.(js|css|...) → 404(不 fallback,assets 必须存在)</li>
 * </ul>
 *
 * <p>资源全部托管自 {@code classpath:/static/admin/**}(admin-ui 构建产物)。
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private static final String ADMIN_BASE = "/admin/**";
    private static final String ADMIN_LOCATION = "classpath:/static/admin/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 标准静态资源:实际文件存在时直接返
        registry.addResourceHandler(ADMIN_BASE)
                .addResourceLocations(ADMIN_LOCATION)
                .setCachePeriod(3600);
    }

    /**
     * SPA history-API fallback:任何 GET /admin/{非 .css/.js/.png/... 资产} 都返 index.html。
     * 资产请求(扩展名)走 addResourceHandlers;命中失败就 404,不 fallback。
     */
    @Bean
    public RouterFunction<ServerResponse> adminSpaFallback() {
        return RouterFunctions.route()
                .GET("/admin", req -> serveIndex())
                .GET("/admin/", req -> serveIndex())
                .GET("/admin/{path:^(?!.*\\.[a-zA-Z0-9]+$).*$}", req -> {
                    String subPath = req.pathVariable("path");
                    // 已存在资源(由 addResourceHandlers 处理)不被本规则覆盖;
                    // 这里只服务"目录式"或"无扩展名"的 SPA 路由
                    Resource candidate = new ClassPathResource("static/admin/" + subPath);
                    if (candidate.exists()) {
                        return ServerResponse.ok().body(candidate);
                    }
                    return serveIndex();
                })
                .build();
    }

    private static ServerResponse serveIndex() {
        Resource index = new ClassPathResource("static/admin/index.html");
        if (!index.exists()) {
            return ServerResponse.notFound().build();
        }
        return ServerResponse.ok()
                .header("Content-Type", "text/html;charset=UTF-8")
                .cacheControl(org.springframework.http.CacheControl.noCache())
                .body(index);
    }

    /** 测试用,公开基础路径常量。 */
    public static List<String> adminPaths() {
        return List.of("/admin", "/admin/", "/admin/dashboard", "/admin/orders/123");
    }
}
