package com.aiworkspace.users.services;

import com.aiworkspace.users.models.RegisterRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class UserAccountValidator {

    public void validateRegisterRequest(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be empty");
        }
    }

    public void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email must not be blank");
        }
    }

    public void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password must not be blank");
        }

        if (password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
    }

    public void validateAuthentication(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
    }

    public void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("User account was not found");
        }
    }
}
