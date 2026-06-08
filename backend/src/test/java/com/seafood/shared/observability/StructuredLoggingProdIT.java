package com.seafood.shared.observability;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenSpec setup-observability-stack PR #1 / task 1.5.2 + 1.5.5 —
 * 验证 prod profile 下 Spring Boot 4 结构化日志输出符合 spec §structured-logging
 * 的 "JSON output in production profile" 与 "Stack traces serialize as a single
 * field" 两个 Scenario。
 *
 * <p>独立 IT 而非 @Nested 是因为不同 profile 必须跑在不同的 Spring 上下文里
 * (Logback appender 在 startup 时定型),而 @Nested 共享 outer 类的测试上下文缓存。
 */
@Tag("native")
@ActiveProfiles("prod")
@SpringBootTest(
        classes = StructuredLoggingProdIT.MinApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext // 隔离 context,避免 Logback appender 跨 IT 复用导致上一 IT 的 structured
               // encoding 状态泄漏到 dev profile 测试
class StructuredLoggingProdIT {

    private static final Logger LOG = LoggerFactory.getLogger(StructuredLoggingProdIT.class);

    private static final String PROBE = "structured-logging-it-probe";

    // --- 1.5.2 ---
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void productionProfileEmitsJson(CapturedOutput output) {
        String id = "01931a45-7c80-7000-9b3e-3f8a1c5e4d20";
        MDC.put("requestId", id);
        try {
            LOG.info(PROBE);
        } finally {
            MDC.remove("requestId");
        }

        String line = findLineContaining(output.getOut(), PROBE);
        assertThat(line)
                .as("prod profile output for INFO must be a single-line JSON line")
                .isNotNull();

        // 解析为 JSON,验证 schema(spec §Production log line is single-line JSON)
        JsonNode node = parseStrict(line);
        assertThat(node.get("@timestamp"))
                .as("@timestamp must be present and parseable as ISO-8601")
                .isNotNull();
        assertThat(node.get("@timestamp").asText())
                .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[.,]\\d+.*");
        assertThat(node.get("level").asText()).isEqualTo("INFO");
        assertThat(node.get("message").asText()).isEqualTo(PROBE);
        // MDC requestId 必须作为顶层 JSON key(spec §Request identifier MDC field)
        assertThat(node.get("requestId").asText()).isEqualTo(id);
    }

    // --- 1.5.5 ---
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void stackTraceSerializesAsSingleField(CapturedOutput output) {
        RuntimeException ex = new RuntimeException("intentional test failure");
        LOG.error("error-with-stack", ex);

        // 整行 JSON(用换行符切分后取含 "error-with-stack" 的第一行 ——
        // stack_trace 字段值是 string,内含 \n 转义序列,parse 后才会变回字面换行)
        String line = findLineContaining(output.getOut(), "error-with-stack");
        assertThat(line)
                .as("ERROR event with stack trace must still be a single JSON line in captured stdout")
                .isNotNull();

        // 1) 整行作为 raw JSON,内嵌换行必须以 \n 转义(在 JSON 字符串里写为 \\n,
        // 即 1 个反斜杠 + 1 个 n),不能在 stdout 引入字面换行。
        // .contains("\\n") 在 Java 源码里是 2 字符(\ + n),实际去匹配 raw line
        // 里的字面 "\n"(也是 2 字符:1 个 \ + 1 个 n)。
        assertThat(line)
                .as("raw JSON line must contain escaped \\\\n (not literal newline in stdout)")
                .contains("\\n");

        // 2) 解析后 stack_trace 是单 string 字段,含异常类型与 message
        JsonNode node = parseStrict(line);
        assertThat(node.get("level").asText()).isEqualTo("ERROR");
        assertThat(node.get("message").asText()).isEqualTo("error-with-stack");
        assertThat(node.get("stack_trace"))
                .as("stack trace must be a single string field (spec §Stack traces serialize as a single field)")
                .isNotNull();
        assertThat(node.get("stack_trace").isTextual()).isTrue();
        String stack = node.get("stack_trace").asText();
        assertThat(stack)
                .as("stack trace text (post-parse) must mention the exception type and message")
                .contains("RuntimeException")
                .contains("intentional test failure");
    }

    private static String findLineContaining(String all, String needle) {
        for (String l : all.split("\\R")) {
            if (l.contains(needle)) return l;
        }
        return null;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode parseStrict(String jsonLine) {
        try {
            return MAPPER.readTree(jsonLine);
        } catch (Exception e) {
            throw new AssertionError("line is not valid JSON: " + jsonLine, e);
        }
    }

    /**
     * 最小 Spring 上下文 — 只装 observability 配置(用于拉起 structured logging 体系),
     * 不拉起 Mongo / Redis / Web。省 context 启动时间,只关心 Logback 配置。
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(ObservabilityConfig.class)
    static class MinApp {
    }
}
