package com.seafood.product.infra;

import com.mongodb.client.MongoCollection;
import com.seafood.product.domain.ProductStatus;
import com.seafood.testsupport.MongoIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * fix-category-bad-status-500 task 1.1/1.2:真实经 Spring Data MongoDB 的
 * document→entity 转换层验证。{@code ProductDocumentRepositoryIT}/
 * {@code ProductRepositorySliceTest} 都只走 raw MongoClient（Boot 4.0.6 test
 * starter 不带 {@code @DataMongoTest}，见 {@link MongoIntegrationTest} 类注释），
 * 无法复现本次要修的 bug——崩溃点正是 Spring Data 把 BSON status 字符串转换成
 * {@link ProductStatus} 枚举那一步，raw driver 断言完全绕不过去。本类显式起
 * 最小 Spring 上下文（{@code @EnableMongoRepositories} + 真实生产
 * {@code MongoCustomConversions} 配置），让 {@link ProductRepository} 的派生
 * 查询方法真正跑起来，验证的是生产接线本身，不是测试自己重新拼一份。
 */
@Tag("native")
@SpringBootTest(classes = ProductCategoryStatusQueryIT.TestApp.class)
class ProductCategoryStatusQueryIT extends MongoIntegrationTest {

    private static final String DB = "seafood_test";

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGO.getReplicaSetUrl(DB));
    }

    @Autowired
    private ProductRepository repository;

    private MongoCollection<Document> products() {
        return database(DB).getCollection("products");
    }

    @BeforeEach
    void clearCollection() {
        products().deleteMany(new Document());
    }

    private Document productDoc(String id, String name, String category, String status) {
        Date now = Date.from(Instant.parse("2026-07-15T00:00:00Z"));
        return new Document()
                .append("_id", id)
                .append("name", name)
                .append("description", "desc")
                .append("price", 10.0)
                .append("stock", 5)
                .append("category", category)
                .append("imageUrl", "http://img")
                .append("onSale", "ACTIVE".equals(status))
                .append("status", status)
                .append("createdAt", now)
                .append("updatedAt", now);
    }

    @Test
    void findByCategoryAndStatus_excludesBadStatusDoc_doesNotThrow() {
        products().insertOne(productDoc("p-good-1", "三文鱼", "鱼类", "ACTIVE"));
        products().insertOne(productDoc("p-good-2", "带鱼", "鱼类", "ACTIVE"));
        // 非法 status 值——Mongo query 层面按 status="ACTIVE" 过滤，该文档天然被排除，
        // 不会进入 document→entity 转换。这条证明查询范围正确收窄、不误伤好数据，
        // 但converter 是否生效由下面 findByCategory（无状态过滤）那条测试证明。
        products().insertOne(productDoc("p-bad", "坏数据鱼", "鱼类", "INACTIVE"));

        Page<ProductDocument> page = repository.findByCategoryAndStatus(
                "鱼类", ProductStatus.ACTIVE, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(ProductDocument::getId)
                .containsExactlyInAnyOrder("p-good-1", "p-good-2");
    }

    @Test
    void findByCategoryAndStatus_onlyReturnsRequestedCategoryAndStatusIntersection() {
        products().insertOne(productDoc("p-fish-active", "三文鱼", "鱼类", "ACTIVE"));
        products().insertOne(productDoc("p-fish-discontinued", "下架鱼", "鱼类", "DISCONTINUED"));
        products().insertOne(productDoc("p-shrimp-active", "虾", "虾蟹", "ACTIVE"));

        Page<ProductDocument> page = repository.findByCategoryAndStatus(
                "鱼类", ProductStatus.ACTIVE, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(ProductDocument::getId)
                .containsExactly("p-fish-active");
    }

    @Test
    void findByCategory_withoutStatusFilter_toleratesBadStatusViaConverter_doesNotThrow() {
        // listAdmin 路径用的是无状态过滤的 findByCategory——这里坏数据文档
        // *会* 被拉取并尝试转换，真正验证 ProductStatusReadConverter 兜底生效。
        products().insertOne(productDoc("p-good", "三文鱼", "鱼类", "ACTIVE"));
        products().insertOne(productDoc("p-bad", "坏数据鱼", "鱼类", "INACTIVE"));

        Page<ProductDocument> page = repository.findByCategory("鱼类", PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        ProductDocument bad = page.getContent().stream()
                .filter(d -> d.getId().equals("p-bad"))
                .findFirst().orElseThrow();
        assertThat(bad.getStatus()).isEqualTo(ProductStatus.DISCONTINUED);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableMongoRepositories(basePackageClasses = ProductRepository.class)
    @Import(ProductMongoConversionConfig.class)
    static class TestApp {
    }
}
