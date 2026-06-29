package com.seafood.contract;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.seafood.SeafoodApplication;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 5 C2 — OpenAPI 契约生成 + 漂移门(tasks §1-2)。
 *
 * <p>springdoc-openapi 3.0.3 从真实 Controller(全上下文 {@link SeafoodApplication})生成
 * OpenAPI 3 spec;{@code addFilters = false} 绕过 SecurityConfig 的 {@code anyRequest().denyAll()},
 * 否则 {@code /v3/api-docs} 不在白名单会 403。
 *
 * <p><b>漂移门</b>:规范化(排序 key + 剔除易变 {@code servers})后与 committed SoT
 * {@code src/test/resources/contract/openapi.json} 比对,不一致即 fail —— 逼 API 变更被有意识提交。
 * API 有意变更后用 {@code CONTRACT_UPDATE=true ./gradlew test --tests *OpenApiContractIT} 重生成。
 *
 * <p>docker-tagged:全上下文需 Mongo(Testcontainers);springdoc 仅 testImplementation,
 * 不进 native 运行时(design D1)。
 */
@Tag("docker")
@Testcontainers
@SpringBootTest(
        classes = SeafoodApplication.class,
        properties = {
                "security.jwt.secret=test-jwt-secret-at-least-32-bytes-long-AAAA",
                "security.jwt.admin-secret=test-jwt-admin-secret-at-least-32-bytes-BBBB",
                "admin.bootstrap.password=test-admin-bootstrap-password-not-default",
                "wechat.enabled=false",
        })
@AutoConfigureMockMvc(addFilters = false)
class OpenApiContractIT {

    private static final Path CONTRACT = Path.of("src/test/resources/contract/openapi.json");

    /** 规范化器:递归按 key 排序 + 缩进,消除 springdoc 输出顺序不稳定导致的假漂移。 */
    private static final JsonMapper NORMALIZER = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    @Container
    @SuppressWarnings("resource")
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @Autowired
    MockMvcTester mvc;

    @Test
    void apiDocs_generatesNonEmptyOpenApiSpec() {
        mvc.get().uri("/v3/api-docs")
                .exchange()
                .assertThat()
                .hasStatusOk()
                .bodyText()
                .contains("\"openapi\":\"3")
                .contains("\"/api/products\"")
                .contains("\"/api/admin/products")
                .contains("\"schemas\":{");
    }

    @Test
    void contract_matchesCommittedSpec() throws Exception {
        String current = normalize(fetchOpenApiJson());

        if ("true".equals(System.getenv("CONTRACT_UPDATE")) || Files.notExists(CONTRACT)) {
            Files.createDirectories(CONTRACT.getParent());
            Files.writeString(CONTRACT, current);
            System.out.println("[contract] 已写入 SoT " + CONTRACT + "(" + current.length() + " chars)");
            return;
        }

        // TEMP DEBUG (CI 诊断契约漂移):把生成的 spec 写进 always() 上传的 jacoco 报告目录,
        // 便于离线精确 diff。诊断后移除。
        Path debug = Path.of("build/reports/jacoco/test/generated-openapi.json");
        Files.createDirectories(debug.getParent());
        Files.writeString(debug, current);

        String committed = Files.readString(CONTRACT);
        assertThat(current)
                .as("OpenAPI 契约漂移:API 变了但未更新 committed spec。确认变更有意后跑 "
                        + "`CONTRACT_UPDATE=true ./gradlew test --tests *OpenApiContractIT -PexcludeTags=` "
                        + "重生成并提交 " + CONTRACT)
                .isEqualTo(committed);
    }

    private String fetchOpenApiJson() throws Exception {
        var result = mvc.get().uri("/v3/api-docs").exchange();
        result.assertThat().hasStatusOk();
        return result.getResponse().getContentAsString();
    }

    @SuppressWarnings("unchecked")
    private static String normalize(String rawJson) throws Exception {
        Object tree = NORMALIZER.readValue(rawJson, Object.class);
        if (tree instanceof Map<?, ?>) {
            // servers 含运行期 host/port(随机端口),非契约本体 → 剔除防假漂移
            ((Map<String, Object>) tree).remove("servers");
        }
        return NORMALIZER.writeValueAsString(tree) + "\n";
    }
}
