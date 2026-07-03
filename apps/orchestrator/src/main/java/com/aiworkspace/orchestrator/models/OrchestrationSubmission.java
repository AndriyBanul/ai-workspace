package com.aiworkspace.orchestrator.models;

import java.util.List;

import lombok.Builder;

@Builder
public record OrchestrationSubmission(
        String jobId,
        String workspaceId,
        IngestionJobStatus status,
        List<String> submitted,
        List<String> skipped
) {
}
