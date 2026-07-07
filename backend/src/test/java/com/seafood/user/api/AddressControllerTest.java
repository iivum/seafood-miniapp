package com.seafood.user.api;

import com.seafood.shared.error.NotFoundException;
import com.seafood.shared.security.Role;
import com.seafood.shared.security.UserPrincipal;
import com.seafood.user.api.dto.AddAddressRequest;
import com.seafood.user.api.dto.AddressUpsertRequest;
import com.seafood.user.api.dto.UpdateAddressRequest;
import com.seafood.user.api.dto.UserResponse;
import com.seafood.user.application.UserService;
import com.seafood.user.domain.Address;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AddressController 单元测试 —— self-scoped 门面只做三件事,逐一锁定:
 * <ol>
 *   <li>身份取自 {@code principal.getId()},委托现有 {@link UserService}(不开第二条写路径);</li>
 *   <li>返回从 {@link UserResponse} 解包的 {@code List<Address>}(mp 直接当数组用);</li>
 *   <li>mp 的 {@code district}/{@code detailAddress} 原样透传给 domain 的
 *       {@code district}/{@code detail}(design.md D4,不折叠)。</li>
 * </ol>
 *
 * <p>用纯 Mockito 直测 controller,不走 {@code @WebMvcTest} —— 该 slice 经 controller 调
 * 被注入的 UserService 时,框架注入的实例与 {@code @MockitoBean} 字段不一致(仓内 UserController
 * 同样无 slice 测试)。路由/状态码由 mp e2e 与 UserController 既有约定覆盖。
 */
class AddressControllerTest {

    private final UserService userService = mock(UserService.class);
    private final AddressController controller = new AddressController(userService);
    private final UserPrincipal me = new UserPrincipal("u-1", Role.CUSTOMER);

    private static Address addr(String id, String name, boolean def) {
        return new Address(id, name, "13800000000", "广东省", "深圳市", "南山区", "科技园1号", def);
    }

    private static UserResponse userWith(List<Address> addrs) {
        return new UserResponse("u-1", "openid-1", "昵称", null, "CUSTOMER",
                "13800000000", addrs, Instant.parse("2026-06-20T00:00:00Z"), 0, 0);
    }

    private static AddressUpsertRequest mpBody() {
        return new AddressUpsertRequest("张三", "13800000000", "广东省", "深圳市",
                "南山区", "科技园1号", true);
    }

    @Test
    void list_returnsMyAddressesUnwrapped() {
        when(userService.get("u-1", me))
                .thenReturn(userWith(List.of(addr("a1", "张三", true), addr("a2", "李四", false))));

        assertThat(controller.list(me))
                .extracting(Address::id).containsExactly("a1", "a2");
    }

    @Test
    void getOne_returnsThatAddress() {
        when(userService.get("u-1", me))
                .thenReturn(userWith(List.of(addr("a1", "张三", true), addr("a2", "李四", false))));

        assertThat(controller.get("a2", me).name()).isEqualTo("李四");
    }

    @Test
    void getOne_missing_throwsNotFound() {
        when(userService.get("u-1", me))
                .thenReturn(userWith(List.of(addr("a1", "张三", true))));

        assertThatThrownBy(() -> controller.get("zzz", me))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void add_delegatesWithPrincipalId_andPassesDistrictThroughWithoutFolding() {
        when(userService.addAddress(eq("u-1"), any(), eq(me)))
                .thenReturn(userWith(List.of(addr("a1", "张三", true))));

        List<Address> result = controller.add(mpBody(), me);

        assertThat(result).extracting(Address::id).containsExactly("a1");
        ArgumentCaptor<AddAddressRequest> captor = ArgumentCaptor.forClass(AddAddressRequest.class);
        verify(userService).addAddress(eq("u-1"), captor.capture(), eq(me));
        assertThat(captor.getValue().district()).isEqualTo("南山区");
        assertThat(captor.getValue().detail()).isEqualTo("科技园1号");
    }

    @Test
    void update_delegatesWithPrincipalId_andPassesDistrictThroughWithoutFolding() {
        when(userService.updateAddress(eq("u-1"), eq("a1"), any(), eq(me)))
                .thenReturn(userWith(List.of(addr("a1", "张三改", true))));

        List<Address> result = controller.update("a1", mpBody(), me);

        assertThat(result).extracting(Address::name).containsExactly("张三改");
        ArgumentCaptor<UpdateAddressRequest> captor = ArgumentCaptor.forClass(UpdateAddressRequest.class);
        verify(userService).updateAddress(eq("u-1"), eq("a1"), captor.capture(), eq(me));
        assertThat(captor.getValue().district()).isEqualTo("南山区");
        assertThat(captor.getValue().detail()).isEqualTo("科技园1号");
    }

    @Test
    void remove_delegatesAndReturnsRemaining() {
        when(userService.removeAddress("u-1", "a1", me))
                .thenReturn(userWith(List.of(addr("a2", "李四", true))));

        assertThat(controller.remove("a1", me))
                .extracting(Address::id).containsExactly("a2");
    }

    @Test
    void setDefault_delegatesToUserService() {
        when(userService.setDefaultAddress("u-1", "a2", me))
                .thenReturn(userWith(List.of(addr("a1", "张三", false), addr("a2", "李四", true))));

        controller.setDefault("a2", me);

        verify(userService).setDefaultAddress("u-1", "a2", me);
    }
}
