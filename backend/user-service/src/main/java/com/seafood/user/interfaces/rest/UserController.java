package com.seafood.user.interfaces.rest;

import com.seafood.user.application.UserApplicationService;
import com.seafood.user.domain.model.User;
import com.seafood.user.domain.model.UserRole;
import com.seafood.user.interfaces.rest.dto.UserResponse;
import com.seafood.user.interfaces.rest.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for user management operations.
 * Provides endpoints for user registration, authentication, profile retrieval,
 * updates and deletion. Handles validation and error responses.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "APIs for managing users")
public class UserController {
    private final UserApplicationService userApplicationService;
    private final UserMapper userMapper;

    @PostMapping
    @Operation(
        summary = "Create a new user",
        description = "Creates a new user account",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "User details",
            required = true,
            content = @Content(schema = @Schema(implementation = CreateUserRequest.class))
        ),
        responses = {
            @ApiResponse(responseCode = "200", description = "User created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data")
        }
    )
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        User user = new User();
        user.setNickname(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPassword(request.getPassword());
        user.setRole(UserRole.USER);
        User created = userApplicationService.createUser(user);
        return ResponseEntity.ok(userMapper.toResponse(created));
    }

    @GetMapping
    @Operation(
        summary = "List all users with optional filtering and sorting",
        description = "Returns a list of all registered users with optional role filter and sorting",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "204", description = "No users found")
        }
    )
    public ResponseEntity<List<UserResponse>> listAllUsers(
            @Parameter(description = "Filter by role (USER, ADMIN, MERCHANT)") @RequestParam(required = false) String role,
            @Parameter(description = "Sort field (createdAt, nickname, email)") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (asc, desc)") @RequestParam(defaultValue = "desc") String sortDir) {
        List<User> users;
        if (role != null && !role.isBlank()) {
            users = userApplicationService.getUsersByRole(role, sortBy, sortDir);
        } else {
            users = userApplicationService.getAllUsers(sortBy, sortDir);
        }
        return ResponseEntity.ok(users.stream().map(userMapper::toResponse).toList());
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get user by ID",
        description = "Returns a specific user by their ID",
        parameters = @Parameter(name = "id", description = "User ID", required = true),
        responses = {
            @ApiResponse(responseCode = "200", description = "User found"),
            @ApiResponse(responseCode = "404", description = "User not found")
        }
    )
    public ResponseEntity<UserResponse> getUserById(@PathVariable String id) {
        return userApplicationService.getUserById(id)
                .map(userMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/email/{email}")
    @Operation(
        summary = "Get user by email",
        description = "Returns a user by their email address",
        parameters = @Parameter(name = "email", description = "Email address", required = true),
        responses = {
            @ApiResponse(responseCode = "200", description = "User found"),
            @ApiResponse(responseCode = "404", description = "User not found")
        }
    )
    public ResponseEntity<UserResponse> getUserByEmail(@PathVariable String email) {
        return userApplicationService.getUserByEmail(email)
                .map(userMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @Operation(
        summary = "Update user information",
        description = "Updates user information by ID",
        parameters = @Parameter(name = "id", description = "User ID", required = true),
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Updated user details",
            required = true,
            content = @Content(schema = @Schema(implementation = UpdateUserRequest.class))
        ),
        responses = {
            @ApiResponse(responseCode = "200", description = "User updated successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
        }
    )
    public ResponseEntity<UserResponse> updateUser(@PathVariable String id, @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userMapper.toResponse(userApplicationService.updateUser(id, request.getEmail(), request.getPhone(), request.getAddress())));
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete a user",
        description = "Deletes a user account by ID",
        parameters = @Parameter(name = "id", description = "User ID", required = true),
        responses = {
            @ApiResponse(responseCode = "204", description = "User deleted successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
        }
    )
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        userApplicationService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/role")
    @Operation(
        summary = "Update user role",
        description = "Updates the role of a user (USER, ADMIN, MERCHANT)",
        parameters = @Parameter(name = "id", description = "User ID", required = true),
        responses = {
            @ApiResponse(responseCode = "200", description = "User role updated successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "400", description = "Invalid role value")
        }
    )
    public ResponseEntity<UserResponse> updateUserRole(@PathVariable String id, @RequestParam("role") String role) {
        return ResponseEntity.ok(userMapper.toResponse(userApplicationService.updateUserRole(id, role)));
    }
}