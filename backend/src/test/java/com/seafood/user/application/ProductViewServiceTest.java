package com.seafood.user.application;

import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.application.ProductService;
import com.seafood.shared.error.NotFoundException;
import com.seafood.user.api.dto.ProductViewResponse;
import com.seafood.user.infra.ProductViewDocument;
import com.seafood.user.infra.ProductViewRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ProductViewServiceTest {

    private final ProductViewRepository views = mock(ProductViewRepository.class);
    private final ProductService productService = mock(ProductService.class);
    private final ProductViewService service = new ProductViewService(views, productService);

    private ProductViewDocument doc(String userId, String productId, Instant viewedAt) {
        ProductViewDocument d = new ProductViewDocument();
        d.setId(productId + "-doc");
        d.setUserId(userId);
        d.setProductId(productId);
        d.setViewedAt(viewedAt);
        return d;
    }

    @Test
    void record_newProduct_insertsDocument() {
        when(views.findByUserIdAndProductId("u1", "p1")).thenReturn(Optional.empty());
        when(views.findByUserIdOrderByViewedAtDesc("u1")).thenReturn(List.of());

        service.record("u1", "p1");

        ArgumentCaptor<ProductViewDocument> captor = ArgumentCaptor.forClass(ProductViewDocument.class);
        verify(views).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("u1");
        assertThat(captor.getValue().getProductId()).isEqualTo("p1");
    }

    @Test
    void record_existingProduct_refreshesViewedAt_doesNotDuplicate() {
        ProductViewDocument existing = doc("u1", "p1", Instant.parse("2026-07-01T00:00:00Z"));
        when(views.findByUserIdAndProductId("u1", "p1")).thenReturn(Optional.of(existing));
        when(views.findByUserIdOrderByViewedAtDesc("u1")).thenReturn(List.of(existing));

        service.record("u1", "p1");

        ArgumentCaptor<ProductViewDocument> captor = ArgumentCaptor.forClass(ProductViewDocument.class);
        verify(views).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(existing.getId());
        assertThat(captor.getValue().getViewedAt()).isAfter(Instant.parse("2026-07-01T00:00:00Z"));
        verify(views, times(1)).save(any());
    }

    @Test
    void record_prunesBeyond100_deletesOldest() {
        when(views.findByUserIdAndProductId("u1", "p101")).thenReturn(Optional.empty());
        List<ProductViewDocument> existing101 = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            existing101.add(doc("u1", "p" + i, Instant.parse("2026-07-01T00:00:00Z").plusSeconds(i)));
        }
        // findByUserIdOrderByViewedAtDesc 按倒序返回 —— 最新在前
        List<ProductViewDocument> sortedDesc = new ArrayList<>(existing101);
        java.util.Collections.reverse(sortedDesc);
        when(views.findByUserIdOrderByViewedAtDesc("u1")).thenReturn(sortedDesc);

        service.record("u1", "p101");

        verify(views).deleteAll(anyList());
    }

    @Test
    void list_enrichesWithProductInfo_sortedByViewedAtDesc() {
        ProductViewDocument d1 = doc("u1", "p1", Instant.parse("2026-07-02T00:00:00Z"));
        when(views.findByUserIdOrderByViewedAtDesc("u1")).thenReturn(List.of(d1));
        when(productService.get("p1")).thenReturn(
                new ProductResponse("p1", "龙虾", "desc", new BigDecimal("128.00"), 5,
                        "虾蟹", "http://img/p1.png", com.seafood.product.domain.ProductStatus.ACTIVE,
                        Instant.now(), Instant.now()));

        List<ProductViewResponse> result = service.list("u1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).productName()).isEqualTo("龙虾");
        assertThat(result.get(0).available()).isTrue();
    }

    @Test
    void list_unavailableProduct_degradesGracefully() {
        ProductViewDocument d1 = doc("u1", "p-gone", Instant.now());
        when(views.findByUserIdOrderByViewedAtDesc("u1")).thenReturn(List.of(d1));
        when(productService.get("p-gone")).thenThrow(new NotFoundException("商品不存在:p-gone"));

        List<ProductViewResponse> result = service.list("u1");

        assertThat(result.get(0).productName()).isEqualTo("商品已下架");
        assertThat(result.get(0).available()).isFalse();
    }

    @Test
    void countForUser_delegatesToRepository() {
        when(views.countByUserId("u1")).thenReturn(3L);

        assertThat(service.countForUser("u1")).isEqualTo(3L);
    }
}
