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
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenSpec setup-observability-stack PR #1 / task 1.5.3 —
 * 验证 dev profile 下 Spring Boot 4 日志输出符合 spec §structured-logging
 * 的 "Console pattern in development profile" Scenario:可读 pattern,显式含
 * {@code [requestId]} 段。
 *
 * <p>独立 IT 而非 @Nested 是因为不同 profile 必须跑在不同的 Spring 上下文里
 * (Logback appender 在 startup 时定型)。
 */
@Tag("native")
@ActiveProfiles("dev")
@SpringBootTest(
        classes = StructuredLoggingDevIT.MinApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
// 显式把 logging.structured.format.console 钉死为空 ——
//
// 已知坑:Logback 的 StructuredLogEncoder 由 CONSOLE_LOG_STRUCTURED_FORMAT 这个
// Logback system property 控制(Spring Boot 在启动时把 logging.structured.format.console
// 转写到该 property)。前一 IT 跑 StructuredLoggingLogFormatJsonIT 时设置过
// logstash,JVM 静态 System property 跨 Spring context 缓存会泄漏到本 IT,
// 即便 @DirtiesContext 销毁了 Spring context,Logback 仍持有 logstash。
//
// @TestPropertySource 显式覆盖为 empty → 强制 pattern。
@TestPropertySource(properties = "logging.structured.format.console=")
@DirtiesContext
class StructuredLoggingDevIT {

    private static final Logger LOG = LoggerFactory.getLogger(StructuredLoggingDevIT.class);

    // --- 1.5.3 ---
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void devProfileEmitsPatternWithRequestId(CapturedOutput output) {
        String id = "01931a45-7c80-7000-9b3e-3f8a1c5e4d20";
        MDC.put("requestId", id);
        try {
            LOG.info("structured-logging-it-probe");
        } finally {
            MDC.remove("requestId");
        }

        String line = findLineContaining(output.getOut(), "structured-logging-it-probe");
        assertThat(line)
                .as("dev profile output must follow the readable pattern with [requestId] segment")
                .isNotNull();
        // dev pattern: "%d{HH:mm:ss.SSS} %-5level [%X{requestId}] %logger{36} - %msg%n"
        // 关键断言:含 [requestId UUID] 段(spec §Dev profile preserves readable pattern)
        assertThat(line)
                .contains("[" + id + "]")
                .contains("structured-logging-it-probe");
        // 必须不是 JSON(无 structured logging)
        assertThat(line)
                .as("dev profile must NOT emit JSON; structured.format.console is unset in dev document")
                .doesNotContain("\"@timestamp\"")
                .doesNotContain("\"level\":");
    }

    private static String findLineContaining(String all, String needle) {
        for (String l : all.split("\\R")) {
            if (l.contains(needle)) return l;
        }
        return null;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(ObservabilityConfig.class)
    static class MinApp {
    }
}
