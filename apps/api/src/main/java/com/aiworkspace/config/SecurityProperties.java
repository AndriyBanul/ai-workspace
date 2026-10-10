package com.aiworkspace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("ai-workspace.security")
public record SecurityProperties(
        @DefaultValue("12") int bcryptStrength,
        @DefaultValue("false") boolean requireHttps,
        @DefaultValue RateLimitProperties rateLimit,
        @DefaultValue UserQuotaProperties userQuota
) {

    public SecurityProperties {
        if (bcryptStrength < 4 || bcryptStrength > 31) {
            throw new IllegalArgumentException("BCrypt strength must be between 4 and 31");
        }
    }
}
