package com.seafood.user.interfaces.rest;

import com.seafood.common.security.JwtAuthFilter;
import com.seafood.user.application.UserApplicationService;
import com.seafood.user.domain.model.User;
import com.seafood.user.domain.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserApplicationService userApplicationService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @Test
    void testCreateUser() throws Exception {
        User user = new User("openid_123", "john_doe", "https://example.com/avatar.jpg", "123-456-7890", UserRole.USER);
        user.setId("user1");

        when(userApplicationService.createUser(any(User.class))).thenReturn(user);

        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"john_doe\",\"email\":\"john@example.com\",\"password\":\"password123\",\"phone\":\"123-456-7890\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("john_doe"));
    }

    @Test
    void testCreateUserInvalidData() throws Exception {
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"email\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testListAllUsers() throws Exception {
        User user1 = new User("openid_1", "john_doe", "https://example.com/avatar1.jpg", "123-456-7890", UserRole.USER);
        User user2 = new User("openid_2", "jane_doe", "https://example.com/avatar2.jpg", "987-654-3210", UserRole.USER);
        user1.setId("user1");
        user2.setId("user2");

        when(userApplicationService.getAllUsers()).thenReturn(Arrays.asList(user1, user2));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nickname").value("john_doe"))
                .andExpect(jsonPath("$[1].nickname").value("jane_doe"));
    }

    @Test
    void testListAllUsersEmpty() throws Exception {
        when(userApplicationService.getAllUsers()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetUserById() throws Exception {
        User user = new User("openid_123", "john_doe", "https://example.com/avatar.jpg", "123-456-7890", UserRole.USER);
        user.setId("user1");

        when(userApplicationService.getUserById("user1")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/users/user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("john_doe"));
    }

    @Test
    void testGetUserByIdNotFound() throws Exception {
        when(userApplicationService.getUserById("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/users/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testUpdateUser() throws Exception {
        User user = new User("openid_123", "john_doe", "https://example.com/avatar_new.jpg", "987-654-3210", UserRole.USER);
        user.setId("user1");

        when(userApplicationService.updateUser(any(String.class), any(), any(), any())).thenReturn(user);

        mockMvc.perform(put("/users/user1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"john@example.com\",\"phone\":\"987-654-3210\",\"address\":\"456 New St\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("987-654-3210"));
    }

    @Test
    void testDeleteUser() throws Exception {
        doNothing().when(userApplicationService).deleteUser("user1");

        mockMvc.perform(delete("/users/user1"))
                .andExpect(status().isNoContent());
    }
}
