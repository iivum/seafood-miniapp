package com.seafood.user.interfaces.rest;

import com.seafood.user.application.UserApplicationService;
import com.seafood.user.domain.model.User;
import com.seafood.user.domain.model.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "APIs for managing users")
public class UserController {
    private final UserApplicationService userApplicationService;

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
    public ResponseEntity<User> createUser(@RequestBody CreateUserRequest request) {
        User user = new User();
        user.setNickname(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPassword(request.getPassword());
        user.setRole(UserRole.USER);
        User created = userApplicationService.createUser(user);
        return ResponseEntity.ok(created);
    }

    @GetMapping
    @Operation(
        summary = "List all users",
        description = "Returns a list of all registered users",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "204", description = "No users found")
        }
    )
    public ResponseEntity<List<User>> listAllUsers() {
        return ResponseEntity.ok(userApplicationService.getAllUsers());
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
    public ResponseEntity<User> getUserById(@PathVariable String id) {
        return userApplicationService.getUserById(id)
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
    public ResponseEntity<User> getUserByEmail(@PathVariable String email) {
        return userApplicationService.getUserByEmail(email)
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
    public ResponseEntity<User> updateUser(@PathVariable String id, @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userApplicationService.updateUser(id, request.getEmail(), request.getPhone(), request.getAddress()));
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
}