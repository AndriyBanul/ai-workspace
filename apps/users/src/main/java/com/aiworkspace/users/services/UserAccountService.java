package com.aiworkspace.users.services;

import com.aiworkspace.users.mappers.UserAccountMapper;
import com.aiworkspace.users.models.RegisterRequest;
import com.aiworkspace.users.models.UserAccount;
import com.aiworkspace.users.repositories.UserAccountEntity;
import com.aiworkspace.users.repositories.UserAccountRepository;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final UserAccountValidator userAccountValidator;

    @Autowired
    public UserAccountService(
            UserAccountRepository userAccountRepository,
            UserAccountMapper userAccountMapper,
            PasswordEncoder passwordEncoder
    ) {
        this(userAccountRepository, userAccountMapper, passwordEncoder, new UserAccountValidator());
    }

    public UserAccountService(
            UserAccountRepository userAccountRepository,
            UserAccountMapper userAccountMapper,
            PasswordEncoder passwordEncoder,
            UserAccountValidator userAccountValidator
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userAccountMapper = userAccountMapper;
        this.passwordEncoder = passwordEncoder;
        this.userAccountValidator = userAccountValidator;
    }

    @Transactional
    public UserAccount register(RegisterRequest request) {
        userAccountValidator.validateRegisterRequest(request);

        return register(request.email(), request.password(), request.displayName());
    }

    @Transactional
    public UserAccount register(String email, String password, String displayName) {
        userAccountValidator.validateEmail(email);
        userAccountValidator.validatePassword(password);
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
        userAccountValidator.validateEmail(email);
        return userAccountRepository.findByEmail(email.trim().toLowerCase())
                .map(userAccountMapper::toModel)
                .orElseThrow(() -> new NoSuchElementException("User account was not found"));
    }

    @Transactional(readOnly = true)
    public UserAccount currentUser(Authentication authentication) {
        userAccountValidator.validateAuthentication(authentication);
        return findByEmail(authentication.getName());
    }

    @Transactional(readOnly = true)
    public String currentUserId(Authentication authentication) {
        return currentUser(authentication).id();
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        userAccountValidator.validateUsername(username);

        UserAccountEntity entity = userAccountRepository.findByEmail(username.trim().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("User account was not found"));

        return User.withUsername(entity.getEmail())
                .password(entity.getPasswordHash())
                .roles("USER")
                .build();
    }

    private String displayName(String displayName, String email) {
        if (displayName == null || displayName.isBlank()) {
            return email;
        }

        return displayName.trim();
    }
}
