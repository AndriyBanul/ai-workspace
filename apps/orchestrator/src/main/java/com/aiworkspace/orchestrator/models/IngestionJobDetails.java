package com.aiworkspace.orchestrator.models;

import java.time.Instant;
import java.util.List;

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
