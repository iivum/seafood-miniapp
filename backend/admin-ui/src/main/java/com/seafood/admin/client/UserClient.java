package com.seafood.admin.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "user-service")
public interface UserClient {
    @GetMapping("/users")
    List<UserResponse> getAllUsers();

    @GetMapping("/users/{id}")
    UserResponse getUser(@PathVariable("id") String id);

    @GetMapping("/users/openid/{openId}")
    UserResponse getUserByOpenId(@PathVariable("openId") String openId);

    @PutMapping("/users/{id}/role")
    UserResponse updateUserRole(@PathVariable("id") String id, @RequestParam("role") String role);
}