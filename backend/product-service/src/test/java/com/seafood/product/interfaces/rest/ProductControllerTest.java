package com.seafood.product.interfaces.rest;

import com.seafood.product.application.ProductApplicationService;
import com.seafood.product.domain.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductApplicationService productApplicationService;

    @Test
    void testGetAllProducts() throws Exception {
        Product product1 = new Product("Lobster", "Fresh lobster", new BigDecimal("49.99"), 100, "Seafood", "url1");
        Product product2 = new Product("Salmon", "Fresh salmon", new BigDecimal("29.99"), 200, "Fish", "url2");
        when(productApplicationService.listAllProducts()).thenReturn(Arrays.asList(product1, product2));
        
        mockMvc.perform(get("/products/all"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].name").value("Lobster"))
                .andExpect(jsonPath("$[1].name").value("Salmon"));
    }

    @Test
    void testGetAllProductsEmpty() throws Exception {
        when(productApplicationService.listAllProducts()).thenReturn(Collections.emptyList());
        
        mockMvc.perform(get("/products/all"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetProductById() throws Exception {
        Product product = new Product("Lobster", "Fresh lobster", new BigDecimal("49.99"), 100, "Seafood", "url");
        product.setId("1");
        when(productApplicationService.getProductById("1")).thenReturn(Optional.of(product));
        
        mockMvc.perform(get("/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lobster"))
                .andExpect(jsonPath("$.description").value("Fresh lobster"))
                .andExpect(jsonPath("$.price").value(49.99))
                .andExpect(jsonPath("$.stock").value(100))
                .andExpect(jsonPath("$.category").value("Seafood"))
                .andExpect(jsonPath("$.imageUrl").value("url"));
    }

    @Test
    void testGetProductByIdNotFound() throws Exception {
        when(productApplicationService.getProductById("999")).thenReturn(Optional.empty());
        
        mockMvc.perform(get("/products/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreateProduct() throws Exception {
        Product product = new Product("Lobster", "Fresh lobster", new BigDecimal("49.99"), 100, "Seafood", "url");
        when(productApplicationService.createProduct(any(), any(), any(), anyInt(), any(), any())).thenReturn(product);
        
        mockMvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Lobster\",\"description\":\"Fresh lobster\",\"price\":49.99,\"stock\":100,\"category\":\"Seafood\",\"imageUrl\":\"url\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lobster"))
                .andExpect(jsonPath("$.price").value(49.99));
    }

    @Test
    void testCreateProductInvalidData() throws Exception {
        // Controller does not validate input, accepts any data
        mockMvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"price\":-10}"))
                .andExpect(status().isOk());
    }

    @Test
    void testFindByCategory() throws Exception {
        Product product = new Product("Lobster", "Fresh lobster", new BigDecimal("49.99"), 100, "Seafood", "url");
        when(productApplicationService.findByCategory("Seafood")).thenReturn(Arrays.asList(product));
        
        mockMvc.perform(get("/products/category/Seafood"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("Seafood"));
    }

    @Test
    void testFindByCategoryNotFound() throws Exception {
        when(productApplicationService.findByCategory("NonExistent")).thenReturn(Collections.emptyList());
        
        mockMvc.perform(get("/products/category/NonExistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testDeleteProduct() throws Exception {
        mockMvc.perform(delete("/products/1"))
                .andExpect(status().isNoContent());
    }

    // ========== 分页和搜索功能测试 ==========

    @Test
    void testGetProductsWithPagination() throws Exception {
        Product product1 = new Product("Lobster", "Fresh lobster", new BigDecimal("49.99"), 100, "Seafood", "url1");
        Product product2 = new Product("Salmon", "Fresh salmon", new BigDecimal("29.99"), 200, "Fish", "url2");
        
        when(productApplicationService.listProducts(any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        Arrays.asList(product1, product2),
                        org.springframework.data.domain.PageRequest.of(0, 10),
                        25
                ));
        
        mockMvc.perform(get("/products")
                        .param("page", "0")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray())
                .andExpect(jsonPath("$.products.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.totalProducts").value(25))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrev").value(false));
    }

    @Test
    void testGetProductsWithCategoryFilter() throws Exception {
        Product product = new Product("Lobster", "Fresh lobster", new BigDecimal("49.99"), 100, "Seafood", "url");
        
        when(productApplicationService.listProducts(any(), eq("Seafood"), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        Arrays.asList(product),
                        org.springframework.data.domain.PageRequest.of(0, 10),
                        1
                ));
        
        mockMvc.perform(get("/products")
                        .param("category", "Seafood")
                        .param("page", "0")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].category").value("Seafood"));
    }

    @Test
    void testGetProductsWithKeywordSearch() throws Exception {
        Product product = new Product("Salmon", "Fresh salmon from Norway", new BigDecimal("29.99"), 200, "Fish", "url");
        
        when(productApplicationService.listProducts(eq("Salmon"), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        Arrays.asList(product),
                        org.springframework.data.domain.PageRequest.of(0, 10),
                        1
                ));
        
        mockMvc.perform(get("/products")
                        .param("keyword", "Salmon")
                        .param("page", "0")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].name").value("Salmon"));
    }

    @Test
    void testGetProductsWithAllParameters() throws Exception {
        Product product = new Product("Salmon", "Fresh salmon", new BigDecimal("29.99"), 200, "Fish", "url");
        
        when(productApplicationService.listProducts(eq("Salmon"), eq("Fish"), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        Arrays.asList(product),
                        org.springframework.data.domain.PageRequest.of(0, 10),
                        1
                ));
        
        mockMvc.perform(get("/products")
                        .param("keyword", "Salmon")
                        .param("category", "Fish")
                        .param("page", "0")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].name").value("Salmon"));
    }

    @Test
    void testGetProductsEmptyResult() throws Exception {
        when(productApplicationService.listProducts(any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        Collections.emptyList(),
                        org.springframework.data.domain.PageRequest.of(0, 10),
                        0
                ));
        
        mockMvc.perform(get("/products")
                        .param("page", "0")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray())
                .andExpect(jsonPath("$.products").isEmpty())
                .andExpect(jsonPath("$.totalProducts").value(0));
    }

    @Test
    void testGetProductsInvalidPageParams() throws Exception {
        // page 为负数应返回 400
        mockMvc.perform(get("/products")
                        .param("page", "-1")
                        .param("pageSize", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetProductsInvalidPageSizeParams() throws Exception {
        // pageSize 为 0 应返回 400
        mockMvc.perform(get("/products")
                        .param("page", "0")
                        .param("pageSize", "0"))
                .andExpect(status().isBadRequest());
    }
}