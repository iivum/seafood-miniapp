package com.seafood.user.application;

import com.seafood.shared.error.DomainException;
import com.seafood.shared.error.NotFoundException;
import com.seafood.shared.security.Role;
import com.seafood.shared.security.UserPrincipal;
import com.seafood.user.api.dto.AddAddressRequest;
import com.seafood.user.api.dto.UpdateAddressRequest;
import com.seafood.user.api.dto.UserResponse;
import com.seafood.user.domain.Address;
import com.seafood.user.domain.User;
import com.seafood.user.infra.UserDocument;
import com.seafood.user.infra.UserMapper;
import com.seafood.user.infra.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户服务(参见 specs/backend-api §未直接列出 / specs/auth §End-to-end 用户生命周期)。
 *
 * <p>权限:所有写操作要求操作者是自己(principal.id == userId)或 ADMIN。
 */
@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    // ----- 读 -----

    public UserResponse get(String userId, UserPrincipal caller) {
        authorize(caller, userId, true);
        return UserResponse.from(loadOrThrow(userId));
    }

    public Page<UserResponse> list(Pageable pageable, UserPrincipal caller) {
        requireAdmin(caller);
        Page<UserDocument> page = users.findAll(pageable);
        List<UserResponse> mapped = page.getContent().stream()
                .map(UserMapper::toDomain)
                .map(UserResponse::from)
                .toList();
        return new PageImpl<>(mapped, pageable, page.getTotalElements());
    }

    // ----- 写(自己或 ADMIN)-----

    public UserResponse addAddress(String userId, AddAddressRequest req, UserPrincipal caller) {
        authorize(caller, userId, false);
        User u = loadOrThrow(userId);
        Address newAddr = new Address(null, req.name(), req.phone(),
                req.province(), req.city(), req.detail(), req.isDefault());
        return persistAndReturn(u.addAddress(newAddr));
    }

    public UserResponse updateAddress(String userId, String addressId,
                                      UpdateAddressRequest req, UserPrincipal caller) {
        authorize(caller, userId, false);
        User u = loadOrThrow(userId);
        Address patch = new Address(addressId, req.name(), req.phone(),
                req.province(), req.city(), req.detail(), req.isDefault());
        return persistAndReturn(u.updateAddress(addressId, patch));
    }

    public UserResponse removeAddress(String userId, String addressId, UserPrincipal caller) {
        authorize(caller, userId, false);
        User u = loadOrThrow(userId);
        return persistAndReturn(u.removeAddress(addressId));
    }

    public UserResponse setDefaultAddress(String userId, String addressId, UserPrincipal caller) {
        authorize(caller, userId, false);
        User u = loadOrThrow(userId);
        return persistAndReturn(u.setDefaultAddress(addressId));
    }

    // ----- helpers -----

    private User loadOrThrow(String userId) {
        return users.findById(userId)
                .map(UserMapper::toDomain)
                .orElseThrow(() -> new NotFoundException("用户不存在:" + userId));
    }

    private UserResponse persistAndReturn(User u) {
        UserDocument saved = users.save(UserMapper.toDocument(u));
        return UserResponse.from(UserMapper.toDomain(saved));
    }

    private static void authorize(UserPrincipal caller, String targetUserId, boolean readOnly) {
        if (caller == null) {
            throw new DomainException("未登录");
        }
        if (caller.getRole() == Role.ADMIN) {
            return;
        }
        if (!caller.getId().equals(targetUserId)) {
            throw new DomainException(readOnly ? "无权查看该用户" : "无权操作该用户");
        }
    }

    private static void requireAdmin(UserPrincipal caller) {
        if (caller == null || caller.getRole() != Role.ADMIN) {
            throw new DomainException("仅管理员可访问");
        }
    }
}
