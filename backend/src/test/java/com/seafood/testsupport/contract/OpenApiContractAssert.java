package com.seafood.testsupport.contract;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 5 C2 — 响应一致校验(对应 spec:响应一致校验证明实现符合声明)。
 *
 * <p>加载 committed OpenAPI 契约 {@code /contract/openapi.json}(由 {@code OpenApiContractIT} 生成),
 * 用 swagger-request-validator 的 core API 校验某 slice 测试响应真符合契约中该端点声明的 schema。
 * 从 {@link MvcTestResult} 取 status/contentType/body 手接,不依赖 mockmvc adapter(spring-test 7
 * 的 MockMvcTester 无官方 adapter)。slice 测试只需一行调用,无 springdoc / 无 docker 依赖。
 */
public final class OpenApiContractAssert {

    private static final OpenApiInteractionValidator VALIDATOR = build();

    private OpenApiContractAssert() {
    }

    private static OpenApiInteractionValidator build() {
        try (var in = OpenApiContractAssert.class.getResourceAsStream("/contract/openapi.json")) {
            if (in == null) {
                throw new IllegalStateException(
                        "缺 committed 契约 /contract/openapi.json — 先跑 OpenApiContractIT 生成并提交");
            }
            String spec = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return OpenApiInteractionValidator.createForInlineApiSpecification(spec).build();
        } catch (Exception e) {
            throw new IllegalStateException("加载 OpenAPI 契约失败", e);
        }
    }

    /**
     * 断言响应符合 committed OpenAPI 中 {@code pathTemplate + method} 声明的 schema。
     *
     * @param pathTemplate OpenAPI 路径模板(如 {@code /api/products/{id}}),非实际带值路径
     * @param method       HTTP 方法
     * @param result       MockMvcTester 的 exchange 结果
     */
    /** 便捷:校验 GET 响应符合契约(slice 测试不必 import com.atlassian)。 */
    public static void assertGetConformsToContract(String pathTemplate, MvcTestResult result) {
        assertConformsToContract(pathTemplate, Request.Method.GET, result);
    }

    /** 便捷:校验 POST 响应符合契约。 */
    public static void assertPostConformsToContract(String pathTemplate, MvcTestResult result) {
        assertConformsToContract(pathTemplate, Request.Method.POST, result);
    }

    public static void assertConformsToContract(String pathTemplate, Request.Method method, MvcTestResult result) {
        var resp = result.getResponse();
        SimpleResponse apiResponse = SimpleResponse.Builder
                .status(resp.getStatus())
                .withContentType(resp.getContentType())
                .withBody(resp.getContentAsByteArray())
                .build();
        ValidationReport report = VALIDATOR.validateResponse(pathTemplate, method, apiResponse);
        assertThat(report.hasErrors())
                .as("响应不符合 OpenAPI 契约 [%s %s]:%s", method, pathTemplate,
                        report.getMessages().stream().map(Object::toString).toList())
                .isFalse();
    }
}
