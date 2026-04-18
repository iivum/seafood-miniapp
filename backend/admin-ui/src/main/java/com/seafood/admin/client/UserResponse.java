package com.seafood.admin.client;

import lombok.Data;

@Data
public class UserResponse {
    private String id;
    private String openId;
    private String nickname;
    private String avatarUrl;
    private String phone;
    private String role;
}
