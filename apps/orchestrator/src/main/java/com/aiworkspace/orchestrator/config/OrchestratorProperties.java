package com.aiworkspace.orchestrator.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai-workspace.orchestrator")
public record OrchestratorProperties(Duration taskTimeout) {

    private static final Duration DEFAULT_TASK_TIMEOUT = Duration.ofSeconds(45);

    public OrchestratorProperties {
        if (taskTimeout == null) {
            taskTimeout = DEFAULT_TASK_TIMEOUT;
        }
    }
}
