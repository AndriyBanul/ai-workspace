package com.aiworkspace.orchestrator.models;

import java.time.Instant;

public record SourceRecoveryTask(
        String sourceId,
        String workspaceId,
        SourceOperationType operationType,
        SourceRecoveryStatus status,
        int attemptCount,
        Instant nextAttemptAt,
        Instant leaseExpiresAt,
        String lastErrorCode,
        String lastErrorMessage,
        Instant createdAt,
        Instant updatedAt
) {
}
