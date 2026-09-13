package com.aiworkspace.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai-workspace.knowledge.reranking")
public record KnowledgeRerankingProperties(
        Boolean enabled,
        String model,
        String modelUri,
        String tokenizerUri,
        String cacheDirectory,
        Integer candidateLimit,
        Integer resultLimit,
        Integer batchSize,
        Integer maxTokens
) {

    private static final String DEFAULT_MODEL = "cross-encoder/mmarco-mMiniLMv2-L12-H384-v1";
    private static final String DEFAULT_CACHE_DIRECTORY = "data/models/spring-ai";

    public KnowledgeRerankingProperties {
        enabled = enabled != null && enabled;
        model = defaultValue(model, DEFAULT_MODEL);
        modelUri = requiredWhenEnabled(modelUri, enabled, "Reranking model URI");
        tokenizerUri = requiredWhenEnabled(tokenizerUri, enabled, "Reranking tokenizer URI");
        cacheDirectory = defaultValue(cacheDirectory, DEFAULT_CACHE_DIRECTORY);
        candidateLimit = positive(candidateLimit, 100, "Reranking candidate limit");
        resultLimit = positive(resultLimit, 12, "Reranking result limit");
        batchSize = positive(batchSize, 16, "Reranking batch size");
        maxTokens = positive(maxTokens, 512, "Reranking maximum tokens");
        if (resultLimit > 12) {
            throw new IllegalArgumentException("Reranking result limit must not exceed 12");
        }
        if (candidateLimit < resultLimit) {
            throw new IllegalArgumentException("Reranking candidate limit must be at least the result limit");
        }
        if (maxTokens > 512) {
            throw new IllegalArgumentException("Reranking maximum tokens must not exceed 512");
        }
    }

    public static KnowledgeRerankingProperties disabled() {
        return new KnowledgeRerankingProperties(false, null, null, null, null, null, null, null, null);
    }

    private static String defaultValue(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String requiredWhenEnabled(String value, boolean enabled, String name) {
        if (value == null || value.isBlank()) {
            if (enabled) {
                throw new IllegalArgumentException(name + " must not be blank when reranking is enabled");
            }
            return null;
        }
        return value.trim();
    }

    private static int positive(Integer value, int defaultValue, String name) {
        int resolved = value == null ? defaultValue : value;
        if (resolved <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return resolved;
    }
}
