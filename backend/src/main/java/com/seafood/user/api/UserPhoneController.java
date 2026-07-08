package com.seafood.user.api;

import com.seafood.shared.security.UserPrincipal;
import com.seafood.user.api.dto.PhoneBindRequest;
import com.seafood.user.api.dto.UserResponse;
import com.seafood.user.application.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 手机号绑定 API(self-scoped 门面——身份取自 JWT principal,不接受外部 userId 参数)。
 * 参见 specs/backend-api §Customer phone number binding。
 */
@RestController
@RequestMapping("/api/users/me")
public class UserPhoneController {

    private final UserService users;

    public UserPhoneController(UserService users) {
        this.users = users;
    }

    @PatchMapping("/phone")
    @PreAuthorize("isAuthenticated()")
    public UserResponse bindPhone(@Valid @RequestBody PhoneBindRequest req,
                                  @AuthenticationPrincipal UserPrincipal me) {
        return users.bindPhone(me.getId(), req.code(), me);
    }
}
