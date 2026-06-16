package com.seafood.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import java.nio.file.Paths;
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
    // v2.1 signoff 修:vite 默认 base='/',build 产物 index.html 引用 /assets/...
    // 根路径,但实际文件在 classpath:/static/admin/assets/。修法:加 /assets/** 资源
    // 处理器,跟 SPA index.html 同源,SecurityConfig 同时 permitAll(public JS/CSS/font)。
    private static final String ASSETS_BASE = "/assets/**";
    private static final String ASSETS_LOCATION = ADMIN_LOCATION + "assets/";

    /**
     * 路线图 3.6 上传图片静态暴露路径(/api/static/uploads/**)。
     * 注意:必须有 /api 前缀,因 {@code com.seafood.shared.config.WebConfig} 把 /api/**
     * 路由交给业务 controller(本类扩展 MVC 资源处理器);不带 /api 前缀的 /static/uploads/**
     * 会与未来 /static/** 其他用途冲突。
     */
    private static final String UPLOADS_BASE = "/api/static/uploads/**";

    /**
     * v2 视觉 5.15 — 路径同源。读盘(本类)与写盘(AdminUploadController)
     * 都从 {@code seafood.upload.dir} 注入,容器内 + dev 一致。Spring
     * ResourceHandler 接受 {@code file:} 前缀定位文件系统位置,故拼一次。
     * 默认 {@code ./var/seafood/uploads} 由 application.yml 提供,容器
     * 由 SEAFOOD_UPLOAD_DIR 注入 /app/var/seafood/uploads。
     */
    private final String uploadsLocation;

    public StaticResourceConfig(@Value("${seafood.upload.dir:./var/seafood/uploads}") String uploadDir) {
        // ResourceHandler 需要以 "/" 结尾(Spring 内部字符串拼接检查),
        // 且以 "file:" 前缀标明文件系统源(非 classpath)。
        String absPath = Paths.get(uploadDir).toAbsolutePath().normalize().toString();
        this.uploadsLocation = "file:" + absPath + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 标准静态资源:实际文件存在时直接返
        registry.addResourceHandler(ADMIN_BASE)
                .addResourceLocations(ADMIN_LOCATION)
                .setCachePeriod(3600);
        // v2.1:admin-ui vite 产物的 /assets/** 根路径(JS/CSS/字体)
        registry.addResourceHandler(ASSETS_BASE)
                .addResourceLocations(ASSETS_LOCATION)
                .setCachePeriod(3600);
        // 3.6 上传文件 + 5.15 路径同源(file: 前缀指向文件系统;与 classpath 不同)
        registry.addResourceHandler(UPLOADS_BASE)
                .addResourceLocations(uploadsLocation)
                .setCachePeriod(86400); // 1 day
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
