package com.aiworkspace.orchestrator.models;

import java.time.Instant;

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
