package com.seafood.product.domain.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

class ProductTest {

    @Test
    void testCreateProduct() {
        Product product = new Product("Lobster", "Fresh Atlantic Lobster", new BigDecimal("49.99"), 100, "Seafood", "imageUrl");
        assertEquals("Lobster", product.getName());
        assertEquals(new BigDecimal("49.99"), product.getPrice());
        assertEquals(100, product.getStock());
        assertTrue(product.isOnSale());
    }

    @Test
    void testUpdateStock() {
        Product product = new Product("Lobster", "Fresh Atlantic Lobster", new BigDecimal("49.99"), 100, "Seafood", "imageUrl");
        product.updateStock(50);
        assertEquals(150, product.getStock());
    }

    @Test
    void testReduceStock() {
        Product product = new Product("Lobster", "Fresh Atlantic Lobster", new BigDecimal("49.99"), 100, "Seafood", "imageUrl");
        product.reduceStock(30);
        assertEquals(70, product.getStock());
    }

    @Test
    void testReduceStockThrowsExceptionWhenInsufficient() {
        Product product = new Product("Lobster", "Fresh Atlantic Lobster", new BigDecimal("49.99"), 10, "Seafood", "imageUrl");
        assertThrows(RuntimeException.class, () -> product.reduceStock(20));
    }

    @Test
    void testSetOnSale() {
        Product product = new Product("Lobster", "Fresh Atlantic Lobster", new BigDecimal("49.99"), 100, "Seafood", "imageUrl");
        assertTrue(product.isOnSale());
        product.setOnSale(false);
        assertFalse(product.isOnSale());
    }

    @Test
    void testNoArgsConstructor() {
        Product product = new Product();
        assertNull(product.getName());
        assertNull(product.getPrice());
    }
}
