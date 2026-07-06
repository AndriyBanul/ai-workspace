package com.aiworkspace.users.services;

import com.aiworkspace.users.mappers.UserAccountMapper;
import com.aiworkspace.users.models.UserAccount;
import com.aiworkspace.users.repositories.UserAccountEntity;
import com.aiworkspace.users.repositories.UserAccountRepository;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;
    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(
            UserAccountRepository userAccountRepository,
            UserAccountMapper userAccountMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userAccountMapper = userAccountMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserAccount register(String email, String password, String displayName) {
        validateEmail(email);
        validatePassword(password);
        String normalizedEmail = email.trim().toLowerCase();
        if (userAccountRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("User email is already registered");
        }

        Instant now = Instant.now();
        UserAccountEntity entity = UserAccountEntity.builder()
                .id(UUID.randomUUID().toString())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(password))
                .displayName(displayName(displayName, normalizedEmail))
                .createdAt(now)
                .build();

        return userAccountMapper.toModel(userAccountRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public UserAccount findByEmail(String email) {
        validateEmail(email);
        return userAccountRepository.findByEmail(email.trim().toLowerCase())
                .map(userAccountMapper::toModel)
                .orElseThrow(() -> new NoSuchElementException("User account was not found"));
    }

    @Transactional(readOnly = true)
    public UserAccount currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalArgumentException("Authenticated user is required");
        }

        return findByEmail(authentication.getName());
    }

    @Transactional(readOnly = true)
    public String currentUserId(Authentication authentication) {
        return currentUser(authentication).id();
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("User account was not found");
        }

        UserAccountEntity entity = userAccountRepository.findByEmail(username.trim().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("User account was not found"));

        return User.withUsername(entity.getEmail())
                .password(entity.getPasswordHash())
                .roles("USER")
                .build();
    }

    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email must not be blank");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password must not be blank");
        }

        if (password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
    }

    private String displayName(String displayName, String email) {
        if (displayName == null || displayName.isBlank()) {
            return email;
        }

        return displayName.trim();
    }
}
