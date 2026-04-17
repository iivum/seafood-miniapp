package com.seafood.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // Vaadin routes - public
                .requestMatchers("/").permitAll()
                .requestMatchers("/admin/").permitAll()
                .requestMatchers("/admin/?*").permitAll()
                .requestMatchers("/admin/login").permitAll()
                // Vaadin internal requests - public
                .requestMatchers("/?v-r=*").permitAll()
                .requestMatchers("/admin/?v-r=*").permitAll()
                .requestMatchers("/login").permitAll()
                // Vaadin static resources - public
                .requestMatchers("/VAADIN/**").permitAll()
                .requestMatchers("/admin/VAADIN/**").permitAll()
                .requestMatchers("/frontend/**").permitAll()
                .requestMatchers("/admin/frontend/**").permitAll()
                .requestMatchers("/build/**").permitAll()
                .requestMatchers("/admin/build/**").permitAll()
                // All other admin routes require authentication
                .requestMatchers("/admin/**").authenticated()
                // Allow everything else (API endpoints, etc.)
                .anyRequest().permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/admin/logout")
                .logoutSuccessUrl("/admin/login")
                .permitAll()
            );
        
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails admin = User.builder()
            .username("admin")
            .password(passwordEncoder().encode("admin123"))
            .roles("ADMIN")
            .build();
        
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
