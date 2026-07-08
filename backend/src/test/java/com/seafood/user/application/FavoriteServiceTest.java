package com.seafood.user.application;

import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.application.ProductService;
import com.seafood.shared.error.NotFoundException;
import com.seafood.shared.security.Role;
import com.seafood.user.api.dto.FavoriteItemResponse;
import com.seafood.user.domain.User;
import com.seafood.user.infra.UserDocument;
import com.seafood.user.infra.UserMapper;
import com.seafood.user.infra.UserRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FavoriteServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final ProductService productService = mock(ProductService.class);
    private final FavoriteService favorites = new FavoriteService(users, productService);

    private User sampleUser(List<String> favoriteProductIds) {
        return new User("u1", "open-1", "nick", null, Role.CUSTOMER, null,
                List.of(), favoriteProductIds, Instant.parse("2026-07-01T00:00:00Z"));
    }

    private void stubLoad(User u) {
        when(users.findById("u1")).thenReturn(Optional.of(UserMapper.toDocument(u)));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void addFavorite_persistsAndReturnsUpdatedList() {
        stubLoad(sampleUser(List.of()));

        List<String> result = favorites.addFavorite("u1", "p1");

        assertThat(result).containsExactly("p1");
    }

    @Test
    void addFavorite_alreadyFavorited_isNoOp_returnsUnchanged() {
        stubLoad(sampleUser(List.of("p1")));

        List<String> result = favorites.addFavorite("u1", "p1");

        assertThat(result).containsExactly("p1");
    }

    @Test
    void removeFavorite_removesFromList() {
        stubLoad(sampleUser(List.of("p1", "p2")));

        List<String> result = favorites.removeFavorite("u1", "p1");

        assertThat(result).containsExactly("p2");
    }

    @Test
    void list_enrichesWithProductInfo() {
        stubLoad(sampleUser(List.of("p1")));
        when(productService.get("p1")).thenReturn(
                new ProductResponse("p1", "三文鱼", "desc", new BigDecimal("58.00"), 10,
                        "鱼类", "http://img/p1.png", com.seafood.product.domain.ProductStatus.ACTIVE,
                        Instant.now(), Instant.now()));

        List<FavoriteItemResponse> result = favorites.list("u1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).productId()).isEqualTo("p1");
        assertThat(result.get(0).productName()).isEqualTo("三文鱼");
        assertThat(result.get(0).available()).isTrue();
    }

    @Test
    void list_unavailableProduct_degradesGracefully() {
        stubLoad(sampleUser(List.of("p-gone")));
        when(productService.get("p-gone")).thenThrow(new NotFoundException("商品不存在:p-gone"));

        List<FavoriteItemResponse> result = favorites.list("u1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).productName()).isEqualTo("商品已下架");
        assertThat(result.get(0).available()).isFalse();
    }

    @Test
    void addFavorite_userNotFound_throws() {
        when(users.findById("nope")).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> favorites.addFavorite("nope", "p1"))
                .isInstanceOf(com.seafood.shared.error.NotFoundException.class);
    }
}
