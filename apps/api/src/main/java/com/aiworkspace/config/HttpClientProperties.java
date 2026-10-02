package com.aiworkspace.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai-workspace.http")
public record HttpClientProperties(
        Duration connectTimeout,
        Duration readTimeout,
        String userAgent
) {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);
    private static final String DEFAULT_USER_AGENT = "AI-Workspace";

    public HttpClientProperties {
        if (connectTimeout == null) {
            connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        }

        if (readTimeout == null) {
            readTimeout = DEFAULT_READ_TIMEOUT;
        }

        if (userAgent == null || userAgent.isBlank()) {
            userAgent = DEFAULT_USER_AGENT;
        }
    }
}
