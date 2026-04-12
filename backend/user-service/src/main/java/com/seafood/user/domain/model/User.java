package com.seafood.user.domain.model;

import com.seafood.common.domain.AggregateRoot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@NoArgsConstructor
@Document(collection = "users")
public class User extends AggregateRoot<String> {
    private String openId;
    private String nickname;
    private String avatarUrl;
    private String email;
    private String phone;
    private String password;
    private String address;
    private UserRole role;

    public User(String openId, String nickname, String avatarUrl, String phone, UserRole role) {
        super();
        this.openId = openId;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.phone = phone;
        this.role = role;
    }

    public void updateProfile(String nickname, String avatarUrl) {
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
    }
}
