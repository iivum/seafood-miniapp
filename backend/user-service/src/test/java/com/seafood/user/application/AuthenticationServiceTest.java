package com.seafood.user.application;

import com.seafood.common.security.JwtUtil;
import com.seafood.user.domain.model.TokenRepository;
import com.seafood.user.domain.model.User;
import com.seafood.user.domain.model.UserRepository;
import com.seafood.user.domain.model.UserRole;
import com.seafood.user.domain.model.WeChatLoginException;
import com.seafood.user.infrastructure.wechat.WxJavaService;
import com.seafood.user.interfaces.rest.LoginResponse;
import me.chanjar.weixin.common.error.WxErrorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticationService Tests")
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private CustomPasswordEncoder passwordEncoder;

    @Mock
    private WxJavaService wxJavaService;

    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(
                userRepository, tokenRepository, jwtUtil, passwordEncoder, wxJavaService
        );
    }

    @Test
    @DisplayName("weChatPhoneLogin should return LoginResponse when successful")
    void weChatPhoneLogin_success() throws WxErrorException {
        // Given
        String code = "test_code";
        String encryptedData = "encrypted_data";
        String iv = "test_iv";
        String phone = "13800138000";
        String sessionKey = "session_key_123";
        String userId = "user_id_123";

        User user = new User();
        user.setId(userId);
        user.setPhone(phone);
        user.setNickname("微信用户");
        user.setRole(UserRole.USER);

        when(wxJavaService.getSessionKey(code)).thenReturn(sessionKey);
        when(wxJavaService.decryptPhoneNumber(sessionKey, encryptedData, iv)).thenReturn(phone);
        when(userRepository.findByPhone(phone)).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("access_token");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("refresh_token");

        // When
        LoginResponse response = authenticationService.weChatPhoneLogin(code, encryptedData, iv);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("access_token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh_token");
    }

    @Test
    @DisplayName("weChatPhoneLogin should create new user when phone not found")
    void weChatPhoneLogin_createsNewUser() throws WxErrorException {
        // Given
        String code = "test_code";
        String encryptedData = "encrypted_data";
        String iv = "test_iv";
        String phone = "13800138000";

        User newUser = new User();
        newUser.setId("new_user_id");
        newUser.setPhone(phone);
        newUser.setNickname("微信用户");
        newUser.setRole(UserRole.USER);

        when(wxJavaService.getSessionKey(code)).thenReturn("session_key");
        when(wxJavaService.decryptPhoneNumber(anyString(), anyString(), anyString())).thenReturn(phone);
        when(userRepository.findByPhone(phone)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(newUser);
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("access_token");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("refresh_token");

        // When
        LoginResponse response = authenticationService.weChatPhoneLogin(code, encryptedData, iv);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("access_token");
    }

    @Test
    @DisplayName("weChatPhoneLogin should throw WeChatLoginException when WxJava fails")
    void weChatPhoneLogin_throwsWeChatLoginException() throws WxErrorException {
        // Given
        String code = "test_code";
        String encryptedData = "encrypted_data";
        String iv = "test_iv";

        when(wxJavaService.getSessionKey(code)).thenThrow(new WxErrorException("Invalid code"));

        // When/Then
        assertThatThrownBy(() -> authenticationService.weChatPhoneLogin(code, encryptedData, iv))
                .isInstanceOf(WeChatLoginException.class)
                .hasMessageContaining("微信登录失败");
    }

    @Test
    @DisplayName("weChatLogin should return LoginResponse when successful")
    void weChatLogin_success() {
        // Given
        String openId = "openid_123";
        String nickname = "测试用户";
        String avatarUrl = "https://example.com/avatar.jpg";

        User user = new User();
        user.setId("user_id");
        user.setOpenId(openId);
        user.setNickname(nickname);
        user.setAvatarUrl(avatarUrl);
        user.setRole(UserRole.USER);

        when(userRepository.findByOpenId(openId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("access_token");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("refresh_token");

        // When
        LoginResponse response = authenticationService.weChatLogin(openId, nickname, avatarUrl);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("access_token");
        assertThat(response.getNickname()).isEqualTo(nickname);
    }
}
