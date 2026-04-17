package com.seafood.user.interfaces.rest;

import com.seafood.common.security.JwtUtil;
import com.seafood.user.application.AuthenticationService;
import com.seafood.user.application.UserRegistrationService;
import com.seafood.user.domain.model.User;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 限流的认证控制器
 * 使用Resilience4j保护认证端点免受暴力破解攻击
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Rate-Limited Authentication", description = "APIs for user authentication with rate limiting protection")
public class RateLimitedAuthController {

    private final AuthenticationService authenticationService;
    private final UserRegistrationService registrationService;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final JwtUtil jwtUtil;

    // 每种认证类型的限流器
    private final RateLimiter loginRateLimiter;
    private final RateLimiter weChatLoginRateLimiter;
    private final RateLimiter phoneLoginRateLimiter;
    private final RateLimiter verifyCodeRateLimiter;
    private final RateLimiter registerRateLimiter;

    public RateLimitedAuthController(
            AuthenticationService authenticationService,
            UserRegistrationService registrationService,
            RateLimiterRegistry rateLimiterRegistry,
            JwtUtil jwtUtil
    ) {
        this.authenticationService = authenticationService;
        this.registrationService = registrationService;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.jwtUtil = jwtUtil;

        // 配置各种限流器
        this.loginRateLimiter = createRateLimiter("login", 5, Duration.ofMinutes(1));
        this.weChatLoginRateLimiter = createRateLimiter("wechat-login", 10, Duration.ofMinutes(1));
        this.phoneLoginRateLimiter = createRateLimiter("phone-login", 5, Duration.ofMinutes(1));
        this.verifyCodeRateLimiter = createRateLimiter("verify-code", 3, Duration.ofMinutes(1));
        this.registerRateLimiter = createRateLimiter("register", 3, Duration.ofHours(1));
    }

    /**
     * 创建限流器
     * @param name 限流器名称
     * @param requestsPerPeriod 每个周期的请求数
     * @param period 周期时间
     * @return 配置好的限流器
     */
    private RateLimiter createRateLimiter(String name, int requestsPerPeriod, Duration period) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(period)
                .limitForPeriod(requestsPerPeriod)
                .timeoutDuration(Duration.ofMillis(100))
                .build();

        return rateLimiterRegistry.rateLimiter("auth-" + name, config);
    }

    @PostMapping("/login")
    @Operation(
            summary = "用户登录（限流保护）",
            description = "Authenticates a user with rate limiting protection",
            responses = {
                    @ApiResponse(responseCode = "200", description = "登录成功"),
                    @ApiResponse(responseCode = "401", description = "认证失败"),
                    @ApiResponse(responseCode = "429", description = "请求过于频繁，请稍后再试")
            }
    )
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return executeWithRateLimit(loginRateLimiter, () -> {
            LoginResponse response = authenticationService.usernamePasswordLogin(
                    request.getOpenId(),
                    request.getPassword()
            );
            return ResponseEntity.ok(response);
        });
    }

    @PostMapping("/wx-login")
    @Operation(
            summary = "微信登录（限流保护）",
            description = "Login or register using WeChat openId with rate limiting protection",
            responses = {
                    @ApiResponse(responseCode = "200", description = "登录/注册成功"),
                    @ApiResponse(responseCode = "400", description = "无效的输入数据"),
                    @ApiResponse(responseCode = "429", description = "请求过于频繁，请稍后再试")
            }
    )
    public ResponseEntity<LoginResponse> weChatLogin(@RequestBody WeChatLoginRequest request) {
        return executeWithRateLimit(weChatLoginRateLimiter, () -> {
            LoginResponse response = authenticationService.weChatLogin(
                    request.getOpenId(),
                    request.getNickname(),
                    request.getAvatarUrl()
            );
            return ResponseEntity.ok(response);
        });
    }

    @PostMapping("/verify-code")
    @Operation(
            summary = "获取验证码（限流保护）",
            description = "Sends a verification code with rate limiting protection",
            responses = {
                    @ApiResponse(responseCode = "200", description = "验证码发送成功"),
                    @ApiResponse(responseCode = "400", description = "无效的手机号"),
                    @ApiResponse(responseCode = "429", description = "请求过于频繁，请稍后再试")
            }
    )
    public ResponseEntity<Void> getVerificationCode(@RequestBody VerifyCodeRequest request) {
        return executeWithRateLimit(verifyCodeRateLimiter, () -> {
            String phone = request.getPhone();

            // 验证手机号格式
            if (!phone.matches("^1[3-9]\\d{9}$")) {
                return ResponseEntity.badRequest().build();
            }

            // 检查请求频率（基于IP或设备标识）
            String clientIdentifier = getClientIdentifier();
            if (!isVerifyCodeRequestAllowed(clientIdentifier)) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
            }

            // 发送验证码
            boolean sent = registrationService.sendRegistrationVerifyCode(phone);
            if (sent) {
                return ResponseEntity.ok().build();
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        });
    }

    @PostMapping("/phone-login")
    @Operation(
            summary = "手机号登录（限流保护）",
            description = "Login using phone number and verification code with rate limiting protection",
            responses = {
                    @ApiResponse(responseCode = "200", description = "登录成功"),
                    @ApiResponse(responseCode = "400", description = "无效的输入数据"),
                    @ApiResponse(responseCode = "401", description = "验证码错误"),
                    @ApiResponse(responseCode = "429", description = "请求过于频繁，请稍后再试")
            }
    )
    public ResponseEntity<LoginResponse> phoneLogin(@RequestBody PhoneLoginRequest request) {
        return executeWithRateLimit(phoneLoginRateLimiter, () -> {
            LoginResponse response = authenticationService.phoneLogin(
                    request.getPhone(),
                    request.getVerifyCode()
            );
            return ResponseEntity.ok(response);
        });
    }

    @PostMapping("/phone-register")
    @Operation(
            summary = "手机号注册（限流保护）",
            description = "Register using phone number with rate limiting protection",
            responses = {
                    @ApiResponse(responseCode = "200", description = "注册成功"),
                    @ApiResponse(responseCode = "400", description = "无效的输入数据"),
                    @ApiResponse(responseCode = "409", description = "用户已存在"),
                    @ApiResponse(responseCode = "429", description = "请求过于频繁，请稍后再试")
            }
    )
    public ResponseEntity<LoginResponse> phoneRegister(@RequestBody PhoneRegisterRequest request) {
        return executeWithRateLimit(registerRateLimiter, () -> {
            User user = registrationService.registerByPhone(
                    request.getPhone(),
                    request.getPassword(),
                    request.getNickname(),
                    request.getVerifyCode()
            );
            LoginResponse response = new LoginResponse(
                    null, // token will be generated
                    user.getId(),
                    user.getNickname(),
                    user.getAvatarUrl(),
                    user.getRole() != null ? user.getRole().name() : "USER"
            );
            return ResponseEntity.ok(response);
        });
    }

    @PostMapping("/logout")
    @Operation(
            summary = "用户登出",
            description = "Invalidates the user's access token",
            responses = {
                    @ApiResponse(responseCode = "200", description = "登出成功")
            }
    )
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String token) {
        authenticationService.logout(token.replace("Bearer ", ""));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    @Operation(
            summary = "获取当前用户信息",
            description = "Returns the current authenticated user information based on JWT token",
            responses = {
                    @ApiResponse(responseCode = "200", description = "获取成功"),
                    @ApiResponse(responseCode = "401", description = "未授权或Token无效")
            }
    )
    public ResponseEntity<LoginResponse> getCurrentUser(@RequestHeader("Authorization") String token) {
        try {
            String jwt = token.replace("Bearer ", "");
            String userId = jwtUtil.extractUserId(jwt);
            User user = authenticationService.getUserById(userId);
            LoginResponse response = new LoginResponse(
                    null, // accessToken not needed for /me
                    user.getId(),
                    user.getNickname(),
                    user.getAvatarUrl(),
                    user.getRole() != null ? user.getRole().name() : "USER"
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "刷新访问令牌",
            description = "Refresh access token using refresh token",
            responses = {
                    @ApiResponse(responseCode = "200", description = "刷新成功"),
                    @ApiResponse(responseCode = "401", description = "无效的刷新令牌")
            }
    )
    public ResponseEntity<TokenRefreshResponse> refreshToken(@RequestBody TokenRefreshRequest request) {
        String newAccessToken = authenticationService.refreshAccessToken(request.getRefreshToken());
        TokenRefreshResponse response = new TokenRefreshResponse(
                newAccessToken,
                null, // refreshToken - may need to generate new one
                "Bearer",
                86400L
        );
        return ResponseEntity.ok(response);
    }

    /**
     * 使用限流器执行操作
     * @param rateLimiter 限流器
     * @param operation 要执行的操作
     * @param <T> 返回类型
     * @return 操作结果或限流错误响应
     */
    private <T> ResponseEntity<T> executeWithRateLimit(RateLimiter rateLimiter, RateLimitedOperation<T> operation) {
        try {
            // 尝试获取许可
            if (!rateLimiter.acquirePermission()) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header("X-Rate-Limit-Retry-After-Milliseconds",
                                String.valueOf(rateLimiter.getRateLimiterConfig().getTimeoutDuration().toMillis()))
                        .build();
            }

            // 执行操作
            return operation.execute();
        } catch (Exception e) {
            // 处理业务异常
            if (e instanceof IllegalArgumentException || e instanceof org.springframework.security.authentication.BadCredentialsException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            if (e instanceof org.springframework.security.core.userdetails.UsernameNotFoundException) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            // 其他异常返回500
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 限流操作接口
     */
    @FunctionalInterface
    private interface RateLimitedOperation<T> {
        ResponseEntity<T> execute();
    }

    /**
     * 获取客户端标识（用于验证码频率控制）
     * 实际实现应该基于IP地址、设备标识等
     */
    private String getClientIdentifier() {
        // 简化实现：返回固定值
        // 实际应该从请求头或IP地址获取
        return "client-ip";
    }

    /**
     * 检查是否允许发送验证码
     * @param clientIdentifier 客户端标识
     * @return 是否允许
     */
    private boolean isVerifyCodeRequestAllowed(String clientIdentifier) {
        // 简化实现：总是允许
        // 实际应该检查Redis中的请求记录
        return true;
    }
}