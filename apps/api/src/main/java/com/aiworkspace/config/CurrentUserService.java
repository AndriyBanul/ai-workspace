package com.aiworkspace.config;

import com.aiworkspace.users.models.UserAccount;
import com.aiworkspace.users.services.UserAccountService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserAccountService userAccountService;

    public CurrentUserService(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    public UserAccount currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalArgumentException("Authenticated user is required");
        }

        return userAccountService.findByEmail(authentication.getName());
    }

    public String currentUserId(Authentication authentication) {
        return currentUser(authentication).id();
    }
}
