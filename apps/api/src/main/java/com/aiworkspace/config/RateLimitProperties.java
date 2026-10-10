package com.aiworkspace.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("300") int requestsPerMinute,
        @DefaultValue("10") int registrationRequestsPerMinute
) {

    public RateLimitProperties {
        if (requestsPerMinute < 1) {
            throw new IllegalArgumentException("API rate limit must be positive");
        }
        if (registrationRequestsPerMinute < 1) {
            throw new IllegalArgumentException("Registration rate limit must be positive");
        }
    }
}
