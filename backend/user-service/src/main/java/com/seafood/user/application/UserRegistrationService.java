package com.seafood.user.application;

import com.seafood.user.domain.model.User;
import com.seafood.user.domain.model.UserRepository;
import com.seafood.user.domain.model.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * 用户注册服务
 * 处理用户注册相关的业务逻辑
 */
@Service
@Transactional
public class UserRegistrationService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[a-zA-Z\\d]{6,20}$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserRegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 通过手机号注册新用户
     * @param phone 手机号
     * @param password 密码
     * @param nickname 昵称（可选）
     * @param verifyCode 验证码
     * @return 注册成功的用户信息
     * @throws IllegalArgumentException 如果注册信息无效
     */
    public User registerByPhone(String phone, String password, String nickname, String verifyCode) {
        validateRegistrationInput(phone, password, verifyCode);

        // 检查用户是否已存在
        if (userRepository.findByPhone(phone).isPresent()) {
            throw new IllegalArgumentException("手机号已被注册");
        }

        // 验证验证码（实际应该从Redis获取）
        if (!validateVerifyCode(phone, verifyCode)) {
            throw new IllegalArgumentException("验证码错误");
        }

        // 创建新用户
        User user = new User();
        user.setOpenId(phone); // Use phone as openId for phone registration
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(generateNickname(phone, nickname));
        user.setRole(UserRole.USER);

        // 保存用户
        User savedUser = userRepository.save(user);

        // 清除已使用的验证码
        clearVerifyCode(phone);

        return savedUser;
    }

    /**
     * 通过OpenID注册（微信注册）
     * @param openId 微信OpenID
     * @param nickname 昵称
     * @param avatarUrl 头像URL
     * @return 注册成功的用户信息
     */
    public User registerByOpenId(String openId, String nickname, String avatarUrl) {
        // 检查OpenID是否已注册
        if (userRepository.findByOpenId(openId).isPresent()) {
            throw new IllegalArgumentException("该微信账号已被注册");
        }

        // 创建新用户
        User user = new User();
        user.setOpenId(openId);
        user.setNickname(nickname != null ? nickname : "微信用户");
        user.setAvatarUrl(avatarUrl);
        user.setRole(UserRole.USER);

        return userRepository.save(user);
    }

    /**
     * 验证注册输入信息
     * @param phone 手机号
     * @param password 密码
     * @param verifyCode 验证码
     * @throws IllegalArgumentException 如果输入信息无效
     */
    private void validateRegistrationInput(String phone, String password, String verifyCode) {
        // 验证手机号
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
            throw new IllegalArgumentException("手机号格式不正确");
        }

        // 验证密码
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码长度至少为6位");
        }

        // 增强密码强度验证
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("密码必须包含大小写字母和数字");
        }

        // 验证码不能为空
        if (verifyCode == null || verifyCode.isEmpty()) {
            throw new IllegalArgumentException("验证码不能为空");
        }
    }

    /**
     * 生成用户昵称
     * @param phone 手机号
     * @param nickname 用户提供的昵称
     * @return 最终的昵称
     */
    private String generateNickname(String phone, String nickname) {
        if (nickname != null && !nickname.trim().isEmpty()) {
            return nickname.trim();
        }
        return "用户" + phone.substring(7);
    }

    /**
     * 验证验证码（简化实现）
     * 实际应该从Redis或数据库中获取验证码并验证
     */
    private boolean validateVerifyCode(String phone, String verifyCode) {
        // 简化实现：假设验证码总是123456
        // 实际实现应该从Redis获取验证码并比较
        return "123456".equals(verifyCode);
    }

    /**
     * 清除验证码（简化实现）
     * 实际应该从Redis中清除已使用的验证码
     */
    private void clearVerifyCode(String phone) {
        // 简化实现
        // 实际应该：redisTemplate.delete("verify_code:" + phone);
    }

    /**
     * 检查手机号是否已被注册
     * @param phone 手机号
     * @return 是否已被注册
     */
    public boolean isPhoneRegistered(String phone) {
        return userRepository.findByPhone(phone).isPresent();
    }

    /**
     * 检查OpenID是否已被注册
     * @param openId OpenID
     * @return 是否已被注册
     */
    public boolean isOpenIdRegistered(String openId) {
        return userRepository.findByOpenId(openId).isPresent();
    }

    /**
     * 验证密码强度
     * @param password 密码
     * @return 是否符合强度要求
     */
    public boolean validatePasswordStrength(String password) {
        return password != null && PASSWORD_PATTERN.matcher(password).matches();
    }

    /**
     * 发送注册验证码
     * @param phone 手机号
     * @return 是否发送成功
     * @throws IllegalArgumentException 如果手机号已被注册
     */
    public boolean sendRegistrationVerifyCode(String phone) {
        // 验证手机号格式
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new IllegalArgumentException("手机号格式不正确");
        }

        // 检查用户是否已存在
        if (isPhoneRegistered(phone)) {
            throw new IllegalArgumentException("手机号已被注册");
        }

        // 生成并发送验证码
        return sendVerifyCode(phone, "register");
    }

    /**
     * 发送验证码（通用方法）
     * @param phone 手机号
     * @param type 验证码类型（register/login/reset）
     * @return 是否发送成功
     */
    private boolean sendVerifyCode(String phone, String type) {
        // 生成6位随机验证码
        String code = String.format("%06d", (int) (Math.random() * 1000000));

        // 存储到Redis（实际实现）
        // redisTemplate.opsForValue().set("verify_code:" + type + ":" + phone, code, 5, TimeUnit.MINUTES);

        // 发送短信（实际实现）
        // smsService.sendVerifyCode(phone, code);

        // 简化实现：打印到控制台（仅用于开发调试，生产环境应删除）
        // TODO: 生产环境删除此行，避免验证码泄露到日志
        System.out.println("[DEV] Verification code sent to " + phone);

        return true;
    }
}