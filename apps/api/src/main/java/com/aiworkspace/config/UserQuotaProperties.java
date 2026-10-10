package com.aiworkspace.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

public record UserQuotaProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("100") int ingestionsPerDay,
        @DefaultValue("500") int answersPerDay,
        @DefaultValue("25") int generationsPerDay
) {
    public UserQuotaProperties {
        if (ingestionsPerDay < 1 || answersPerDay < 1 || generationsPerDay < 1) {
            throw new IllegalArgumentException("Daily user quotas must be positive");
        }
    }
}
