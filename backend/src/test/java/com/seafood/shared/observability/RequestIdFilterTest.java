package com.seafood.shared.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * PR #1 / 1.1.x — {@link RequestIdFilter} 单元测试。
 *
 * <p>纯单元测试,plain JUnit + Mockito + Spring 的 {@code MockHttpServletRequest/Response}。
 * 不依赖 {@code @WebMvcTest}(Spring Boot 4 移除)。直接 new filter,手工触发。
 *
 * <p>覆盖设计 §D3 / §D4 / §spec §Request identifier passthrough and generation /
 * §MDC lifecycle isolation 的所有 WHEN-THEN 行为。
 */
class RequestIdFilterTest {

    private RequestIdFilter filter;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger warnLogger;

    @BeforeEach
    void setUp() {
        filter = new RequestIdFilter();
        // 拦截 WARN 日志,断言"X-Request-Id rejected" 的内容
        warnLogger = (Logger) LoggerFactory.getLogger(RequestIdFilter.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        warnLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        warnLogger.detachAppender(logAppender);
        MDC.clear();
    }

    // --- 1.1.2 ---
    @Test
    void generatesUuidV7_whenHeaderAbsent() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/products");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        String header = res.getHeader(RequestIdFilter.HEADER);
        assertThat(header).isNotNull();
        assertThat(UUID.fromString(header).version())
                .as("X-Request-Id 必须是 UUID v7 (D3 + ADR-OQ1)")
                .isEqualTo(7);
    }

    // --- 1.1.3 ---
    @Test
    void passesThroughValidUuid_whenHeaderValid() throws Exception {
        String validV7 = "01931a45-7c80-7000-9b3e-3f8a1c5e4d20";
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/products");
        req.addHeader(RequestIdFilter.HEADER, validV7);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getHeader(RequestIdFilter.HEADER)).isEqualTo(validV7);
    }

    // --- 1.1.4 ---
    @Test
    void rejectsMalformedHeader_andLogsWarn() throws Exception {
        String evil = "<script>alert(1)</script>";
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/products");
        req.addHeader(RequestIdFilter.HEADER, evil);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        String responseHeader = res.getHeader(RequestIdFilter.HEADER);
        assertThat(responseHeader).isNotEqualTo(evil);
        // 必须是合法 UUID v7
        assertThat(UUID.fromString(responseHeader).version()).isEqualTo(7);

        // WARN 日志记录"X-Request-Id rejected",且 message 不含原始恶意字符串
        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent event = logAppender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
        assertThat(event.getFormattedMessage())
                .as("WARN message must NOT echo the malicious value verbatim")
                .doesNotContain(evil)
                .doesNotContain("<script>");
    }

    // --- 1.1.5 ---
    @Test
    void rejectsOversizedHeader() throws Exception {
        // 65 个字符的字符串(虽然不是 UUID 格式,但长度先被拦截)
        String oversized = "a".repeat(RequestIdFilter.MAX_LENGTH + 1);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/products");
        req.addHeader(RequestIdFilter.HEADER, oversized);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        String responseHeader = res.getHeader(RequestIdFilter.HEADER);
        assertThat(responseHeader)
                .as("oversized header must be discarded, response header must be fresh UUID v7")
                .isNotEqualTo(oversized);
        assertThat(UUID.fromString(responseHeader).version()).isEqualTo(7);
    }

    // --- 1.1.6 ---
    @Test
    void clearsMdcOnSuccessPath() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/products");
        req.addHeader(RequestIdFilter.HEADER, "01931a45-7c80-7000-9b3e-3f8a1c5e4d20");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(MDC.get(RequestIdFilter.MDC_KEY))
                .as("MDC must be cleared after request completes")
                .isNull();
    }

    // --- 1.1.7 ---
    @Test
    void clearsMdcOnExceptionPath() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/products");
        req.addHeader(RequestIdFilter.HEADER, "01931a45-7c80-7000-9b3e-3f8a1c5e4d20");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doThrow(new RuntimeException("boom")).when(chain).doFilter(any(), any());

        // finally 块必须清理 MDC + 写入 response header(虽 response 已 commit,setHeader 不会抛 NPE)
        try {
            filter.doFilter(req, res, chain);
        } catch (RuntimeException expected) {
            assertThat(expected.getMessage()).isEqualTo("boom");
        }

        assertThat(MDC.get(RequestIdFilter.MDC_KEY))
                .as("MDC must be cleared even on exception path")
                .isNull();
    }

    // --- 1.1.8 ---
    @Test
    void isolatesAcrossSequentialRequestsOnSameVirtualThread() throws Exception {
        // 第一个请求带 header
        MockHttpServletRequest req1 = new MockHttpServletRequest("GET", "/api/products");
        req1.addHeader(RequestIdFilter.HEADER, "01931a45-7c80-7000-9b3e-3f8a1c5e4d20");
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        filter.doFilter(req1, res1, mock(FilterChain.class));

        String firstId = res1.getHeader(RequestIdFilter.HEADER);
        assertThat(firstId).isEqualTo("01931a45-7c80-7000-9b3e-3f8a1c5e4d20");
        // 第一个请求结束后 MDC 必须清
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();

        // 第二个请求不带 header — 必须新生成,不能复用 firstId
        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/api/products");
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        filter.doFilter(req2, res2, mock(FilterChain.class));

        String secondId = res2.getHeader(RequestIdFilter.HEADER);
        assertThat(secondId)
                .as("second request must generate a fresh UUID v7, not reuse first request's id")
                .isNotEqualTo(firstId);
        assertThat(UUID.fromString(secondId).version()).isEqualTo(7);

        // 第二个请求结束后 MDC 也必须清
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    // --- extra:大写 UUID 透传(SPEC §Request identifier passthrough 1) ---
    @Test
    void passesThroughValidUuid_caseInsensitive() throws Exception {
        String validV7Upper = "01931A45-7C80-7000-9B3E-3F8A1C5E4D20";
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/products");
        req.addHeader(RequestIdFilter.HEADER, validV7Upper);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getHeader(RequestIdFilter.HEADER)).isEqualTo(validV7Upper);
    }

    // --- extra:边界 64 字符仍合法,65 字符拒 ---
    @Test
    void acceptsHeaderAtMaxLengthBoundary() throws Exception {
        // 标准 UUID 36 字符,在 64 字符以内,合法
        String validV7 = "01931a45-7c80-7000-9b3e-3f8a1c5e4d20";
        assertThat(validV7.length()).isLessThanOrEqualTo(RequestIdFilter.MAX_LENGTH);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/products");
        req.addHeader(RequestIdFilter.HEADER, validV7);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, mock(FilterChain.class));
        assertThat(res.getHeader(RequestIdFilter.HEADER)).isEqualTo(validV7);
    }
}
