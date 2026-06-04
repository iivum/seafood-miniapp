package com.seafood.user.api;

import com.seafood.shared.security.UserPrincipal;
import com.seafood.user.api.dto.AddAddressRequest;
import com.seafood.user.api.dto.UpdateAddressRequest;
import com.seafood.user.api.dto.UserResponse;
import com.seafood.user.application.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户 API(参见 specs/auth §End-to-end)。
 *
 * <p>地址写操作路径形如 {@code /api/users/{userId}/addresses},其中 {@code userId}
 * 必须等于调用者本人或调用者是 ADMIN — 校验在 {@link UserService#authorize}。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public UserResponse me(@AuthenticationPrincipal UserPrincipal me) {
        return users.get(me.getId(), me);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("isAuthenticated()")
    public UserResponse get(@PathVariable String userId,
                            @AuthenticationPrincipal UserPrincipal me) {
        return users.get(userId, me);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserResponse> list(@PageableDefault(size = 20) Pageable pageable,
                                   @AuthenticationPrincipal UserPrincipal me) {
        return users.list(pageable, me);
    }

    @PostMapping("/{userId}/addresses")
    @PreAuthorize("isAuthenticated()")
    public UserResponse addAddress(@PathVariable String userId,
                                   @Valid @RequestBody AddAddressRequest req,
                                   @AuthenticationPrincipal UserPrincipal me) {
        return users.addAddress(userId, req, me);
    }

    @PutMapping("/{userId}/addresses/{addressId}")
    @PreAuthorize("isAuthenticated()")
    public UserResponse updateAddress(@PathVariable String userId,
                                      @PathVariable String addressId,
                                      @Valid @RequestBody UpdateAddressRequest req,
                                      @AuthenticationPrincipal UserPrincipal me) {
        return users.updateAddress(userId, addressId, req, me);
    }

    @DeleteMapping("/{userId}/addresses/{addressId}")
    @PreAuthorize("isAuthenticated()")
    public UserResponse removeAddress(@PathVariable String userId,
                                      @PathVariable String addressId,
                                      @AuthenticationPrincipal UserPrincipal me) {
        return users.removeAddress(userId, addressId, me);
    }

    @PostMapping("/{userId}/addresses/{addressId}/default")
    @PreAuthorize("isAuthenticated()")
    public UserResponse setDefault(@PathVariable String userId,
                                   @PathVariable String addressId,
                                   @AuthenticationPrincipal UserPrincipal me) {
        return users.setDefaultAddress(userId, addressId, me);
    }
}
