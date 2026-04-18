package com.seafood.user.infrastructure.wechat;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.WxMaUserService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import me.chanjar.weixin.common.error.WxErrorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WxJavaService Tests")
class WxJavaServiceTest {

    @Mock
    private WxMaService wxMaService;

    @Mock
    private WxMaUserService wxMaUserService;

    private WxJavaService wxJavaService;

    @BeforeEach
    void setUp() {
        wxJavaService = new WxJavaService(wxMaService);
    }

    @Test
    @DisplayName("getSessionKey should return session key when successful")
    void getSessionKey_success() throws WxErrorException {
        // Given
        String code = "test_code";
        String expectedSessionKey = "session_key_123";
        WxMaJscode2SessionResult sessionResult = new WxMaJscode2SessionResult();
        sessionResult.setSessionKey(expectedSessionKey);

        when(wxMaService.getUserService()).thenReturn(wxMaUserService);
        when(wxMaUserService.getSessionInfo(code)).thenReturn(sessionResult);

        // When
        String sessionKey = wxJavaService.getSessionKey(code);

        // Then
        assertThat(sessionKey).isEqualTo(expectedSessionKey);
    }

    @Test
    @DisplayName("getSessionKey should throw WxErrorException when code is invalid")
    void getSessionKey_throwsException() throws WxErrorException {
        // Given
        String code = "invalid_code";
        when(wxMaService.getUserService()).thenReturn(wxMaUserService);
        when(wxMaUserService.getSessionInfo(code)).thenThrow(new WxErrorException("Invalid code"));

        // When/Then
        assertThatThrownBy(() -> wxJavaService.getSessionKey(code))
                .isInstanceOf(WxErrorException.class);
    }
}
