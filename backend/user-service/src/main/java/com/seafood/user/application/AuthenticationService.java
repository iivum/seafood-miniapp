package com.seafood.user.application;

import com.seafood.common.security.JwtUtil;
import com.seafood.user.domain.model.TokenRepository;
import com.seafood.user.domain.model.User;
import com.seafood.user.domain.model.UserRepository;
import com.seafood.user.domain.model.WeChatLoginException;
import com.seafood.user.infrastructure.wechat.WxJavaService;
import com.seafood.user.interfaces.rest.LoginResponse;
import lombok.RequiredArgsConstructor;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务
 * 处理用户登录、注册、令牌管理等认证相关逻辑
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AuthenticationService implements UserDetailsService {

    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final JwtUtil jwtUtil;
    private final CustomPasswordEncoder passwordEncoder;
    private final WxJavaService wxJavaService;

    /**
     * 微信登录或注册
     * @param openId 微信OpenID
     * @param nickname 用户昵称
     * @param avatarUrl 用户头像URL
     * @return 用户信息和登录响应
     */
    public LoginResponse weChatLogin(String openId, String nickname, String avatarUrl) {
        User user = userRepository.findByOpenId(openId)
                .orElseGet(() -> {
                    // 用户不存在，创建新用户
                    User newUser = new User();
                    newUser.setOpenId(openId);
                    newUser.setNickname(nickname);
                    newUser.setAvatarUrl(avatarUrl);
                    newUser.setRole(com.seafood.user.domain.model.UserRole.USER); // 设置默认角色
                    return userRepository.save(newUser);
                });

        // 更新用户信息（以防微信信息有变化）
        user.setNickname(nickname);
        user.setAvatarUrl(avatarUrl);
        user = userRepository.save(user);

        return generateLoginResponse(user);
    }

    /**
     * 微信手机号授权登录
     * @param code 微信登录code
     * @param encryptedData 加密手机号
     * @param iv 解密向量
     * @return 登录响应
     */
    public LoginResponse weChatPhoneLogin(String code, String encryptedData, String iv) {
        try {
            // 1. 获取 session_key
            String sessionKey = wxJavaService.getSessionKey(code);

            // 2. 解密手机号
            String phone = wxJavaService.decryptPhoneNumber(sessionKey, encryptedData, iv);

            // 3. 根据手机号查找用户，不存在则创建
            User user = userRepository.findByPhone(phone)
                    .orElseGet(() -> {
                        User newUser = new User();
                        newUser.setPhone(phone);
                        newUser.setNickname("微信用户");
                        newUser.setRole(com.seafood.user.domain.model.UserRole.USER);
                        return userRepository.save(newUser);
                    });

            // 4. 生成 JWT
            return generateLoginResponse(user);

        } catch (WxErrorException e) {
            throw new WeChatLoginException("微信登录失败，请稍后重试", e);
        }
    }

    /**
     * 手机号验证码登录
     * @param phone 手机号
     * @param verifyCode 验证码
     * @return 用户信息和登录响应
     */
    public LoginResponse phoneLogin(String phone, String verifyCode) {
        // 验证码验证逻辑（实际应该从Redis获取）
        // 简化版本：假设验证码正确
        String storedCode = getVerifyCodeFromStore(phone);
        if (storedCode == null || !storedCode.equals(verifyCode)) {
            throw new BadCredentialsException("验证码错误");
        }

        // 清除已使用的验证码
        clearVerifyCode(phone);

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));

        return generateLoginResponse(user);
    }

    /**
     * 手机号注册
     * @param phone 手机号
     * @param verifyCode 验证码
     * @param password 密码
     * @param nickname 昵称
     * @return 用户信息和登录响应
     */
    public LoginResponse phoneRegister(String phone, String verifyCode, String password, String nickname) {
        // 验证码验证
        String storedCode = getVerifyCodeFromStore(phone);
        if (storedCode == null || !storedCode.equals(verifyCode)) {
            throw new BadCredentialsException("验证码错误");
        }

        // 检查用户是否已存在
        if (userRepository.findByPhone(phone).isPresent()) {
            throw new IllegalArgumentException("用户已存在");
        }

        // 验证密码强度
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码长度至少为6位");
        }

        // 创建新用户
        User user = new User();
        user.setPhone(phone);
        user.setNickname(nickname != null ? nickname : "用户" + phone.substring(7));
        user.setPassword(passwordEncoder.encode(password));
        user = userRepository.save(user);

        // 清除已使用的验证码
        clearVerifyCode(phone);

        return generateLoginResponse(user);
    }

    /**
     * 用户名密码登录
     * @param username 用户名（手机号或OpenID）
     * @param password 密码
     * @return 用户信息和登录响应
     */
    public LoginResponse usernamePasswordLogin(String username, String password) {
        User user = userRepository.findById(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));

        // 验证密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("密码错误");
        }

        return generateLoginResponse(user);
    }

    /**
     * 生成登录响应
     * @param user 用户信息
     * @return 登录响应
     */
    public LoginResponse generateLoginResponse(User user) {
        // 生成访问令牌和刷新令牌
        String accessToken = jwtUtil.generateToken(user.getId(), user.getRole().name());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        // 保存令牌到Redis
        tokenRepository.saveAccessToken(accessToken, user.getId());
        tokenRepository.saveRefreshToken(refreshToken, user.getId());

        // 构建响应
        return new LoginResponse(
                accessToken,
                user.getId(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getRole() != null ? user.getRole().name() : "USER",
                refreshToken
        );
    }

    /**
     * 刷新访问令牌
     * @param refreshToken 刷新令牌
     * @return 新的访问令牌
     */
    public String refreshAccessToken(String refreshToken) {
        // 验证刷新令牌
        String userId = tokenRepository.getUserIdByToken(refreshToken);
        if (userId == null) {
            throw new BadCredentialsException("无效的刷新令牌");
        }

        // 获取用户信息
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));

        // 生成新的访问令牌
        String newAccessToken = jwtUtil.generateToken(user.getId(), user.getRole().name());

        // 保存新的访问令牌
        tokenRepository.saveAccessToken(newAccessToken, user.getId());

        return newAccessToken;
    }

    /**
     * 用户登出
     * @param token 访问令牌
     */
    public void logout(String token) {
        // 从令牌获取用户ID
        String userId = tokenRepository.getUserIdByToken(token);
        if (userId != null) {
            // 将令牌加入黑名单
            tokenRepository.blacklistToken(token);
        }
    }

    /**
     * 强制用户登出（删除所有令牌）
     * @param userId 用户ID
     */
    public void forceLogout(String userId) {
        tokenRepository.deleteAllUserTokens(userId);
    }

    /**
     * 验证令牌有效性
     * @param token 访问令牌
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        return tokenRepository.validateToken(token);
    }

    /**
     * 根据用户ID获取用户信息
     * @param userId 用户ID
     * @return 用户信息
     */
    public User getUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
    }

    /**
     * 加载用户详情（Spring Security接口）
     * @param username 用户名
     * @return 用户详情
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findById(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getId())
                .password(user.getPassword())
                .roles(user.getRole().name())
                .build();
    }

    // 以下方法用于验证码管理（简化实现，实际应该使用Redis）

    private String getVerifyCodeFromStore(String phone) {
        // 实际应该从Redis获取
        // 简化实现
        return "123456"; // 假设验证码总是123456
    }

    private void clearVerifyCode(String phone) {
        // 实际应该从Redis清除
        // 简化实现
    }
}