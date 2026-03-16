package com.seafood.product.interfaces.rest;

import com.seafood.product.application.ProductApplicationService;
import com.seafood.product.domain.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock Mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
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
        
        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].name").value("Lobster"))
                .andExpect(jsonPath("$[1].name").value("Salmon"));
    }

    @Test
    void testGetAllProductsEmpty() throws Exception {
        when(productApplicationService.listAllProducts()).thenReturn(Collections.emptyList());
        
        mockMvc.perform(get("/products"))
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
        mockMvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"price\":-10}"))
                .andExpect(status().isBadRequest());
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
}