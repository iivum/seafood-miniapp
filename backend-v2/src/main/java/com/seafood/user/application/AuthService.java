package com.seafood.user.application;

import com.seafood.shared.config.AdminBootstrap;
import com.seafood.shared.error.DomainException;
import com.seafood.shared.error.ErrorCode;
import com.seafood.shared.error.NotFoundException;
import com.seafood.shared.security.JwtTokenProvider;
import com.seafood.shared.security.Role;
import com.seafood.user.api.dto.AuthResponse;
import com.seafood.user.api.dto.LoginRequest;
import com.seafood.user.api.dto.RefreshRequest;
import com.seafood.user.api.dto.RegisterRequest;
import com.seafood.user.domain.User;
import com.seafood.user.infra.UserMongoRepository;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserMongoRepository users;
    private final PasswordEncoder encoder;
    private final JwtTokenProvider jwt;
    private final AdminBootstrap adminBootstrap;

    public AuthService(UserMongoRepository users, PasswordEncoder encoder, JwtTokenProvider jwt, AdminBootstrap adminBootstrap) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.adminBootstrap = adminBootstrap;
    }

    @PostConstruct
    public void seedAdmin() {
        String adminUser = adminBootstrap.bootstrapUsername();
        if (adminUser == null || users.existsByUsername(adminUser)) {
            return;
        }
        User admin = User.newAdmin(adminUser, encoder.encode(adminBootstrap.bootstrapPassword()), Instant.now());
        users.save(admin);
        log.info("Bootstrapped admin user '{}'", adminUser);
    }

    public AuthResponse register(RegisterRequest req) {
        if (users.existsByUsername(req.username())) {
            throw new DomainException(ErrorCode.CONFLICT, "用户名已存在");
        }
        User user = User.newCustomer(
            req.username(),
            encoder.encode(req.password()),
            req.displayName(),
            Instant.now()
        );
        User saved = users.save(user);
        return issueTokens(saved);
    }

    public AuthResponse login(LoginRequest req) {
        User user = users.findByUsername(req.username())
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!encoder.matches(req.password(), user.passwordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return issueTokens(user);
    }

    public AuthResponse refresh(RefreshRequest req) {
        Claims claims;
        try {
            claims = jwt.parseRefreshToken(req.refreshToken());
        } catch (Exception e) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        String userId = claims.getSubject();
        User user = users.findById(userId)
            .orElseThrow(() -> new NotFoundException("用户不存在"));
        return issueTokens(user);
    }

    private AuthResponse issueTokens(User user) {
        String access = jwt.issueAccessToken(user.id(), user.username(), user.role());
        String refresh = jwt.issueRefreshToken(user.id(), user.role());
        return new AuthResponse(access, refresh, "Bearer", 900, user.username(), user.role().name());
    }
}
