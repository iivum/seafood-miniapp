package com.seafood.user.infrastructure.wechat;

import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import cn.binarywang.wx.miniapp.bean.WxMaPhoneNumberInfo;
import cn.binarywang.wx.miniapp.api.WxMaService;
import lombok.RequiredArgsConstructor;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.stereotype.Service;

/**
 * 微信 API 调用服务
 */
@Service
@RequiredArgsConstructor
public class WxJavaService {

    private final WxMaService wxMaService;

    /**
     * 通过 code 换取 session_key
     *
     * @param code 微信登录 code
     * @return session_key
     * @throws WxErrorException 微信 API 错误
     */
    public String getSessionKey(String code) throws WxErrorException {
        WxMaJscode2SessionResult result = wxMaService.getUserService()
                .getSessionInfo(code);
        return result.getSessionKey();
    }

    /**
     * 解密微信手机号
     *
     * @param sessionKey    会话密钥
     * @param encryptedData 加密数据
     * @param iv           解密向量
     * @return 解密后的手机号
     * @throws WxErrorException 微信 API 错误
     */
    public String decryptPhoneNumber(String sessionKey, String encryptedData, String iv) throws WxErrorException {
        WxMaPhoneNumberInfo phoneInfo = wxMaService.getUserService()
                .getPhoneNoInfo(sessionKey, encryptedData, iv);
        return phoneInfo.getPhoneNumber();
    }
}
