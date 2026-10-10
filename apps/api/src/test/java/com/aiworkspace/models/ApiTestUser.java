package com.aiworkspace.models;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record ApiTestUser(String id, String email, String password) {

    public String basicAuthHeader() {
        String credentials = email + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
