package com.aiworkspace.orchestrator.models;

import java.time.Instant;

import lombok.Builder;

@Builder
public record IngestionJobStep(
        String id,
        String jobId,
        IngestionContentType contentType,
        IngestionStepStatus status,
        Instant startedAt,
        Instant completedAt,
        String errorMessage
) {
}
