package com.seafood.shared.error;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * code-review 发现(2026-07-17):{@code ContractErrorAttributes} 只把 404 映射
 * {@code code=NOT_FOUND}，其余任何状态一律 {@code code=INTERNAL}——filter 层的
 * URL 级鉴权拒绝（{@code SecurityConfig} 的 {@code authenticated()} 规则，不是
 * 方法级 {@code @PreAuthorize}）从未经过任何 {@code @ExceptionHandler}，直接被
 * Spring Security 转发到 {@code /error}，命中 {@code ContractErrorAttributes}——
 * 状态码对（403），但契约里的 {@code code} 字段说谎（说 INTERNAL，实际是 FORBIDDEN）。
 *
 * <p>必须用真实容器（{@code RANDOM_PORT} + {@link RestTestClient}），不能用
 * {@code MockMvc}——{@code HttpServletResponse#sendError()} 触发的容器级
 * {@code /error} 转发在 {@code MockMvc} 里不完整模拟，同 {@link GlobalExceptionHandlerContractIT}
 * 复用 {@code TestApp}/{@code TestBeans}，但独立成本文件用 {@code RANDOM_PORT}。
 */
@SpringBootTest(
        classes = GlobalExceptionHandlerContractIT.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "security.jwt.secret=ci-test-secret-must-be-at-least-32-bytes-long-AAAA",
        "security.jwt.admin-secret=ci-test-admin-secret-must-be-at-least-32-bytes-BBBB",
        "admin.bootstrap.password=ci-test-admin-bootstrap-password"
})
class ContractErrorAttributesUrlLevelDenialIT {

    @LocalServerPort
    private int port;

    private RestTestClient client() {
        return RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void urlLevelAuthDenial_hitsErrorPath_returnsForbiddenCode_notInternal() {
        // 不带 Authorization header 命中 /api/orders/** —— SecurityConfig 的
        // authenticated() URL 级规则直接拒绝，请求连 DispatcherServlet 都没进去，
        // 不会触发 GlobalExceptionHandler.accessDenied()（那条测的是方法级
        // @PreAuthorize 在分发期间抛异常，见 GlobalExceptionHandlerContractIT
        // 的 methodLevelPreAuthorizeDenial 用例）。
        var response = client().get().uri("/api/orders/admin-only-probe")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(response)
                .as("filter 层 403 应该走 ContractErrorAttributes 渲染成 {code:FORBIDDEN}，不是 {code:INTERNAL}")
                .contains("\"code\":\"FORBIDDEN\"");
    }
}
