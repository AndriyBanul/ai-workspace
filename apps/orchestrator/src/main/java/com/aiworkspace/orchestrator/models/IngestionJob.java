package com.aiworkspace.orchestrator.models;

import java.time.Instant;

import lombok.Builder;

@Builder
public record IngestionJob(
        String id,
        String workspaceId,
        IngestionJobStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
}
