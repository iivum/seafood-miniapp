package com.seafood.admin.service;

import com.seafood.admin.client.UserClient;
import com.seafood.admin.client.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserClient userClient;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userClient);
    }

    @Test
    @DisplayName("getAllUsers returns list of users")
    void getAllUsers_returnsUsers() {
        UserResponse user = new UserResponse();
        user.setId("1");
        user.setNickname("Test User");
        when(userClient.getAllUsers()).thenReturn(List.of(user));

        List<UserResponse> result = userService.getAllUsers();

        assertThat(result).hasSize(1);
        verify(userClient).getAllUsers();
    }

    @Test
    @DisplayName("getAllUsers returns empty list on error")
    void getAllUsers_returnsEmptyListOnError() {
        when(userClient.getAllUsers()).thenThrow(new RuntimeException("Network error"));

        List<UserResponse> result = userService.getAllUsers();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getUserByOpenId calls client with valid openId")
    void getUserByOpenId_callsClientWithValidOpenId() {
        UserResponse user = new UserResponse();
        user.setOpenId("open123");
        when(userClient.getUserByOpenId("open123")).thenReturn(user);

        UserResponse result = userService.getUserByOpenId("open123");

        assertThat(result.getOpenId()).isEqualTo("open123");
        verify(userClient).getUserByOpenId("open123");
    }

    @Test
    @DisplayName("getUserByOpenId throws exception for blank openId")
    void getUserByOpenId_throwsExceptionForBlankOpenId() {
        assertThatThrownBy(() -> userService.getUserByOpenId(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("OpenID is required");
    }

    @Test
    @DisplayName("updateUserRole calls client with valid parameters")
    void updateUserRole_callsClientWithValidParams() {
        UserResponse user = new UserResponse();
        user.setId("1");
        user.setRole("ADMIN");
        when(userClient.updateUserRole("1", "ADMIN")).thenReturn(user);

        UserResponse result = userService.updateUserRole("1", "ADMIN");

        assertThat(result.getRole()).isEqualTo("ADMIN");
        verify(userClient).updateUserRole("1", "ADMIN");
    }

    @Test
    @DisplayName("updateUserRole throws exception for blank user id")
    void updateUserRole_throwsExceptionForBlankUserId() {
        assertThatThrownBy(() -> userService.updateUserRole("", "ADMIN"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("User ID is required");
    }

    @Test
    @DisplayName("updateUserRole throws exception for blank role")
    void updateUserRole_throwsExceptionForBlankRole() {
        assertThatThrownBy(() -> userService.updateUserRole("1", ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Role is required");
    }

    @Test
    @DisplayName("countAdminUsers returns correct count")
    void countAdminUsers_returnsCorrectCount() {
        UserResponse admin1 = new UserResponse();
        admin1.setRole("ADMIN");
        UserResponse admin2 = new UserResponse();
        admin2.setRole("ADMIN");
        UserResponse user = new UserResponse();
        user.setRole("USER");
        when(userClient.getAllUsers()).thenReturn(List.of(admin1, admin2, user));

        long count = userService.countAdminUsers();

        assertThat(count).isEqualTo(2);
    }
}
