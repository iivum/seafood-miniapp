package com.seafood.order.application;

import com.seafood.order.api.dto.OrderResponse;
import com.seafood.order.domain.Cart;
import com.seafood.order.domain.CartItem;
import com.seafood.order.infra.CartDocument;
import com.seafood.order.infra.CartRepository;
import com.seafood.order.infra.OrderDocument;
import com.seafood.order.infra.OrderRepository;
import com.seafood.product.domain.ProductCategory;
import com.seafood.product.domain.ProductStatus;
import com.seafood.product.infra.ProductDocument;
import com.seafood.product.infra.ProductRepository;
import com.seafood.shared.error.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private OrderRepository orderRepo;
    private CartRepository cartRepo;
    private ProductRepository productRepo;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orderRepo = mock(OrderRepository.class);
        cartRepo = mock(CartRepository.class);
        productRepo = mock(ProductRepository.class);
        service = new OrderService(orderRepo, cartRepo, productRepo);
        SecurityContextHolder.clearContext();
    }

    private ProductDocument activeProduct(String id, String name, int stock) {
        ProductDocument d = new ProductDocument();
        d.setId(id);
        d.setName(name);
        d.setPrice(new BigDecimal("50.00"));
        d.setStock(stock);
        d.setCategory("鱼类");
        d.setStatus(ProductStatus.ACTIVE);
        d.setOnSale(true);
        d.setCreatedAt(Instant.now());
        d.setUpdatedAt(Instant.now());
        return d;
    }

    private void loginAs(String userId, com.seafood.shared.security.Role role) {
        var principal = new com.seafood.shared.security.UserPrincipal(userId, role);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void create_snapshotsPriceAndDecrementsStock() {
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        CartDocument cartDoc = new CartDocument();
        cartDoc.setUserId("u1");
        cartDoc.setItems(List.of(new CartItem("p1", 2, true, Instant.now())));
        when(cartRepo.findById("u1")).thenReturn(Optional.of(cartDoc));
        when(productRepo.findAllById(List.of("p1"))).thenReturn(List.of(activeProduct("p1", "三文鱼", 10)));
        when(productRepo.findById("p1")).thenReturn(Optional.of(activeProduct("p1", "三文鱼", 10)));
        when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> {
            OrderDocument d = inv.getArgument(0);
            d.setId("o1");
            return d;
        });

        OrderResponse res = service.create("u1");

        assertThat(res.id()).isEqualTo("o1");
        assertThat(res.totalAmount()).isEqualByComparingTo("100.00");
        assertThat(res.status()).isEqualTo("PENDING");
    }

    @Test
    void create_rejectsInsufficientStock() {
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        CartDocument cartDoc = new CartDocument();
        cartDoc.setUserId("u1");
        cartDoc.setItems(List.of(new CartItem("p1", 5, true, Instant.now())));
        when(cartRepo.findById("u1")).thenReturn(Optional.of(cartDoc));
        when(productRepo.findAllById(List.of("p1"))).thenReturn(List.of(activeProduct("p1", "三文鱼", 2)));
        when(productRepo.findById("p1")).thenReturn(Optional.of(activeProduct("p1", "三文鱼", 2)));

        assertThatThrownBy(() -> service.create("u1"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("库存不足");
    }

    @Test
    void create_rejectsEmptyCart() {
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        when(cartRepo.findById("u1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("u1"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("购物车为空");
    }

    @Test
    void create_marksOutOfStockWhenDepleted() {
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        CartDocument cartDoc = new CartDocument();
        cartDoc.setUserId("u1");
        cartDoc.setItems(List.of(new CartItem("p1", 2, true, Instant.now())));
        when(cartRepo.findById("u1")).thenReturn(Optional.of(cartDoc));
        ProductDocument prod = activeProduct("p1", "三文鱼", 2);
        when(productRepo.findAllById(List.of("p1"))).thenReturn(List.of(prod));
        when(productRepo.findById("p1")).thenReturn(Optional.of(prod));
        when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> {
            OrderDocument d = inv.getArgument(0);
            d.setId("o1");
            return d;
        });

        service.create("u1");

        assertThat(prod.getStock()).isEqualTo(0);
        assertThat(prod.getStatus()).isEqualTo(ProductStatus.OUT_OF_STOCK);
    }

    @Test
    void ship_adminOnlyTransitions() {
        loginAs("admin", com.seafood.shared.security.Role.ADMIN);
        OrderDocument doc = new OrderDocument();
        doc.setId("o1");
        doc.setUserId("u1");
        doc.setItems(List.of(new com.seafood.order.domain.OrderItem("p1", "三文鱼", new BigDecimal("50"), 1)));
        doc.setTotalAmount(new BigDecimal("50"));
        doc.setStatus("PAID");
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        when(orderRepo.findById("o1")).thenReturn(Optional.of(doc));
        when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse res = service.ship("o1");
        assertThat(res.status()).isEqualTo("SHIPPED");
    }

    @Test
    void ship_fromPending_throws() {
        loginAs("admin", com.seafood.shared.security.Role.ADMIN);
        OrderDocument doc = new OrderDocument();
        doc.setId("o1");
        doc.setUserId("u1");
        doc.setItems(List.of());
        doc.setTotalAmount(new BigDecimal("50"));
        doc.setStatus("PENDING");
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        when(orderRepo.findById("o1")).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> service.ship("o1"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void list_customerSeesOnlyOwn() {
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        when(orderRepo.findByUserId(anyString(), any())).thenReturn(org.springframework.data.domain.Page.empty());

        service.list(null, org.springframework.data.domain.PageRequest.of(0, 20));

        // 验证调用了 findByUserId("u1", ...) 而不是 findAll
        org.mockito.Mockito.verify(orderRepo).findByUserId(org.mockito.ArgumentMatchers.eq("u1"), any());
        org.mockito.Mockito.verify(orderRepo, org.mockito.Mockito.never())
                .findAll(any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void list_adminCanSeeAll() {
        loginAs("admin", com.seafood.shared.security.Role.ADMIN);
        when(orderRepo.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.list(null, org.springframework.data.domain.PageRequest.of(0, 20));

        org.mockito.Mockito.verify(orderRepo).findAll(any(org.springframework.data.domain.Pageable.class));
    }
}
