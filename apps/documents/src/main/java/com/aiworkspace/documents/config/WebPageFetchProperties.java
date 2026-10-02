package com.aiworkspace.documents.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai-workspace.documents.web-page-fetch")
public record WebPageFetchProperties(
        Integer maxRedirects,
        Integer maxResponseBytes
) {

    private static final int DEFAULT_MAX_REDIRECTS = 5;
    private static final int DEFAULT_MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    public WebPageFetchProperties {
        if (maxRedirects == null) {
            maxRedirects = DEFAULT_MAX_REDIRECTS;
        }

        if (maxRedirects < 0) {
            throw new IllegalArgumentException("Web page fetch max redirects must not be negative");
        }

        if (maxResponseBytes == null) {
            maxResponseBytes = DEFAULT_MAX_RESPONSE_BYTES;
        }

        if (maxResponseBytes <= 0) {
            throw new IllegalArgumentException("Web page fetch max response bytes must be positive");
        }
    }
}
