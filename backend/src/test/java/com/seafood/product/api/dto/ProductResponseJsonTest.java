package com.seafood.product.api.dto;

import com.seafood.product.domain.Product;
import com.seafood.product.domain.ProductCategory;
import com.seafood.product.domain.ProductStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @JsonTest slice for the {@link ProductResponse} record. Validates record round-trip
 * serialization. {@link JacksonTester} is auto-configured by the @JsonTest slice and
 * uses the application's auto-configured ObjectMapper (Java time module included).
 */
@JsonTest
class ProductResponseJsonTest {

    @Autowired
    private JacksonTester<ProductResponse> json;

    @Test
    void serializesAllFields() throws Exception {
        Product p = new Product(
                "p1", "三文鱼", "新鲜", new BigDecimal("99.00"), 10,
                new ProductCategory.Fish(), "http://img",
                ProductStatus.ACTIVE,
                Instant.parse("2026-06-05T00:00:00Z"),
                Instant.parse("2026-06-05T00:00:00Z"));
        ProductResponse response = ProductResponse.from(p);

        assertThat(json.write(response).getJson())
                .contains("\"id\":\"p1\"")
                .contains("\"name\":\"三文鱼\"")
                .contains("\"category\":\"鱼类\"")
                .contains("\"status\":\"ACTIVE\"")
                .contains("\"stock\":10");
    }

    @Test
    void deserializesFromJson() throws Exception {
        String body = """
                {
                  "id": "p2",
                  "name": "金枪鱼",
                  "description": "大份",
                  "price": 199.00,
                  "stock": 5,
                  "category": "鱼类",
                  "imageUrl": "http://img/2",
                  "status": "ACTIVE",
                  "createdAt": "2026-06-05T00:00:00Z",
                  "updatedAt": "2026-06-05T00:00:00Z"
                }
                """;

        ProductResponse parsed = json.parseObject(body);

        assertThat(parsed.id()).isEqualTo("p2");
        assertThat(parsed.name()).isEqualTo("金枪鱼");
        assertThat(parsed.price()).isEqualByComparingTo("199.00");
        assertThat(parsed.stock()).isEqualTo(5);
        assertThat(parsed.status()).isEqualTo(ProductStatus.ACTIVE);
    }
}
