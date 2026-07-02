package com.aiworkspace.orchestrator.models;

import java.time.Instant;

public record IngestionJob(
        String id,
        String workspaceId,
        IngestionJobStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
}
