package com.aiworkspace.orchestrator.models;

import java.util.List;

public record OrchestrationSubmission(
        String jobId,
        String workspaceId,
        IngestionJobStatus status,
        List<String> submitted,
        List<String> skipped
) {
}
