package com.seafood.user.interfaces.rest;

import com.seafood.user.application.UserApplicationService;
import com.seafood.user.domain.model.User;
import com.seafood.user.domain.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.mock.ArgumentMatchers.any;
import static org.mock.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserApplicationService userApplicationService;

    @Test
    void testCreateUser() throws Exception {
        User user = new User("john_doe", "john@example.com", "password123", "123-456-7890", "123 Main St");
        user.setId("user1");
        user.setRole(UserRole.CUSTOMER);

        when(userApplicationService.createUser(any(), any(), any(), any(), any())).thenReturn(user);

        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"john_doe\",\"email\":\"john@example.com\",\"password\":\"password123\",\"phone\":\"123-456-7890\",\"address\":\"123 Main St\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("john_doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    void testCreateUserInvalidData() throws Exception {
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testListAllUsers() throws Exception {
        User user1 = new User("john_doe", "john@example.com", "password123", "123-456-7890", "123 Main St");
        User user2 = new User("jane_doe", "jane@example.com", "password123", "987-654-3210", "456 Oak St");
        user1.setId("user1");
        user2.setId("user2");

        when(userApplicationService.listAllUsers()).thenReturn(Arrays.asList(user1, user2));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("john_doe"))
                .andExpect(jsonPath("$[1].username").value("jane_doe"));
    }

    @Test
    void testListAllUsersEmpty() throws Exception {
        when(userApplicationService.listAllUsers()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetUserById() throws Exception {
        User user = new User("john_doe", "john@example.com", "password123", "123-456-7890", "123 Main St");
        user.setId("user1");

        when(userApplicationService.getUserById("user1")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/users/user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("john_doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    void testGetUserByIdNotFound() throws Exception {
        when(userApplicationService.getUserById("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/users/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetUserByEmail() throws Exception {
        User user = new User("john_doe", "john@example.com", "password123", "123-456-7890", "123 Main St");
        user.setId("user1");

        when(userApplicationService.getUserByEmail("john@example.com")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/users/email/john@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.username").value("john_doe"));
    }

    @Test
    void testUpdateUser() throws Exception {
        User user = new User("john_doe", "john_new@example.com", "password123", "987-654-3210", "456 Oak St");
        user.setId("user1");

        when(userApplicationService.updateUser(any(), any(), any(), any())).thenReturn(user);

        mockMvc.perform(put("/users/user1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"john_new@example.com\",\"phone\":\"987-654-3210\",\"address\":\"456 Oak St\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john_new@example.com"))
                .andExpect(jsonPath("$.phone").value("987-654-3210"));
    }

    @Test
    void testDeleteUser() throws Exception {
        mockMvc.perform(delete("/users/user1"))
                .andExpect(status().isNoContent());
    }
}