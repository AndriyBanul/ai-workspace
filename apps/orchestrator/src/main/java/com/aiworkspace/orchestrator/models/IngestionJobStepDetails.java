package com.aiworkspace.orchestrator.models;

import java.time.Instant;

import lombok.Builder;

@Builder
public record IngestionJobStepDetails(
        String type,
        IngestionStepStatus status,
        Instant startedAt,
        Instant completedAt,
        String errorMessage
) {
}
