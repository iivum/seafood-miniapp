package com.seafood.admin;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.Theme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@Theme(value = "seafood-admin")
public class AdminApplication implements AppShellConfigurator {
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
