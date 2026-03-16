package com.seafood.user.domain.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void testCreateUser() {
        User user = new User("open123", "SeafoodLover", "avatar.jpg", "13800000000", UserRole.USER);
        assertEquals("open123", user.getOpenId());
        assertEquals("SeafoodLover", user.getNickname());
        assertEquals(UserRole.USER, user.getRole());
    }

    @Test
    void testUpdateProfile() {
        User user = new User("open123", "SeafoodLover", "avatar.jpg", "13800000000", UserRole.USER);
        user.updateProfile("NewNickname", "new_avatar.jpg");
        assertEquals("NewNickname", user.getNickname());
        assertEquals("new_avatar.jpg", user.getAvatarUrl());
    }
}
