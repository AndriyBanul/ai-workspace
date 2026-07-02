package com.aiworkspace.orchestrator.models;

import java.time.Instant;

public record IngestionJobStepDetails(
        String type,
        IngestionStepStatus status,
        Instant startedAt,
        Instant completedAt,
        String errorMessage
) {
}
