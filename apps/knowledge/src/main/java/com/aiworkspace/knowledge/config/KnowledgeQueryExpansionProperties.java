package com.aiworkspace.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai-workspace.knowledge.query-expansion")
public record KnowledgeQueryExpansionProperties(Boolean enabled, Integer queryLimit) {

    public KnowledgeQueryExpansionProperties {
        enabled = enabled == null || enabled;
        queryLimit = queryLimit == null ? 2 : queryLimit;
        if (queryLimit < 1 || queryLimit > 4) {
            throw new IllegalArgumentException("Knowledge query expansion limit must be between 1 and 4");
        }
    }

    public static KnowledgeQueryExpansionProperties disabled() {
        return new KnowledgeQueryExpansionProperties(false, 1);
    }
}
