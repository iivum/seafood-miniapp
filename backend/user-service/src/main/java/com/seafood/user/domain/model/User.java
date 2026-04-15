package com.seafood.user.domain.model;

import com.seafood.common.domain.AggregateRoot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * User aggregate root representing a registered user in the system.
 * Follows Domain-Driven Design principles and encapsulates user-related business logic.
 * 
 * <p>Users can be either regular users or administrators based on their role.
 * User identity is primarily managed through WeChat OpenID integration.</p>
 * 
 * @see AggregateRoot
 * @see UserRole
 * @see org.springframework.data.mongodb.core.mapping.Document
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "users")
public class User extends AggregateRoot<String> {
    
    /** WeChat OpenID for user identification */
    private String openId;
    
    /** User display name in the system */
    private String nickname;
    
    /** URL to user avatar image */
    private String avatarUrl;
    
    /** User email address for notifications */
    private String email;
    
    /** User phone number for contact */
    private String phone;
    
    /** User password (hashed) for authentication */
    private String password;
    
    /** Default shipping address */
    private String address;
    
    /** User role determining permissions (USER or ADMIN) */
    private UserRole role;

    /**
     * Creates a new User with the specified attributes.
     * Used primarily for initial user registration via WeChat.
     *
     * @param openId    WeChat OpenID (required for WeChat login)
     * @param nickname  Display name shown in the app
     * @param avatarUrl URL to user avatar image
     * @param phone     Contact phone number
     * @param role      User role (USER or ADMIN)
     */
    public User(String openId, String nickname, String avatarUrl, String phone, UserRole role) {
        super();
        this.openId = openId;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.phone = phone;
        this.role = role;
    }

    /**
     * Updates the user's profile information.
     * Allows users to change their display name and avatar.
     *
     * @param nickname  New display name
     * @param avatarUrl New avatar image URL
     */
    public void updateProfile(String nickname, String avatarUrl) {
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
    }
}
