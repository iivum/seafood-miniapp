package com.seafood.admin.service;

import com.seafood.admin.client.CreateProductRequest;
import com.seafood.admin.client.ProductClient;
import com.seafood.admin.client.ProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductClient productClient;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productClient);
    }

    @Test
    @DisplayName("getAllProducts returns list of products")
    void getAllProducts_returnsProducts() {
        ProductResponse product = new ProductResponse();
        product.setId("1");
        product.setName("Test Product");
        when(productClient.getAllProducts()).thenReturn(List.of(product));

        List<ProductResponse> result = productService.getAllProducts();

        assertThat(result).hasSize(1);
        verify(productClient).getAllProducts();
    }

    @Test
    @DisplayName("getAllProducts returns empty list on error")
    void getAllProducts_returnsEmptyListOnError() {
        when(productClient.getAllProducts()).thenThrow(new RuntimeException("Network error"));

        List<ProductResponse> result = productService.getAllProducts();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("createProduct calls client with valid request")
    void createProduct_callsClientWithValidRequest() {
        CreateProductRequest request = new CreateProductRequest();
        request.setName("New Product");
        request.setPrice(BigDecimal.TEN);
        request.setStock(100);
        ProductResponse response = new ProductResponse();
        response.setId("1");
        when(productClient.createProduct(any())).thenReturn(response);

        ProductResponse result = productService.createProduct(request);

        assertThat(result.getId()).isEqualTo("1");
        verify(productClient).createProduct(request);
    }

    @Test
    @DisplayName("createProduct throws exception for null request")
    void createProduct_throwsExceptionForNullRequest() {
        assertThatThrownBy(() -> productService.createProduct(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null");
    }

    @Test
    @DisplayName("createProduct throws exception for blank name")
    void createProduct_throwsExceptionForBlankName() {
        CreateProductRequest request = new CreateProductRequest();
        request.setName("");
        request.setPrice(BigDecimal.TEN);

        assertThatThrownBy(() -> productService.createProduct(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name is required");
    }

    @Test
    @DisplayName("createProduct throws exception for invalid price")
    void createProduct_throwsExceptionForInvalidPrice() {
        CreateProductRequest request = new CreateProductRequest();
        request.setName("Product");
        request.setPrice(BigDecimal.ZERO);

        assertThatThrownBy(() -> productService.createProduct(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("price must be positive");
    }

    @Test
    @DisplayName("deleteProduct calls client with valid id")
    void deleteProduct_callsClientWithValidId() {
        doNothing().when(productClient).deleteProduct("1");

        productService.deleteProduct("1");

        verify(productClient).deleteProduct("1");
    }

    @Test
    @DisplayName("deleteProduct throws exception for blank id")
    void deleteProduct_throwsExceptionForBlankId() {
        assertThatThrownBy(() -> productService.deleteProduct(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ID is required");
    }

    @Test
    @DisplayName("getLowStockCount returns count of products with stock below 10")
    void getLowStockCount_returnsCorrectCount() {
        ProductResponse p1 = new ProductResponse();
        p1.setStock(5);
        ProductResponse p2 = new ProductResponse();
        p2.setStock(15);
        ProductResponse p3 = new ProductResponse();
        p3.setStock(3);
        when(productClient.getAllProducts()).thenReturn(List.of(p1, p2, p3));

        int count = productService.getLowStockCount();

        assertThat(count).isEqualTo(2);
    }
}
