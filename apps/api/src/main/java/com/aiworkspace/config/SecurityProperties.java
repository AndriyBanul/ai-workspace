package com.aiworkspace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("ai-workspace.security")
public record SecurityProperties(
        @DefaultValue("12") int bcryptStrength,
        @DefaultValue("false") boolean requireHttps,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue UserQuota userQuota
) {

    public SecurityProperties {
        if (bcryptStrength < 4 || bcryptStrength > 31) {
            throw new IllegalArgumentException("BCrypt strength must be between 4 and 31");
        }
    }

    public record UserQuota(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("100") int ingestionsPerDay,
            @DefaultValue("500") int answersPerDay,
            @DefaultValue("25") int generationsPerDay
    ) {
        public UserQuota {
            if (ingestionsPerDay < 1 || answersPerDay < 1 || generationsPerDay < 1) {
                throw new IllegalArgumentException("Daily user quotas must be positive");
            }
        }
    }

    public record RateLimit(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("300") int requestsPerMinute,
            @DefaultValue("10") int registrationRequestsPerMinute
    ) {

        public RateLimit {
            if (requestsPerMinute < 1) {
                throw new IllegalArgumentException("API rate limit must be positive");
            }
            if (registrationRequestsPerMinute < 1) {
                throw new IllegalArgumentException("Registration rate limit must be positive");
            }
        }
    }
}
