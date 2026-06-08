package com.seafood.shared.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OpenSpec setup-observability-stack PR #1 / tasks 1.6.x —
 * 验证 {@link RequestIdFilter} 即便下游抛 {@link RuntimeException} 触发 5xx,
 * 响应仍带 {@code X-Request-Id} 头。
 *
 * <p>MDC 注入 log line 的完整断言留 {@link StructuredLoggingProdIT} /
 * {@link StructuredLoggingDevIT}(1.5.x);filter 顺序的结构断言留
 * {@link ObservabilityConfigTest}(plain JUnit,验证
 * {@code FilterRegistrationBean.getOrder() == HIGHEST_PRECEDENCE + 100})。
 *
 * <p>Spring Security 401 路径的 RequestId 透传验证留
 * {@code RequestIdFilterSecurityChainIT}(独立拆,本期不实现 —
 * 项目禁用 {@code @WebMvcTest},等 Sprint 3 引入专用 IT 工具集再补,
 * 见 tasks.md 1.6.1 注释)。
 *
 * <p>架构说明:用 {@link MockMvcBuilders#standaloneSetup} + 显式
 * {@code addFilter} 装配 {@code RequestIdFilter},不依赖 Spring 容器,
 * 避开 SecurityAutoConfiguration / Mongo / Redis 自动装配的干扰,
 * 隔离测试 RequestIdFilter 自身契约。
 */
@Tag("native")
class RequestIdFilterOrderIT {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        // standaloneSetup 默认不装 HandlerExceptionResolver;RuntimeException
        // 会冒泡到 ServletException,无法断言 5xx。加 stub advice 转 5xx。
        mvc = MockMvcBuilders
                .standaloneSetup(new BoomController())
                .setControllerAdvice(new GlobalErrorStub())
                .addFilters(new RequestIdFilter())
                .build();
    }

    // --- 1.6.2:服务端异常路径(5xx)仍带 X-Request-Id ---
    @Test
    void internalErrorPreservesRequestId() throws Exception {
        mvc.perform(get("/__test__/boom"))
                .andExpect(status().is5xxServerError())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void internalErrorWithIncomingHeaderPassesThrough() throws Exception {
        String incoming = "01931a45-7c80-7000-9b3e-3f8a1c5e4d20";
        mvc.perform(get("/__test__/boom").header("X-Request-Id", incoming))
                .andExpect(status().is5xxServerError())
                .andExpect(header().string("X-Request-Id", incoming));
    }

    @Test
    void internalErrorGeneratesUuidV7IfHeaderMissing() throws Exception {
        var result = mvc.perform(get("/__test__/boom"))
                .andExpect(status().is5xxServerError())
                .andExpect(header().exists("X-Request-Id"))
                .andReturn();
        String responseId = result.getResponse().getHeader("X-Request-Id");
        assertThat(java.util.UUID.fromString(responseId).version())
                .as("X-Request-Id 必须是 UUID v7 (design §D3 + ADR-OQ1)")
                .isEqualTo(7);
    }

    @RestController
    static class BoomController {
        @GetMapping("/__test__/boom")
        public String boom() {
            throw new RuntimeException("intentional test failure");
        }
    }

    @ControllerAdvice
    static class GlobalErrorStub {
        @ExceptionHandler(RuntimeException.class)
        public ResponseEntity<Void> handle(RuntimeException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
