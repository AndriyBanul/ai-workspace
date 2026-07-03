package com.aiworkspace.orchestrator.models;

import java.time.Instant;
import java.util.List;

import lombok.Builder;

@Builder
public record IngestionJobDetails(
        String jobId,
        String workspaceId,
        IngestionJobStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        List<IngestionJobStepDetails> steps
) {
}
