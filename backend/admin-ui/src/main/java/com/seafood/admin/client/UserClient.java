package com.seafood.admin.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "user-service")
public interface UserClient {
    @GetMapping("/users")
    List<UserResponse> getAllUsers();
}
