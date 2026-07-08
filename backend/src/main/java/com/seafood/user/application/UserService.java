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
    private final ProductViewService productViews;
    private final WechatPhoneNumberExchanger phoneExchanger;

    public UserService(UserRepository users, ProductViewService productViews,
                       WechatPhoneNumberExchanger phoneExchanger) {
        this.users = users;
        this.productViews = productViews;
        this.phoneExchanger = phoneExchanger;
    }

    // ----- 读 -----

    public UserResponse get(String userId, UserPrincipal caller) {
        authorize(caller, userId, true);
        User u = loadOrThrow(userId);
        return UserResponse.from(u, productViews.countForUser(userId));
    }

    public Page<UserResponse> list(Pageable pageable, UserPrincipal caller) {
        requireAdmin(caller);
        Page<UserDocument> page = users.findAll(pageable);
        List<UserResponse> mapped = page.getContent().stream()
                .map(UserMapper::toDomain)
                .map(u -> UserResponse.from(u, productViews.countForUser(u.id())))
                .toList();
        return new PageImpl<>(mapped, pageable, page.getTotalElements());
    }

    // ----- 写(自己或 ADMIN)-----

    public UserResponse addAddress(String userId, AddAddressRequest req, UserPrincipal caller) {
        authorize(caller, userId, false);
        User u = loadOrThrow(userId);
        Address newAddr = new Address(null, req.name(), req.phone(),
                req.province(), req.city(), req.district(), req.detail(), req.isDefault());
        return persistAndReturn(u.addAddress(newAddr));
    }

    public UserResponse updateAddress(String userId, String addressId,
                                      UpdateAddressRequest req, UserPrincipal caller) {
        authorize(caller, userId, false);
        User u = loadOrThrow(userId);
        Address patch = new Address(addressId, req.name(), req.phone(),
                req.province(), req.city(), req.district(), req.detail(), req.isDefault());
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

    // ----- 手机号绑定 -----

    /**
     * 先换号再加载用户:code 无效/过期时不必浪费一次 DB 读(code review 效率发现)。
     * authorize 与其它写操作一致——今天调用方(UserPhoneController)只会传 me.getId(),
     * 但服务层仍应独立守住"自己或 ADMIN"不变量,不依赖调用方永远正确传参。
     */
    public UserResponse bindPhone(String userId, String code, UserPrincipal caller) {
        authorize(caller, userId, false);
        String phone = phoneExchanger.exchange(code);
        User u = loadOrThrow(userId);
        return persistAndReturn(u.bindPhone(phone));
    }

    // ----- helpers -----

    private User loadOrThrow(String userId) {
        return users.findById(userId)
                .map(UserMapper::toDomain)
                .orElseThrow(() -> new NotFoundException("用户不存在:" + userId));
    }

    private UserResponse persistAndReturn(User u) {
        UserDocument saved = users.save(UserMapper.toDocument(u));
        User reloaded = UserMapper.toDomain(saved);
        return UserResponse.from(reloaded, productViews.countForUser(reloaded.id()));
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
