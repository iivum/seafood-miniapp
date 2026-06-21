package com.seafood.user.api;

import com.seafood.shared.error.NotFoundException;
import com.seafood.shared.security.UserPrincipal;
import com.seafood.user.api.dto.AddressUpsertRequest;
import com.seafood.user.application.UserService;
import com.seafood.user.domain.Address;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 地址 API(self-scoped 门面)。
 *
 * <p>身份取自 JWT principal({@code me.getId()}),所有操作委托 {@link UserService} →
 * {@code User} 聚合,不引入第二条写路径,聚合的"唯一默认"等不变量仍是唯一权威。
 * 返回 {@code List<Address>}(从 UserResponse 解包),与 mp {@code address-list} 直接当数组用对齐。
 */
@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    private final UserService users;

    public AddressController(UserService users) {
        this.users = users;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<Address> list(@AuthenticationPrincipal UserPrincipal me) {
        return users.get(me.getId(), me).addresses();
    }

    @GetMapping("/{addressId}")
    @PreAuthorize("isAuthenticated()")
    public Address get(@PathVariable String addressId, @AuthenticationPrincipal UserPrincipal me) {
        return users.get(me.getId(), me).addresses().stream()
                .filter(a -> a.id().equals(addressId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("地址不存在:" + addressId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public List<Address> add(@Valid @RequestBody AddressUpsertRequest req,
                             @AuthenticationPrincipal UserPrincipal me) {
        return users.addAddress(me.getId(), req.toAddRequest(), me).addresses();
    }

    @PutMapping("/{addressId}")
    @PreAuthorize("isAuthenticated()")
    public List<Address> update(@PathVariable String addressId,
                                @Valid @RequestBody AddressUpsertRequest req,
                                @AuthenticationPrincipal UserPrincipal me) {
        return users.updateAddress(me.getId(), addressId, req.toUpdateRequest(), me).addresses();
    }

    @DeleteMapping("/{addressId}")
    @PreAuthorize("isAuthenticated()")
    public List<Address> remove(@PathVariable String addressId,
                                @AuthenticationPrincipal UserPrincipal me) {
        return users.removeAddress(me.getId(), addressId, me).addresses();
    }

    @PutMapping("/{addressId}/default")
    @PreAuthorize("isAuthenticated()")
    public List<Address> setDefault(@PathVariable String addressId,
                                    @AuthenticationPrincipal UserPrincipal me) {
        return users.setDefaultAddress(me.getId(), addressId, me).addresses();
    }
}
