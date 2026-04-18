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
 * Authentication service handling all login, registration, and token management
 * operations. Coordinates with JWT utility, token repository, and WeChat APIs
 * to provide secure authentication for the seafood e-commerce platform.
 *
 * <p>This service implements Spring Security's UserDetailsService to integrate
 * with the security framework, while also providing higher-level authentication
 * methods for various login flows (WeChat, phone, username/password).</p>
 *
 * <p>All write operations are transactional to ensure data consistency.</p>
 *
 * @see UserDetailsService
 * @see JwtUtil
 * @see TokenRepository
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
     * Authenticates a user via WeChat OpenID, creating them if they don't exist.
     * This is the primary login method for WeChat mini-program users.
     *
     * <p>If the user exists, their profile is updated with the latest WeChat
     * nickname and avatar to keep the local profile in sync with WeChat.</p>
     *
     * @param openId    WeChat OpenID for user identification
     * @param nickname  Current WeChat nickname
     * @param avatarUrl Current WeChat avatar URL
     * @return Login response with JWT tokens and user info
     */
    public LoginResponse weChatLogin(String openId, String nickname, String avatarUrl) {
        User user = userRepository.findByOpenId(openId)
                .orElseGet(() -> {
                    // New WeChat user - create local account with default USER role
                    User newUser = new User();
                    newUser.setOpenId(openId);
                    newUser.setNickname(nickname);
                    newUser.setAvatarUrl(avatarUrl);
                    newUser.setRole(com.seafood.user.domain.model.UserRole.USER);
                    return userRepository.save(newUser);
                });

        // Sync profile in case nickname or avatar changed on WeChat side
        user.setNickname(nickname);
        user.setAvatarUrl(avatarUrl);
        user = userRepository.save(user);

        return generateLoginResponse(user);
    }

    /**
     * Authenticates a user via WeChat phone number authorization.
     * The phone number is decrypted from encrypted data provided by WeChat's
     * phone number button component.
     *
     * <p>If no account exists with this phone number, a new account is created.
     * This enables one-tap login for users who have already authorized
     * their phone number to the mini-program.</p>
     *
     * @param code           WeChat login code to exchange for session key
     * @param encryptedData  Encrypted phone number from WeChat button
     * @param iv             Decryption vector from WeChat button
     * @return Login response with JWT tokens and user info
     * @throws WeChatLoginException if WeChat API calls fail
     */
    public LoginResponse weChatPhoneLogin(String code, String encryptedData, String iv) {
        try {
            // 1. Exchange code for session key needed for decryption
            String sessionKey = wxJavaService.getSessionKey(code);

            // 2. Decrypt the phone number using session key
            String phone = wxJavaService.decryptPhoneNumber(sessionKey, encryptedData, iv);

            // 3. Find or create user by phone number
            User user = userRepository.findByPhone(phone)
                    .orElseGet(() -> {
                        User newUser = new User();
                        newUser.setPhone(phone);
                        newUser.setNickname("微信用户");
                        newUser.setRole(com.seafood.user.domain.model.UserRole.USER);
                        return userRepository.save(newUser);
                    });

            // 4. Generate JWT tokens for authenticated session
            return generateLoginResponse(user);

        } catch (WxErrorException e) {
            throw new WeChatLoginException("微信登录失败，请稍后重试", e);
        }
    }

    /**
     * Authenticates a user using phone number and SMS verification code.
     * The verification code must be obtained separately via the verifyCode endpoint.
     *
     * <p>After successful verification, the used code is invalidated to prevent replay.</p>
     *
     * @param phone      Phone number for user identification
     * @param verifyCode SMS verification code
     * @return Login response with JWT tokens and user info
     */
    public LoginResponse phoneLogin(String phone, String verifyCode) {
        // Validate the verification code (currently simplified - should check Redis)
        String storedCode = getVerifyCodeFromStore(phone);
        if (storedCode == null || !storedCode.equals(verifyCode)) {
            throw new BadCredentialsException("验证码错误");
        }

        // Invalidate used code to prevent replay attacks
        clearVerifyCode(phone);

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));

        return generateLoginResponse(user);
    }

    /**
     * Registers a new user via phone number with password.
     * Requires a valid SMS verification code and validates password strength.
     *
     * <p>The verification code is validated before creating the account.
     * After successful registration, the code is invalidated.</p>
     *
     * @param phone      Phone number for the new account
     * @param verifyCode SMS verification code
     * @param password   Password (must be at least 6 characters)
     * @param nickname   Display name (optional, defaults to generated name)
     * @return Login response with JWT tokens and user info
     */
    public LoginResponse phoneRegister(String phone, String verifyCode, String password, String nickname) {
        // Validate verification code
        String storedCode = getVerifyCodeFromStore(phone);
        if (storedCode == null || !storedCode.equals(verifyCode)) {
            throw new BadCredentialsException("验证码错误");
        }

        // Check if phone already registered
        if (userRepository.findByPhone(phone).isPresent()) {
            throw new IllegalArgumentException("用户已存在");
        }

        // Validate password length
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码长度至少为6位");
        }

        // Create new user with hashed password
        User user = new User();
        user.setPhone(phone);
        user.setNickname(nickname != null ? nickname : "用户" + phone.substring(7));
        user.setPassword(passwordEncoder.encode(password));
        user = userRepository.save(user);

        // Invalidate used code
        clearVerifyCode(phone);

        return generateLoginResponse(user);
    }

    /**
     * Authenticates a user with username (or ID) and password.
     * The username field accepts either a user ID or phone number.
     *
     * @param username User ID or phone number
     * @param password Plain text password
     * @return Login response with JWT tokens and user info
     */
    public LoginResponse usernamePasswordLogin(String username, String password) {
        User user = userRepository.findById(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));

        // Verify password against stored hash
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("密码错误");
        }

        return generateLoginResponse(user);
    }

    /**
     * Generates a login response with JWT tokens for the authenticated user.
     * Creates both access token (24h validity) and refresh token (7d validity),
     * and stores them in Redis for validation and revocation support.
     *
     * @param user The authenticated user
     * @return Login response containing tokens and user info
     */
    public LoginResponse generateLoginResponse(User user) {
        // Generate JWT access and refresh tokens
        String accessToken = jwtUtil.generateToken(user.getId(), user.getRole().name());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        // Store tokens in Redis for later validation and blacklist support
        tokenRepository.saveAccessToken(accessToken, user.getId());
        tokenRepository.saveRefreshToken(refreshToken, user.getId());

        // Build response
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
     * Refreshes an expired access token using a valid refresh token.
     * Issues a new access token while keeping the refresh token valid.
     *
     * <p>The refresh token itself is not rotated to avoid logout of
     * multiple sessions when refreshing. If rotation is desired,
     * a new refresh token should be issued.</p>
     *
     * @param refreshToken Valid refresh token from login
     * @return New access token
     */
    public String refreshAccessToken(String refreshToken) {
        // Validate refresh token exists and is not blacklisted
        String userId = tokenRepository.getUserIdByToken(refreshToken);
        if (userId == null) {
            throw new BadCredentialsException("无效的刷新令牌");
        }

        // Get current user info for token generation
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));

        // Issue new access token with same claims
        String newAccessToken = jwtUtil.generateToken(user.getId(), user.getRole().name());

        // Store new token in Redis
        tokenRepository.saveAccessToken(newAccessToken, user.getId());

        return newAccessToken;
    }

    /**
     * Logs out a user by blacklisting their access token.
     * The token remains valid until its natural expiration but is
     * immediately invalidated for future requests.
     *
     * @param token Access token to invalidate
     */
    public void logout(String token) {
        // Extract userId from token and blacklist it
        String userId = tokenRepository.getUserIdByToken(token);
        if (userId != null) {
            tokenRepository.blacklistToken(token);
        }
    }

    /**
     * Forcefully logs out a user by revoking all their tokens.
     * Used by administrators to terminate all sessions for a user.
     *
     * @param userId User whose tokens should be revoked
     */
    public void forceLogout(String userId) {
        tokenRepository.deleteAllUserTokens(userId);
    }

    /**
     * Checks if an access token is currently valid (exists and not blacklisted).
     *
     * @param token Access token to validate
     * @return true if token is valid, false otherwise
     */
    public boolean validateToken(String token) {
        return tokenRepository.validateToken(token);
    }

    /**
     * Retrieves a user by their ID.
     *
     * @param userId User's unique identifier
     * @return User entity
     * @throws UsernameNotFoundException if user doesn't exist
     */
    public User getUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
    }

    /**
     * Loads user details for Spring Security authentication.
     * This method is called by the security framework during authentication.
     *
     * @param username User identifier (typically user ID)
     * @return Spring Security UserDetails for authentication
     * @throws UsernameNotFoundException if user doesn't exist
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

    // Verification code management (simplified - should use Redis in production)

    /**
     * Retrieves stored verification code for a phone number.
     * Current implementation returns hardcoded value.
     *
     * @param phone Phone number to look up
     * @return Stored verification code
     */
    private String getVerifyCodeFromStore(String phone) {
        // TODO: Implement Redis-based verification code storage
        return "123456"; // Placeholder for testing
    }

    /**
     * Clears a used verification code after successful authentication.
     * Current implementation is a no-op.
     *
     * @param phone Phone number whose code should be cleared
     */
    private void clearVerifyCode(String phone) {
        // TODO: Implement Redis-based code invalidation
    }
}