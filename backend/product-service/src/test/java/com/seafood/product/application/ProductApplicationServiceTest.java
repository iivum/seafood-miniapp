package com.seafood.product.application;

import com.seafood.product.domain.model.Product;
import com.seafood.product.domain.model.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Test class for ProductApplicationService
 * Tests the application layer service operations for product management
 */
@ExtendWith(MockitoExtension.class)
class ProductApplicationServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductApplicationService productApplicationService;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        testProduct = new Product("Fresh Salmon", "Wild caught salmon", new BigDecimal("29.99"), 100, "Fish", "https://example.com/salmon.jpg");
        testProduct.setId("product-1");
    }

    @Test
    void shouldCreateProductSuccessfully() {
        // Arrange
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId("product-new");
            return p;
        });

        // Act
        Product result = productApplicationService.createProduct(
            "Fresh Salmon", "Wild caught salmon", new BigDecimal("29.99"), 100, "Fish", "https://example.com/salmon.jpg"
        );

        // Assert
        assertNotNull(result);
        assertEquals("Fresh Salmon", result.getName());
        assertEquals("Wild caught salmon", result.getDescription());
        assertEquals(new BigDecimal("29.99"), result.getPrice());
        assertEquals(100, result.getStock());
        assertEquals("Fish", result.getCategory());
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void shouldListProductsWithKeywordAndCategory() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        List<Product> products = Arrays.asList(testProduct);
        Page<Product> productPage = new PageImpl<>(products, pageable, 1);
        when(productRepository.findByKeywordAndCategory(eq("salmon"), eq("Fish"), eq(pageable))).thenReturn(productPage);

        // Act
        Page<Product> result = productApplicationService.listProducts("salmon", "Fish", pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Fresh Salmon", result.getContent().get(0).getName());
        verify(productRepository, times(1)).findByKeywordAndCategory(eq("salmon"), eq("Fish"), eq(pageable));
    }

    @Test
    void shouldListProductsWithNullKeyword() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        List<Product> products = Arrays.asList(testProduct);
        Page<Product> productPage = new PageImpl<>(products, pageable, 1);
        when(productRepository.findByKeywordAndCategory(eq(""), eq("Fish"), eq(pageable))).thenReturn(productPage);

        // Act - passing null keyword should be treated as empty string
        Page<Product> result = productApplicationService.listProducts(null, "Fish", pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(productRepository, times(1)).findByKeywordAndCategory(eq(""), eq("Fish"), eq(pageable));
    }

    @Test
    void shouldListProductsWithBlankKeyword() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        List<Product> products = Arrays.asList(testProduct);
        Page<Product> productPage = new PageImpl<>(products, pageable, 1);
        when(productRepository.findByKeywordAndCategory(eq(""), eq("Fish"), eq(pageable))).thenReturn(productPage);

        // Act - blank keyword should be treated as empty string
        Page<Product> result = productApplicationService.listProducts("   ", "Fish", pageable);

        // Assert
        assertNotNull(result);
        verify(productRepository, times(1)).findByKeywordAndCategory(eq(""), eq("Fish"), eq(pageable));
    }

    @Test
    void shouldListProductsWithOldSignature() {
        // Arrange
        List<Product> products = Arrays.asList(testProduct);
        Page<Product> productPage = new PageImpl<>(products, PageRequest.of(0, 10), 1);
        when(productRepository.findByKeywordAndCategory(eq("salmon"), eq("Fish"), any(Pageable.class))).thenReturn(productPage);

        // Act
        Page<Product> result = productApplicationService.listProducts("salmon", "Fish", null, 0, 10);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(productRepository, times(1)).findByKeywordAndCategory(eq("salmon"), eq("Fish"), any(Pageable.class));
    }

    @Test
    void shouldListAllProducts() {
        // Arrange
        List<Product> products = Arrays.asList(testProduct);
        when(productRepository.findAll()).thenReturn(products);

        // Act
        List<Product> result = productApplicationService.listAllProducts();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Fresh Salmon", result.get(0).getName());
        verify(productRepository, times(1)).findAll();
    }

    @Test
    void shouldFindProductsByCategory() {
        // Arrange
        List<Product> products = Arrays.asList(testProduct);
        when(productRepository.findByCategory("Fish")).thenReturn(products);

        // Act
        List<Product> result = productApplicationService.findByCategory("Fish");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Fish", result.get(0).getCategory());
        verify(productRepository, times(1)).findByCategory("Fish");
    }

    @Test
    void shouldGetProductById() {
        // Arrange
        when(productRepository.findById("product-1")).thenReturn(Optional.of(testProduct));

        // Act
        Optional<Product> result = productApplicationService.getProductById("product-1");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("Fresh Salmon", result.get().getName());
        verify(productRepository, times(1)).findById("product-1");
    }

    @Test
    void shouldReturnEmptyWhenProductNotFound() {
        // Arrange
        when(productRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // Act
        Optional<Product> result = productApplicationService.getProductById("nonexistent");

        // Assert
        assertFalse(result.isPresent());
        verify(productRepository, times(1)).findById("nonexistent");
    }

    @Test
    void shouldUpdateProductStockSuccessfully() {
        // Arrange
        when(productRepository.findById("product-1")).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Product result = productApplicationService.updateProductStock("product-1", 50);

        // Assert
        assertNotNull(result);
        verify(productRepository, times(1)).findById("product-1");
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void shouldThrowExceptionWhenUpdatingStockForNonexistentProduct() {
        // Arrange
        when(productRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            productApplicationService.updateProductStock("nonexistent", 50);
        });
        assertEquals("Product not found", exception.getMessage());
        verify(productRepository, times(1)).findById("nonexistent");
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void shouldDeleteProduct() {
        // Arrange
        doNothing().when(productRepository).deleteById("product-1");

        // Act
        productApplicationService.deleteProduct("product-1");

        // Assert
        verify(productRepository, times(1)).deleteById("product-1");
    }
}