package com.aiworkspace.users.services;

import com.aiworkspace.users.entities.UserAccountEntity;
import com.aiworkspace.users.mappers.UserAccountMapperImpl;
import com.aiworkspace.users.repositories.UserAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserAccountServiceTest {

    @Test
    void registersUserWithNormalizedEmailAndEncodedPassword() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        when(repository.save(any(UserAccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UserAccountService service = newService(repository);

        var user = service.register(" User@Example.COM ", "password1234", " Test User ");

        assertEquals("user@example.com", user.email());
        assertEquals("Test User", user.displayName());
    }

    @Test
    void rejectsDuplicateEmail() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        when(repository.existsByEmail("user@example.com")).thenReturn(true);
        UserAccountService service = newService(repository);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.register("user@example.com", "password1234", "User")
        );

        assertEquals("User email is already registered", exception.getMessage());
    }

    @Test
    void rejectsShortPassword() {
        UserAccountService service = newService(mock(UserAccountRepository.class));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.register("user@example.com", "short", "User")
        );

        assertEquals("Password must be at least 12 characters", exception.getMessage());
    }

    @Test
    void rejectsExcessivelyLongPassword() {
        UserAccountService service = newService(mock(UserAccountRepository.class));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.register("user@example.com", "a".repeat(73), "User")
        );

        assertEquals("Password must not exceed 72 UTF-8 bytes", exception.getMessage());
    }

    @Test
    void rejectsMalformedEmail() {
        UserAccountService service = newService(mock(UserAccountRepository.class));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.register("not-an-email", "password1234", "User")
        );

        assertEquals("Email must be a valid address", exception.getMessage());
    }

    @Test
    void loadsUserDetailsByNormalizedEmail() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(UserAccountEntity.builder()
                .id("user-1")
                .email("user@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .displayName("User")
                .createdAt(java.time.Instant.parse("2026-07-03T00:00:00Z"))
                .build()));
        UserAccountService service = new UserAccountService(repository, new UserAccountMapperImpl(), passwordEncoder);

        var userDetails = service.loadUserByUsername(" User@Example.COM ");

        assertEquals("user@example.com", userDetails.getUsername());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_USER".equals(authority.getAuthority())));
    }

    @Test
    void failsWhenUserDetailsAreMissing() {
        UserAccountService service = newService(mock(UserAccountRepository.class));

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("missing@example.com"));
    }

    @Test
    void resolvesCurrentUserFromAuthenticationName() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(UserAccountEntity.builder()
                .id("user-1")
                .email("user@example.com")
                .passwordHash("hash")
                .displayName("User")
                .createdAt(java.time.Instant.parse("2026-07-03T00:00:00Z"))
                .build()));
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(" User@Example.COM ");
        UserAccountService service = newService(repository);

        assertEquals("user-1", service.currentUserId(authentication));
    }

    @Test
    void rejectsMissingAuthenticatedUser() {
        UserAccountService service = newService(mock(UserAccountRepository.class));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.currentUser(null)
        );

        assertEquals("Authenticated user is required", exception.getMessage());
    }

    private static UserAccountService newService(UserAccountRepository repository) {
        return new UserAccountService(repository, new UserAccountMapperImpl(), new BCryptPasswordEncoder());
    }
}
