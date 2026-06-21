package com.seafood.banner.application;

import com.seafood.banner.api.dto.BannerRequest;
import com.seafood.banner.api.dto.BannerResponse;
import com.seafood.banner.domain.BannerStatus;
import com.seafood.banner.domain.BannerTone;
import com.seafood.banner.infra.BannerDocument;
import com.seafood.banner.infra.BannerRepository;
import com.seafood.product.application.ProductService;
import com.seafood.shared.error.DomainException;
import com.seafood.shared.error.NotFoundException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BannerServiceTest {

    private final BannerRepository repo = mock(BannerRepository.class);
    private final ProductService productService = mock(ProductService.class);
    private final BannerService service = new BannerService(repo, productService);

    private static BannerDocument doc(String id, BannerStatus status, int sortOrder) {
        BannerDocument d = new BannerDocument();
        d.setId(id);
        d.setTone(BannerTone.ACCENT);
        d.setEmoji("🦞");
        d.setTitle("t-" + id);
        d.setSubtitle("sub");
        d.setSortOrder(sortOrder);
        d.setStatus(status);
        d.setCreatedAt(Instant.now());
        d.setUpdatedAt(Instant.now());
        return d;
    }

    private static BannerRequest req(String targetProductId) {
        return new BannerRequest(BannerTone.ACCENT, "🦞", "波龙季", "鲜活到岸",
                targetProductId, 0, true);
    }

    @Test
    void listActive_returnsOnlyActive_orderedBySortOrder() {
        when(repo.findByStatusOrderBySortOrderAsc(BannerStatus.ACTIVE))
                .thenReturn(List.of(doc("b1", BannerStatus.ACTIVE, 0), doc("b2", BannerStatus.ACTIVE, 1)));

        List<BannerResponse> out = service.listActive();

        assertThat(out).extracting(BannerResponse::id).containsExactly("b1", "b2");
        verify(repo).findByStatusOrderBySortOrderAsc(BannerStatus.ACTIVE);
    }

    @Test
    void listAll_returnsAllIncludingInactive() {
        when(repo.findAllByOrderBySortOrderAsc())
                .thenReturn(List.of(doc("b1", BannerStatus.ACTIVE, 0), doc("b2", BannerStatus.INACTIVE, 1)));

        List<BannerResponse> out = service.listAll();

        assertThat(out).extracting(BannerResponse::status)
                .containsExactly(BannerStatus.ACTIVE, BannerStatus.INACTIVE);
    }

    @Test
    void create_withValidTargetProduct_validatesAndSaves() {
        when(productService.get("p-1")).thenReturn(null); // 不抛即视为存在
        when(repo.save(any())).thenAnswer(i -> {
            BannerDocument d = i.getArgument(0);
            d.setId("b-new");
            return d;
        });

        BannerResponse out = service.create(req("p-1"));

        assertThat(out.id()).isEqualTo("b-new");
        assertThat(out.status()).isEqualTo(BannerStatus.ACTIVE);
        verify(productService).get("p-1");
    }

    @Test
    void create_withMissingTargetProduct_rejected() {
        when(productService.get("ghost")).thenThrow(new NotFoundException("商品不存在"));

        assertThatThrownBy(() -> service.create(req("ghost")))
                .isInstanceOf(DomainException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void create_withNullTarget_skipsValidation() {
        when(repo.save(any())).thenAnswer(i -> {
            BannerDocument d = i.getArgument(0);
            d.setId("b-new");
            return d;
        });

        service.create(req(null));

        verify(productService, never()).get(any());
    }

    @Test
    void get_missing_throwsNotFound() {
        when(repo.findById("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get("ghost")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_missing_throwsNotFound() {
        when(repo.existsById("ghost")).thenReturn(false);
        assertThatThrownBy(() -> service.delete("ghost")).isInstanceOf(NotFoundException.class);
        verify(repo, never()).deleteById(eq("ghost"));
    }

    @Test
    void update_inactiveFlag_deactivates() {
        when(repo.findById("b1")).thenReturn(Optional.of(doc("b1", BannerStatus.ACTIVE, 0)));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        BannerRequest inactive = new BannerRequest(BannerTone.SOFT, "🐟", "t", "s", null, 2, false);
        BannerResponse out = service.update("b1", inactive);

        assertThat(out.status()).isEqualTo(BannerStatus.INACTIVE);
        assertThat(out.tone()).isEqualTo(BannerTone.SOFT);
    }
}
