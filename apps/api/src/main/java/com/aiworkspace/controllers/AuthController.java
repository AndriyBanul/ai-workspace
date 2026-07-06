package com.aiworkspace.controllers;

import com.aiworkspace.users.models.UserAccount;
import com.aiworkspace.users.services.UserAccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserAccountService userAccountService;

    public AuthController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserAccount> register(@RequestBody RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be empty");
        }

        return ResponseEntity.status(CREATED).body(userAccountService.register(
                request.email(),
                request.password(),
                request.displayName()
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<UserAccount> me(Authentication authentication) {
        return ResponseEntity.ok(userAccountService.currentUser(authentication));
    }

    public record RegisterRequest(String email, String password, String displayName) {
    }
}
