package com.aiworkspace.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai-workspace.knowledge.embedding")
public record KnowledgeEmbeddingProperties(
        Boolean enabled,
        String model,
        Integer dimensions,
        Integer batchSize,
        Integer candidateLimit,
        Integer rrfRankConstant
) {

    private static final String DEFAULT_MODEL = "gemini-embedding-001";

    public KnowledgeEmbeddingProperties {
        enabled = enabled == null || enabled;
        model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
        if (model.startsWith("models/")) {
            model = model.substring("models/".length());
        }
        dimensions = positive(dimensions, 768, "Embedding dimensions");
        batchSize = positive(batchSize, 32, "Embedding batch size");
        candidateLimit = positive(candidateLimit, 32, "Hybrid search candidate limit");
        rrfRankConstant = positive(rrfRankConstant, 60, "RRF rank constant");
    }

    private static int positive(Integer value, int defaultValue, String name) {
        int resolved = value == null ? defaultValue : value;
        if (resolved <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return resolved;
    }
}
