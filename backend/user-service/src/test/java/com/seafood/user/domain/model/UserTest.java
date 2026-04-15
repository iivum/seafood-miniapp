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

    @Test
    void testNoArgsConstructor() {
        User user = new User();
        assertNull(user.getOpenId());
        assertNull(user.getNickname());
        assertNull(user.getEmail());
    }

    @Test
    void testSettersAndGetters() {
        User user = new User();
        user.setOpenId("open456");
        user.setNickname("TestUser");
        user.setEmail("test@example.com");
        user.setPhone("13900000000");
        user.setAddress("123 Beach Road");
        user.setRole(UserRole.ADMIN);

        assertEquals("open456", user.getOpenId());
        assertEquals("TestUser", user.getNickname());
        assertEquals("test@example.com", user.getEmail());
        assertEquals("13900000000", user.getPhone());
        assertEquals("123 Beach Road", user.getAddress());
        assertEquals(UserRole.ADMIN, user.getRole());
    }
}
