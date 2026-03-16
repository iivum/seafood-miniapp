package com.seafood.user.application;

import com.seafood.user.domain.model.User;
import com.seafood.user.domain.model.UserRepository;
import com.seafood.user.domain.model.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserApplicationService {
    private final UserRepository userRepository;

    public User loginOrRegister(String openId, String nickname, String avatarUrl) {
        return userRepository.findByOpenId(openId)
                .map(user -> {
                    user.updateProfile(nickname, avatarUrl);
                    return userRepository.save(user);
                })
                .orElseGet(() -> {
                    User newUser = new User(openId, nickname, avatarUrl, "", UserRole.USER);
                    return userRepository.save(newUser);
                });
    }

    public Optional<User> getUserById(String id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByOpenId(String openId) {
        return userRepository.findByOpenId(openId);
    }

    public java.util.List<User> getAllUsers() {
        return userRepository.findAll();
    }
}
