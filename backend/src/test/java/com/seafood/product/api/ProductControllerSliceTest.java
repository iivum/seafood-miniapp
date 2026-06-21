package com.seafood.product.api;

import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.application.ProductService;
import com.seafood.product.domain.Product;
import com.seafood.shared.error.NotFoundException;
import com.seafood.shared.security.AdminRateLimiter;
import com.seafood.shared.security.JwtTokenProvider;
import com.seafood.shared.security.SecurityHeadersProperties;
import com.seafood.testsupport.builders.ProductBuilder;
import com.seafood.testsupport.contract.OpenApiContractAssert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebMvcTest(ProductController.class)
@EnableConfigurationProperties(SecurityHeadersProperties.class)
class ProductControllerSliceTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean ProductService productService;
    // @WebMvcTest auto-loads SecurityConfig; mock its filter collaborators.
    // AdminRateLimiter feeds AdminRateLimitFilter, JwtTokenProvider +
    // TokenRevocationService feed JwtAuthenticationFilter.
    @MockitoBean AdminRateLimiter adminRateLimiter;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean com.seafood.user.application.TokenRevocationService tokenRevocationService;

    @Test
    void list_returnsPagedProducts() {
        Product product = ProductBuilder.aProduct().withId("p-test").build();
        Page<ProductResponse> page = new PageImpl<>(List.of(toResponse("p-test", product)),
            PageRequest.of(0, 20), 1);
        when(productService.listPublic(eq(null), any())).thenReturn(page);

        var result = mvc.get().uri("/api/products").exchange();
        result.assertThat()
            .hasStatusOk()
            .bodyJson()
            .hasPath("$.content[0].id");
        // C2 响应一致校验:响应真符合 committed OpenAPI 中 GET /api/products 声明的 schema
        OpenApiContractAssert.assertGetConformsToContract("/api/products", result);
    }

    @Test
    void conformance_rejectsResponseViolatingSchema() {
        // 把"商品列表"(Page)响应拿去比对"单商品 detail"(GET /api/products/{id})的 schema —
        // Page 顶层缺 ProductResponse 必填字段 → 契约校验必须失败,证明响应一致校验有牙。
        Product product = ProductBuilder.aProduct().withId("p-test").build();
        Page<ProductResponse> page = new PageImpl<>(List.of(toResponse("p-test", product)),
            PageRequest.of(0, 20), 1);
        when(productService.listPublic(eq(null), any())).thenReturn(page);

        var result = mvc.get().uri("/api/products").exchange();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                OpenApiContractAssert.assertGetConformsToContract("/api/products/{id}", result))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void getProduct_notFound_returns404() {
        when(productService.get("missing"))
            .thenThrow(new NotFoundException("产品不存在"));

        mvc.get().uri("/api/products/missing")
            .exchange()
            .assertThat()
            .hasStatus(404)
            .bodyJson()
            .hasPath("$.code");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void discontinue_asAdmin_returnsUpdatedProduct() {
        Product discontinued = ProductBuilder.aProduct().withId("p-1").build();
        when(productService.discontinue(eq("p-1")))
            .thenReturn(toResponse("p-1", discontinued));

        mvc.post().uri("/api/products/p-1/discontinue")
            .exchange()
            .assertThat()
            .hasStatusOk();
    }

    /** Map Product (record, domain) → ProductResponse (record, api.dto) for stubbing. */
    private static ProductResponse toResponse(String id, Product p) {
        return new ProductResponse(
            id,
            p.name(),
            p.description(),
            p.price(),
            p.stock(),
            p.category() == null ? null : p.category().displayName(),
            p.imageUrl(),
            p.status(),
            p.createdAt(),
            p.updatedAt());
    }
}
