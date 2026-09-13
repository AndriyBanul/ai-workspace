package com.aiworkspace.knowledge.config;

import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai-workspace.knowledge.search")
public record KnowledgeSearchProperties(String mode) {

    public static final String HYBRID = "HYBRID";
    public static final String VECTOR = "VECTOR";

    public KnowledgeSearchProperties {
        mode = mode == null || mode.isBlank() ? HYBRID : mode.trim().toUpperCase(Locale.ROOT);
        if (!HYBRID.equals(mode) && !VECTOR.equals(mode)) {
            throw new IllegalArgumentException("Knowledge search mode must be HYBRID or VECTOR");
        }
    }

    public boolean vectorOnly() {
        return VECTOR.equals(mode);
    }
}
