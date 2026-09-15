package com.aiworkspace.users.services;

import com.aiworkspace.users.models.RegisterRequest;
import java.nio.charset.StandardCharsets;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class UserAccountValidator {

    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final int MAX_PASSWORD_BYTES = 72;
    private static final int MAX_EMAIL_LENGTH = 320;

    public void validateRegisterRequest(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be empty");
        }
    }

    public void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email must not be blank");
        }

        String normalizedEmail = email.trim();
        int separator = normalizedEmail.indexOf('@');
        if (normalizedEmail.length() > MAX_EMAIL_LENGTH || separator < 1
                || separator != normalizedEmail.lastIndexOf('@') || separator == normalizedEmail.length() - 1
                || normalizedEmail.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Email must be a valid address");
        }
    }

    public void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password must not be blank");
        }

        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least 12 characters");
        }

        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes");
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
