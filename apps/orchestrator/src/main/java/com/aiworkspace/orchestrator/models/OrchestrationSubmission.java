package com.aiworkspace.orchestrator.models;

import java.util.List;
import java.util.Map;

import lombok.Builder;

@Builder
public record OrchestrationSubmission(
        String jobId,
        String workspaceId,
        IngestionJobStatus status,
        List<String> submitted,
        List<String> skipped,
        Map<String, String> sourceIds
) {

    public OrchestrationSubmission {
        submitted = submitted == null ? List.of() : List.copyOf(submitted);
        skipped = skipped == null ? List.of() : List.copyOf(skipped);
        sourceIds = sourceIds == null ? Map.of() : Map.copyOf(sourceIds);
    }
}
