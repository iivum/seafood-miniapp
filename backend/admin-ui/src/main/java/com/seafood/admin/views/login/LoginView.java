package com.seafood.admin.views.login;

import com.seafood.admin.service.JwtService;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinService;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

@Route(value = "login")
@PageTitle("登录 | 海鲜商城")
public class LoginView extends VerticalLayout {

    private static final String JWT_COOKIE = "jwt_token";

    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final LoginForm loginForm;

    public LoginView(
            JwtService jwtService,
            @Value("${admin.username:admin}") String adminUsername,
            @Value("${admin.password:admin123}") String adminPassword) {

        this.jwtService = jwtService;

        // Create in-memory UserDetailsService with configured credentials
        UserDetails adminUser = User.builder()
            .username(adminUsername)
            .password("{noop}" + adminPassword)
            .authorities(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
            .build();

        UserDetailsService inMemoryUserDetailsService = username -> {
            if (username.equals(adminUsername)) {
                return adminUser;
            }
            throw new org.springframework.security.core.userdetails.UsernameNotFoundException("User not found: " + username);
        };

        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(inMemoryUserDetailsService);
        this.authenticationManager = new ProviderManager(authProvider);

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        getStyle()
            .set("background", "linear-gradient(135deg, #0c1929 0%, #023e6a 50%, #0096c7 100%)")
            .set("min-height", "100vh");

        // Create login form
        loginForm = new LoginForm();
        // Don't set action - we handle login via addLoginListener

        // Configure i18n for Chinese
        LoginI18n i18n = LoginI18n.createDefault();
        i18n.getForm().setUsername("用户名");
        i18n.getForm().setPassword("密码");
        i18n.getForm().setSubmit("登录");
        i18n.getErrorMessage().setTitle("登录失败");
        i18n.getErrorMessage().setMessage("用户名或密码错误");
        loginForm.setI18n(i18n);

        // Set if forgot password should be shown
        loginForm.setForgotPasswordButtonVisible(false);

        // Add login listener
        loginForm.addLoginListener(e -> {
            String username = e.getUsername();
            String password = e.getPassword();

            try {
                Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
                );

                if (auth.isAuthenticated()) {
                    String token = jwtService.generateToken(username, "ADMIN");
                    saveTokenCookie(token);
                    // Redirect to dashboard
                    getUI().ifPresent(ui -> ui.navigate("dashboard"));
                }
            } catch (AuthenticationException ex) {
                // Show error on the login form
                loginForm.setError(true);
            }
        });

        loginForm.getStyle()
            .set("background", "rgba(255,255,255,0.1)")
            .set("border-radius", "16px")
            .set("padding", "40px")
            .set("backdrop-filter", "blur(10px)")
            .set("box-shadow", "0 8px 32px rgba(0,0,0,0.2)");

        add(loginForm);
    }

    private void saveTokenCookie(String token) {
        Cookie cookie = new Cookie(JWT_COOKIE, token);
        cookie.setPath("/");
        cookie.setMaxAge(86400);
        cookie.setHttpOnly(true);
        VaadinService.getCurrentResponse().addCookie(cookie);
    }
}
