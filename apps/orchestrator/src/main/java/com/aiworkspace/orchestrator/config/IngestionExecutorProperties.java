package com.aiworkspace.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("ai-workspace.orchestrator.executor")
public record IngestionExecutorProperties(
        @DefaultValue("4") int threads,
        @DefaultValue("100") int queueCapacity
) {

    public IngestionExecutorProperties {
        if (threads < 1 || queueCapacity < 1) {
            throw new IllegalArgumentException("Ingestion worker threads and queue capacity must be positive");
        }
    }
}
