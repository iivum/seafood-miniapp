package com.seafood.user.interfaces.rest;

import com.seafood.user.application.UserApplicationService;
import com.seafood.user.domain.model.User;
import com.seafood.user.domain.model.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "APIs for user authentication and authorization")
public class AuthController {
    
    private final UserApplicationService userApplicationService;
    private final Map<String, String> tokenStore = new HashMap<>(); // 简单的token存储，生产环境应使用Redis
    private final Map<String, String> verifyCodeStore = new HashMap<>(); // 验证码存储，生产环境应使用Redis

    @PostMapping("/login")
    @Operation(
        summary = "User login",
        description = "Authenticates a user and returns an access token",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Login credentials",
            required = true,
            content = @Content(schema = @Schema(implementation = LoginRequest.class))
        ),
        responses = {
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
        }
    )
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        // 这里应该验证用户凭证，简化版本直接使用openId
        User user = userApplicationService.getUserByOpenId(request.getOpenId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        String token = generateToken();
        tokenStore.put(token, user.getId());
        
        LoginResponse response = new LoginResponse(
            token,
            user.getId(),
            user.getNickname(),
            user.getAvatarUrl(),
            user.getRole().name()
        );
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/wx-login")
    @Operation(
        summary = "WeChat login",
        description = "Login or register using WeChat openId",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "WeChat login data",
            required = true,
            content = @Content(schema = @Schema(implementation = WeChatLoginRequest.class))
        ),
        responses = {
            @ApiResponse(responseCode = "200", description = "Login/Registration successful"),
            @ApiResponse(responseCode = "400", description = "Invalid input data")
        }
    )
    public ResponseEntity<LoginResponse> weChatLogin(@RequestBody WeChatLoginRequest request) {
        // 微信登录/注册逻辑
        User user = userApplicationService.loginOrRegister(
            request.getOpenId(),
            request.getNickname(),
            request.getAvatarUrl()
        );
        
        String token = generateToken();
        tokenStore.put(token, user.getId());
        
        LoginResponse response = new LoginResponse(
            token,
            user.getId(),
            user.getNickname(),
            user.getAvatarUrl(),
            user.getRole().name()
        );
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-code")
    @Operation(
        summary = "Get verification code",
        description = "Sends a verification code to the provided phone number",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Phone number and code type",
            required = true,
            content = @Content(schema = @Schema(implementation = VerifyCodeRequest.class))
        ),
        responses = {
            @ApiResponse(responseCode = "200", description = "Verification code sent successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid phone number"),
            @ApiResponse(responseCode = "429", description = "Too many requests")
        }
    )
    public ResponseEntity<Void> getVerificationCode(@RequestBody VerifyCodeRequest request) {
        String phone = request.getPhone();
        
        // 验证手机号格式
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            return ResponseEntity.badRequest().build();
        }
        
        // 生成6位随机验证码
        String code = String.format("%06d", (int)(Math.random() * 1000000));
        
        // 存储验证码（实际应该设置过期时间）
        verifyCodeStore.put(phone, code);
        
        // 这里应该调用短信服务发送验证码
        // 简化版本直接返回成功
        System.out.println("Verification code for " + phone + ": " + code);
        
        return ResponseEntity.ok().build();
    }

    @PostMapping("/phone-login")
    @Operation(
        summary = "Phone number login/register",
        description = "Login or register using phone number and verification code",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Login data",
            required = true,
            content = @Content(schema = @Schema(implementation = PhoneLoginRequest.class))
        ),
        responses = {
            @ApiResponse(responseCode = "200", description = "Login/Registration successful"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "401", description = "Invalid verification code")
        }
    )
    public ResponseEntity<LoginResponse> phoneLogin(@RequestBody PhoneLoginRequest request) {
        String phone = request.getPhone();
        String verifyCode = request.getVerifyCode();
        String password = request.getPassword();
        String loginMode = request.getLoginMode();
        
        // 验证验证码
        String storedCode = verifyCodeStore.get(phone);
        if (storedCode == null || !storedCode.equals(verifyCode)) {
            return ResponseEntity.status(401).body(null);
        }
        
        // 清除已使用的验证码
        verifyCodeStore.remove(phone);
        
        User user;
        if ("register".equals(loginMode)) {
            // 注册模式
            if (password == null || password.length() < 6) {
                return ResponseEntity.badRequest().build();
            }
            
            // 检查用户是否已存在
            user = userApplicationService.getUserByPhone(phone);
            if (user != null) {
                return ResponseEntity.badRequest().build();
            }
            
            // 创建新用户
            user = new User();
            user.setPhone(phone);
            user.setNickname("用户" + phone.substring(7));
            user.setRole(UserRole.USER);
            user = userApplicationService.createUser(user);
        } else {
            // 登录模式
            user = userApplicationService.getUserByPhone(phone);
            if (user == null) {
                return ResponseEntity.status(401).body(null);
            }
        }
        
        // 生成token
        String token = generateToken();
        tokenStore.put(token, user.getId());
        
        LoginResponse response = new LoginResponse(
            token,
            user.getId(),
            user.getNickname(),
            user.getAvatarUrl(),
            user.getRole().name()
        );
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(
        summary = "User logout",
        description = "Invalidates the user's access token",
        responses = {
            @ApiResponse(responseCode = "200", description = "Logout successful")
        }
    )
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String token) {
        tokenStore.remove(token.replace("Bearer ", ""));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    @Operation(
        summary = "Get current user",
        description = "Returns the currently authenticated user's information",
        responses = {
            @ApiResponse(responseCode = "200", description = "User found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
        }
    )
    public ResponseEntity<UserInfoResponse> getCurrentUser(@RequestHeader("Authorization") String token) {
        String userId = tokenStore.get(token.replace("Bearer ", ""));
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        
        User user = userApplicationService.getUserById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        UserInfoResponse response = new UserInfoResponse(
            user.getId(),
            user.getOpenId(),
            user.getNickname(),
            user.getAvatarUrl(),
            user.getPhone(),
            user.getRole().name()
        );
        
        return ResponseEntity.ok(response);
    }

    private String generateToken() {
        return "TOKEN_" + UUID.randomUUID().toString().replace("-", "");
    }
}