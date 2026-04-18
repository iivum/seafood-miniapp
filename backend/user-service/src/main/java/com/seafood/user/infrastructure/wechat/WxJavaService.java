package com.seafood.user.infrastructure.wechat;

import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import cn.binarywang.wx.miniapp.bean.WxMaPhoneNumberInfo;
import cn.binarywang.wx.miniapp.api.WxMaService;
import lombok.RequiredArgsConstructor;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.stereotype.Service;

/**
 * Service for interacting with WeChat Mini Program APIs.
 * Encapsulates the WxJava SDK functionality for user authentication
 * and data decryption operations.
 *
 * <p>This service handles the communication with WeChat servers for
 * session key exchange and sensitive data decryption. All operations
 * may throw WxErrorException for handling in the calling layer.</p>
 *
 * @see WxMaService
 * @see <a href="https://github.com/Wechat-Group/WxJava">WxJava SDK</a>
 */
@Service
@RequiredArgsConstructor
public class WxJavaService {

    private final WxMaService wxMaService;

    /**
     * Exchanges a login code for a session key.
     * The session key is required for decrypting WeChat user data
     * such as phone numbers.
     *
     * <p>The code is obtained from the WeChat mini program
     * via wx.login() and can only be used once.</p>
     *
     * @param code the login code from WeChat mini program
     * @return the session key for subsequent decryption operations
     * @throws WxErrorException if the code is invalid or expired
     */
    public String getSessionKey(String code) throws WxErrorException {
        WxMaJscode2SessionResult result = wxMaService.getUserService()
                .getSessionInfo(code);
        return result.getSessionKey();
    }

    /**
     * Decrypts the user's phone number from WeChat's encrypted data.
     * The phone number is obtained through WeChat's getPhoneNumber button
     * which returns encrypted data that must be decrypted using the session key.
     *
     * <p>This method validates that the session key matches the user
     * who originally obtained it to prevent phone number theft.</p>
     *
     * @param sessionKey    the session key obtained from getSessionKey
     * @param encryptedData encrypted phone number data from WeChat
     * @param iv            the initialization vector from WeChat
     * @return the user's phone number as a string
     * @throws WxErrorException if decryption fails due to invalid data or expired session
     */
    public String decryptPhoneNumber(String sessionKey, String encryptedData, String iv) throws WxErrorException {
        WxMaPhoneNumberInfo phoneInfo = wxMaService.getUserService()
                .getPhoneNoInfo(sessionKey, encryptedData, iv);
        return phoneInfo.getPhoneNumber();
    }
}
