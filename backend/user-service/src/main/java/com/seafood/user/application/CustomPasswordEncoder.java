package com.seafood.user.application;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 自定义密码编码器
 * 使用BCrypt算法进行密码加密和验证
 */
@Component
public class CustomPasswordEncoder implements PasswordEncoder {

    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    public CustomPasswordEncoder() {
        this.bCryptPasswordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * 对原始密码进行编码
     * @param rawPassword 原始密码
     * @return 编码后的密码
     */
    @Override
    public String encode(CharSequence rawPassword) {
        return bCryptPasswordEncoder.encode(rawPassword);
    }

    /**
     * 验证原始密码是否与编码后的密码匹配
     * @param rawPassword 原始密码
     * @param encodedPassword 编码后的密码
     * @return 是否匹配
     */
    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return bCryptPasswordEncoder.matches(rawPassword, encodedPassword);
    }

    /**
     * 检查编码后的密码是否需要重新编码
     * 如果密码使用了较弱的加密强度，则返回true
     * @param encodedPassword 编码后的密码
     * @return 是否需要重新编码
     */
    public boolean needsReEncode(String encodedPassword) {
        return bCryptPasswordEncoder.upgradeEncoding(encodedPassword);
    }

    /**
     * 使用指定强度创建新的编码器
     * @param strength 强度值（4-31）
     * @return 新的密码编码器
     */
    public PasswordEncoder withStrength(int strength) {
        return new BCryptPasswordEncoder(strength);
    }
}