package com.aiworkspace.users.models;

public record RegisterRequest(String email, String password, String displayName) {
}
