package com.seafood.order.domain.model;

import com.seafood.order.infrastructure.persistence.MongoCartRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for CartRepository
 * Tests MongoDB persistence operations for Cart
 * Uses Testcontainers to provide a real MongoDB instance
 *
 * Note: These tests require Docker to be running and accessible.
 * If Docker is not available, these tests will fail with
 * "Could not find a valid Docker environment".
 *
 * To run these tests:
 * 1. Ensure Docker Desktop is running
 * 2. Ensure testcontainers can access Docker (check ~/.testcontainers.properties)
 * 3. MongoDB images must be cached locally (docker pull mongo:6)
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class CartRepositoryTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:6"))
            .withExposedPorts(27017);

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private MongoCartRepository cartRepository;

    private Cart testCart;
    private CartItem testItem;

    @BeforeEach
    void setUp() {
        // Clean up before each test
        cartRepository.deleteAll();

        // Create test cart with items
        testCart = new Cart("test-user-123");
        testItem = new CartItem("product-1", "Fresh Salmon", 29.99, 2, "https://example.com/salmon.jpg");
        testCart.addItem(testItem);
    }

    @Test
    void shouldSaveCartSuccessfully() {
        // Act
        Cart savedCart = cartRepository.save(testCart);

        // Assert
        assertNotNull(savedCart);
        assertEquals(testCart.getId(), savedCart.getId());
        assertEquals(testCart.getUserId(), savedCart.getUserId());
        assertEquals(testCart.getItems().size(), savedCart.getItems().size());
        assertEquals(testCart.getTotalPrice(), savedCart.getTotalPrice());
    }

    @Test
    void shouldFindCartByUserIdSuccessfully() {
        // Arrange
        cartRepository.save(testCart);

        // Act
        Optional<Cart> foundCart = cartRepository.findByUserId("test-user-123");

        // Assert
        assertTrue(foundCart.isPresent());
        assertEquals(testCart.getId(), foundCart.get().getId());
        assertEquals(1, foundCart.get().getItems().size());
    }

    @Test
    void shouldReturnEmptyWhenCartNotFound() {
        // Act
        Optional<Cart> foundCart = cartRepository.findByUserId("non-existent-user");

        // Assert
        assertFalse(foundCart.isPresent());
    }

    @Test
    void shouldUpdateCartSuccessfully() {
        // Arrange
        cartRepository.save(testCart);

        // Act - Add another item
        CartItem newItem = new CartItem("product-2", "Fresh Tuna", 39.99, 1, "https://example.com/tuna.jpg");
        testCart.addItem(newItem);
        Cart updatedCart = cartRepository.save(testCart);

        // Assert
        assertEquals(2, updatedCart.getItems().size());
        assertEquals(99.97, updatedCart.getTotalPrice());
    }

    @Test
    void shouldDeleteCartSuccessfully() {
        // Arrange
        cartRepository.save(testCart);

        // Act
        cartRepository.delete(testCart);

        // Assert
        Optional<Cart> foundCart = cartRepository.findByUserId("test-user-123");
        assertFalse(foundCart.isPresent());
    }

    @Test
    void shouldDeleteAllCarts() {
        // Arrange
        Cart anotherCart = new Cart("another-user-456");
        cartRepository.save(testCart);
        cartRepository.save(anotherCart);

        // Act
        cartRepository.deleteAll();

        // Assert
        assertEquals(0, cartRepository.count());
    }

    @Test
    void shouldHandleConcurrentUpdates() {
        // Arrange
        cartRepository.save(testCart);

        // Simulate concurrent update by fetching, modifying, and saving
        Optional<Cart> cart1 = cartRepository.findByUserId("test-user-123");
        Optional<Cart> cart2 = cartRepository.findByUserId("test-user-123");

        assertTrue(cart1.isPresent());
        assertTrue(cart2.isPresent());

        // Modify cart1
        cart1.get().updateItemQuantity("product-1", 5);
        cartRepository.save(cart1.get());

        // Modify cart2 (simulating stale data)
        cart2.get().updateItemQuantity("product-1", 3);
        Cart savedCart = cartRepository.save(cart2.get());

        // The final state should reflect the last save
        assertEquals(3, savedCart.getItems().get(0).getQuantity());
    }
}