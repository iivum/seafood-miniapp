package com.seafood.user.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for updating user profile information.
 * All fields are optional - only provided fields will be updated.
 *
 * <p>This DTO supports partial updates, allowing clients to
 * update only the fields that changed without sending the entire profile.</p>
 *
 * @see CreateUserRequest
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update user information")
public class UpdateUserRequest {

    @Email(message = "Invalid email format")
    @Schema(description = "Email address", example = "john@example.com")
    private String email;

    @Schema(description = "Phone number", example = "+1234567890")
    private String phone;

    @Schema(description = "Address", example = "456 New St, City, Country")
    private String address;
}
