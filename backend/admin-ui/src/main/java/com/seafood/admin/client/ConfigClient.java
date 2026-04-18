package com.seafood.admin.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Feign client for config-service management API.
 */
@FeignClient(name = "config-service", url = "${config.service.url:http://config-service:8888}")
public interface ConfigClient {

    @GetMapping(value = "/api/config", produces = MediaType.APPLICATION_JSON_VALUE)
    List<ConfigPropertyResponse> getProperties(
            @RequestParam("serviceName") String serviceName,
            @RequestParam(value = "profile", defaultValue = "docker") String profile,
            @RequestParam(value = "label", defaultValue = "main") String label);

    @GetMapping(value = "/api/config/{serviceName}/{profile}/{label}/{key}", produces = MediaType.APPLICATION_JSON_VALUE)
    ConfigPropertyResponse getProperty(
            @PathVariable("serviceName") String serviceName,
            @PathVariable("profile") String profile,
            @PathVariable("label") String label,
            @PathVariable("key") String key);

    @PostMapping(value = "/api/config", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    ConfigPropertyResponse saveProperty(@RequestBody SaveConfigPropertyRequest request);

    @DeleteMapping("/api/config/{serviceName}/{profile}/{label}/{key}")
    void deleteProperty(
            @PathVariable("serviceName") String serviceName,
            @PathVariable("profile") String profile,
            @PathVariable("label") String label,
            @PathVariable("key") String key);

    @PostMapping(value = "/api/config/encryption/encrypt", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    Map<String, String> encrypt(@RequestBody Map<String, String> request);

    @PostMapping(value = "/api/config/encryption/decrypt", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    Map<String, String> decrypt(@RequestBody Map<String, String> request);
}
