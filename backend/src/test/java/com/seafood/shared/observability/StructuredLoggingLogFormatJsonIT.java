package com.seafood.shared.observability;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenSpec setup-observability-stack PR #1 / task 1.5.4 —
 * 验证 dev profile + structured logging 启用 → JSON 输出(spec §LOG_FORMAT env
 * overrides dev format)。
 *
 * <p>Spring Boot 3.4+ 的 {@code LOG_FORMAT=json} env / system property 在启动时被
 * {@code org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor}
 * 映射到 {@code logging.structured.format.console=logstash},触发 structured
 * logging(参考 Spring Boot 3.4 release notes)。
 *
 * <p>本 IT 直接通过 {@code @TestPropertySource} 把等价 property 注入,功能上
 * 等价于"LOG_FORMAT=json"(都是把 {@code logging.structured.format.console}
 * 设为 {@code logstash})。{@code LOG_FORMAT} 的<em>映射机制</em>是 Spring Boot
 * 内部行为,本 IT 验证的是我们的 application.yml / spec 契约:<b>当 structured
 * logging 被启用,dev profile 也输出 JSON</b>。机制正确性由 Spring Boot 自身
 * 集成测试覆盖。
 *
 * <p>独立 IT 是因为不同 profile + 不同 structured format 必须跑在不同的 Spring
 * 上下文里(Logback appender 在 startup 时定型),@Nested 上下文缓存会复用。
 */
@Tag("native")
@ActiveProfiles("dev")
@SpringBootTest(
        classes = StructuredLoggingLogFormatJsonIT.MinApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "logging.structured.format.console=logstash")
@DirtiesContext // 确保下一个 IT(StructuredLoggingDevIT)不复用本 context 的 Logback 配置
class StructuredLoggingLogFormatJsonIT {

    private static final Logger LOG = LoggerFactory.getLogger(StructuredLoggingLogFormatJsonIT.class);

    // --- 1.5.4 ---
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logFormatEnvOverridesDev(CapturedOutput output) {
        LOG.info("structured-logging-it-probe");

        String line = findLineContaining(output.getOut(), "structured-logging-it-probe");
        assertThat(line)
                .as("dev profile + structured logging enabled must emit JSON (spec §LOG_FORMAT env overrides dev format)")
                .isNotNull();
        assertThat(line)
                .as("output line must contain a JSON @timestamp field")
                .contains("\"@timestamp\"");

        // 解析为 JSON + 验证关键字段
        JsonNode node = parseStrict(line);
        assertThat(node.get("message").asText()).isEqualTo("structured-logging-it-probe");
        assertThat(node.get("level").asText()).isEqualTo("INFO");
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

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(ObservabilityConfig.class)
    static class MinApp {
    }
}
