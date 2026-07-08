package com.seafood.user.api;

import com.seafood.shared.security.Role;
import com.seafood.shared.security.UserPrincipal;
import com.seafood.user.api.dto.ProductViewResponse;
import com.seafood.user.application.ProductViewService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductViewControllerTest {

    private final ProductViewService productViewService = mock(ProductViewService.class);
    private final ProductViewController controller = new ProductViewController(productViewService);
    private final UserPrincipal me = new UserPrincipal("u-1", Role.CUSTOMER);

    @Test
    void record_delegatesWithPrincipalId() {
        controller.record("p1", me);

        verify(productViewService).record("u-1", "p1");
    }

    @Test
    void list_delegatesWithPrincipalId() {
        ProductViewResponse item = new ProductViewResponse("p1", "龙虾", new BigDecimal("128.00"), "http://img", true, Instant.now());
        when(productViewService.list("u-1")).thenReturn(List.of(item));

        List<ProductViewResponse> result = controller.list(me);

        assertThat(result).containsExactly(item);
    }
}
