package com.seafood.shared.observability;

import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenSpec setup-observability-stack PR #2 / tasks 2.2.x —
 * 验证 Micrometer Prometheus 端点(<code>/actuator/prometheus</code>)在
 * 独立 management 端口(9090 / 测试用随机端口)上,而业务端口(<code>server.port</code>
 * = 8080 / 测试用随机端口)<em>不</em>暴露 actuator 路径(由 design §D2 的端口
 * 隔离契约决定)。
 *
 * <p>端口隔离契约(spec §"Management port 9090 is physically isolated from
 * business port 8080"):
 * <ul>
 *   <li>management 端口提供 {@code /actuator/prometheus} + {@code /actuator/health}
 *       + {@code /actuator/info},供 Prometheus scrape / k8s probe</li>
 *   <li>业务端口只暴露 {@code /api/**} 业务端点;{@code /actuator/**} 全部
 *       404,避免运维数据被外网误访问</li>
 * </ul>
 *
 * <p>Spring Boot 4 移除了 {@code TestRestTemplate} ——
 * 改用 Spring Framework 7 引入的 {@link RestTestClient}(blocking 版,直接走
 * 真实 HTTP,与 WebTestClient 区分)。配置上用 {@code bindToServer()} 显式
 * 指向 {@code http://localhost:{port}},避开 MockMvc 抽象,保证 actuator filter
 * 链 / Tomcat / 路由分发都真实生效。
 *
 * <p>架构说明:用最小 {@code @SpringBootConfiguration} + {@code @EnableAutoConfiguration}
 * (不引入 SecurityConfig / Mongo / 业务模块),避开鉴权与数据库依赖,只关心 actuator
 * + 业务路由的端口分发。{@code @LocalServerPort} + {@code @LocalManagementPort} 由
 * Spring Boot Test 在 RANDOM_PORT 模式下自动注入。
 */
@Tag("native")
@SpringBootTest(
        classes = MetricsEndpointIT.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // 关键:management.server.port=0 让 Spring 启动第二个 HTTP 监听器
                // 绑到随机端口(management 端口),@LocalManagementPort 会自动捕获。
                "management.server.port=0",
                "management.server.address=127.0.0.1"
        })
class MetricsEndpointIT {

    @LocalServerPort
    private int serverPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private MongoClient mongoClient;

    private RestTestClient managementClient() {
        return RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + managementPort)
                .build();
    }

    private RestTestClient businessClient() {
        return RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + serverPort)
                .build();
    }

    // --- 2.2.3:management 端口 prometheus 端点 200 + Content-Type ---
    @Test
    void prometheusEndpointReturns200OnManagementPort() {
        managementClient().get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith("text/plain");
    }

    // --- 2.2.4:业务端口 /actuator/prometheus 必须 404(端口隔离) ---
    @Test
    void prometheusEndpointAbsentFromBusinessPort() {
        businessClient().get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isNotFound();
    }

    // --- 2.2.5:management 端口 /actuator/health 200 ---
    @Test
    void healthEndpointReturns200OnManagementPort() {
        managementClient().get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    // --- 2.2.6:响应体含 # TYPE 与 # HELP 行(Prometheus exposition format 契约) ---
    @Test
    void bodyContainsTypeAndHelpHeaders() {
        String body = managementClient().get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body)
                .as("Prometheus exposition body must not be empty")
                .isNotNull();
        assertThat(body)
                .as("Prometheus exposition must include # TYPE metadata lines")
                .contains("# TYPE");
        assertThat(body)
                .as("Prometheus exposition must include # HELP metadata lines")
                .contains("# HELP");
    }

    // --- 2.2.7:业务请求后,prometheus 端点含 http_server_requests_seconds_count ---
    @Test
    void httpServerRequestsMeterAppearsAfterCall() {
        // 先触发一次业务请求(让 WebMvcTagsContributor 注册 http_server_requests
        // timer);指标注册是 lazy 的,只在第一次匹配的请求后产生样本。
        businessClient().get().uri("/api/products")
                .exchange()
                .expectStatus().is2xxSuccessful();

        // 再读 prometheus 输出,断言含 http_server_requests_seconds_count
        String body = managementClient().get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body)
                .as("Prometheus body must contain http_server_requests_seconds_count with uri tag for /api/products")
                .isNotNull()
                .contains("http_server_requests_seconds_count")
                .contains("uri=\"/api/products\"");
    }

    // --- 扩展断言(spec §Port isolation 双向):业务端口 /actuator/health 也应 404 ---
    @Test
    void businessPortHasNoActuatorRoutes() {
        businessClient().get().uri("/actuator/health")
                .exchange()
                .expectStatus().isNotFound();
    }

    // --- 扩展断言:management 端口不暴露 /api/** 业务路由(避免混淆 scrape) ---
    @Test
    void managementPortHasNoBusinessRoutes() {
        managementClient().get().uri("/api/products")
                .exchange()
                .expectStatus().isNotFound();
    }

    // --- 2.4.3:MongoDB driver 命令指标由 Spring Boot 自动注册
    // (spring-data-mongodb 4.5 + MongoMetricsCommandListener 触发)。
    // 触发一次 listDatabases 命令,断言 prometheus 输出含 mongodb_driver_commands_seconds_count。 ---
    @Test
    void mongoCommandsMeterAppears() {
        // 触发一次 Mongo 命令让 Micrometer 记录 sample
        mongoClient.listDatabaseNames().first();

        // 读 prometheus 输出
        String body = managementClient().get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body)
                .as("Prometheus body must contain mongodb_driver_commands_seconds_count (auto-registered MongoMetricsCommandListener)")
                .isNotNull()
                .contains("mongodb_driver_commands_seconds_count");
    }

    /**
     * 最小 Spring 上下文:只挂 1 个 {@code /api/products} stub 路由用于触发
     * http_server_requests 指标 + Actuator(由 {@code @EnableAutoConfiguration}
     * 拉起),不引入 SecurityConfig / Mongo / 业务模块,减少装配噪声。
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({TestBeans.class, TestSecurityConfig.class})
    static class TestApp {
    }

    @RestController
    static class TestController {
        @GetMapping("/api/products")
        public String products() {
            return "[]";
        }
    }

    /**
     * 测试用 SecurityFilterChain —— permitAll,跳过 JWT/限流/头过滤,只让
     * {@code /actuator/**} + {@code /api/**} 都直通,验证端口隔离本身。
     * 真实 SecurityConfig 走主代码 SecurityConfigTest / AdminRateLimitIT。
     *
     * <p>注意:这个类被 {@link TestApp}{@code .@Import} 引入(而非通过 {@code @Bean}
     * 工厂方法返回),这样 Spring 才会把它的 {@code @Bean} 方法当作 Configuration
     * 类的 bean 定义处理 —— 否则只是普通 bean,不会注册 {@code testSecurityFilterChain}。
     * {@code @ConditionalOnDefaultWebSecurity} 的默认 chain 会在
     * {@code SecurityFilterChain} bean 缺失时回退到 {@code httpBasic + formLogin},
     * 触发 401。
     */
    @Configuration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    static class TestBeans {
        @Bean
        TestController testController() {
            return new TestController();
        }
    }
}
