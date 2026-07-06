package com.seafood.user.api;

import com.seafood.shared.security.Role;
import com.seafood.shared.security.UserPrincipal;
import com.seafood.user.api.dto.FavoriteItemResponse;
import com.seafood.user.application.FavoriteService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FavoriteControllerTest {

    private final FavoriteService favoriteService = mock(FavoriteService.class);
    private final FavoriteController controller = new FavoriteController(favoriteService);
    private final UserPrincipal me = new UserPrincipal("u-1", Role.CUSTOMER);

    @Test
    void add_delegatesWithPrincipalId() {
        when(favoriteService.addFavorite("u-1", "p1")).thenReturn(List.of("p1"));

        List<String> result = controller.add("p1", me);

        assertThat(result).containsExactly("p1");
        verify(favoriteService).addFavorite("u-1", "p1");
    }

    @Test
    void remove_delegatesWithPrincipalId() {
        when(favoriteService.removeFavorite("u-1", "p1")).thenReturn(List.of());

        List<String> result = controller.remove("p1", me);

        assertThat(result).isEmpty();
        verify(favoriteService).removeFavorite("u-1", "p1");
    }

    @Test
    void list_delegatesWithPrincipalId() {
        FavoriteItemResponse item = new FavoriteItemResponse("p1", "三文鱼", new BigDecimal("58.00"), "http://img", true);
        when(favoriteService.list("u-1")).thenReturn(List.of(item));

        List<FavoriteItemResponse> result = controller.list(me);

        assertThat(result).containsExactly(item);
    }
}
