package com.seafood.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * 请求级 TraceID 注入(OpenSpec setup-observability-stack PR #1,design §D3 / §D4)。
 *
 * <p>职责:
 * <ol>
 *   <li>从入站 {@code X-Request-Id} header 透传(若符合 UUID 正则且长度 ≤ 64),否则
 *       通过 {@link RequestIdGenerator} 生成 UUID v7。</li>
 *   <li>把 ID 写入 SLF4J MDC 的 {@code requestId} 字段 — Spring Boot 4 的
 *       structured logging(Logstash schema)会自动把它作为顶层 JSON key 输出。</li>
 *   <li>把同一 ID 写到响应 header {@code X-Request-Id},保证下游 / 前端拿到回写凭证。</li>
 *   <li>在 {@code finally} 块清理 MDC,避免虚拟线程池复用时泄漏到下一个请求
 *       (Sprint 2 §nativeTest 切片 + design §D4 明确要求)。</li>
 * </ol>
 *
 * <p>异常路径:即便 {@code chain.doFilter} 抛异常,finally 块仍保证 MDC 清理
 * 与 response header 写入(在 response commit 之前完成)。
 *
 * <p>GraalVM Native 兼容:UUID 正则在编译期就确定;不依赖任何反射;SLF4J / MDC
 * 走 SLF4J 2.x facade,native-image 友好。
 *
 * <p>本 filter <em>不</em>用 {@code @Component} — 注册通过
 * {@link ObservabilityConfig#requestIdFilter()} 显式声明 URL pattern 与顺序,
 * 这样测试可绕开 Spring 直接 new()。
 */
public class RequestIdFilter extends OncePerRequestFilter {

    /** HTTP header 名(对外契约,SPEC §Request identifier MDC field)。 */
    public static final String HEADER = "X-Request-Id";

    /** MDC key(Spring Boot 4 Logstash structured logging 自动作为顶层 JSON key)。 */
    public static final String MDC_KEY = "requestId";

    /** 头值最大长度(SPEC §Request identifier passthrough 1;design §Risks 防 log injection)。 */
    public static final int MAX_LENGTH = 64;

    /**
     * UUID 正则(SPEC §Request identifier passthrough 1)。不强制 hex 段大小写,允许
     * {@code 01931A45-7C80-7000-9B3E-3F8A1C5E4D20} 等大写形式透传。
     */
    public static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE);

    private static final Logger LOG = LoggerFactory.getLogger(RequestIdFilter.class);

    private final RequestIdGenerator generator;

    /** 默认构造 — 供 Spring 容器化装配使用。 */
    public RequestIdFilter() {
        this(new RequestIdGenerator.TimeBasedEpoch());
    }

    /** 测试 / 自定义生成器注入。 */
    public RequestIdFilter(RequestIdGenerator generator) {
        this.generator = generator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(req.getHeader(HEADER));
        try {
            MDC.put(MDC_KEY, requestId);
            res.setHeader(HEADER, requestId);
            chain.doFilter(req, res);
        } finally {
            // 必须无条件清 — 否则虚拟线程复用时泄漏到下一个请求
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * 决定入站请求的 requestId。优先级(SPEC §Request identifier passthrough):
     * <ol>
     *   <li>header 存在 + 匹配 UUID 正则 + 长度 ≤ 64 → 透传</li>
     *   <li>否则 → 通过 {@link RequestIdGenerator} 生成新 UUID v7,并在 header 不合法
     *       时记录一条 WARN(WARN message 不回显原始 header 值,防 log injection)</li>
     * </ol>
     */
    private String resolveRequestId(String header) {
        if (header != null) {
            if (header.length() > MAX_LENGTH) {
                LOG.warn("X-Request-Id rejected: length exceeds {} chars", MAX_LENGTH);
                return generator.next().toString();
            }
            if (!UUID_PATTERN.matcher(header).matches()) {
                // SPEC §Malformed X-Request-Id is replaced:message 不回显原始值
                LOG.warn("X-Request-Id rejected: malformed (length={})", header.length());
                return generator.next().toString();
            }
            return header;
        }
        return generator.next().toString();
    }
}
