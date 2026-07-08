package com.seafood.user.api;

import com.seafood.shared.security.Role;
import com.seafood.shared.security.UserPrincipal;
import com.seafood.user.api.dto.PhoneBindRequest;
import com.seafood.user.api.dto.UserResponse;
import com.seafood.user.application.UserService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserPhoneController 单元测试 —— self-scoped 门面(同 {@code AddressController}/
 * {@code FavoriteController} 既有惯例):身份取自 {@code principal.getId()},委托
 * {@link UserService#bindPhone}。鉴权(未登录 401)由 {@code @PreAuthorize("isAuthenticated()")}
 * + 既有 JWT filter 链保证,不在此 slice 里重复验证(同 AddressControllerTest 既有约定)。
 */
class UserPhoneControllerTest {

    private final UserService userService = mock(UserService.class);
    private final UserPhoneController controller = new UserPhoneController(userService);
    private final UserPrincipal me = new UserPrincipal("u-1", Role.CUSTOMER);

    private static UserResponse userWith(String phone) {
        return new UserResponse("u-1", "openid-1", "昵称", null, "CUSTOMER",
                phone, List.of(), Instant.parse("2026-06-20T00:00:00Z"), 0, 0);
    }

    @Test
    void bindPhone_delegatesWithPrincipalIdAndCode() {
        when(userService.bindPhone("u-1", "dev-abc", me)).thenReturn(userWith("13711112222"));

        UserResponse result = controller.bindPhone(new PhoneBindRequest("dev-abc"), me);

        assertThat(result.phone()).isEqualTo("13711112222");
        verify(userService).bindPhone(eq("u-1"), eq("dev-abc"), eq(me));
    }
}
