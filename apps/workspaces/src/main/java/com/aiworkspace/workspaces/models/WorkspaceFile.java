package com.aiworkspace.workspaces.models;

import java.time.Instant;
import lombok.Builder;

@Builder
public record WorkspaceFile(
        String id,
        String workspaceId,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String storageKey,
        String checksumSha256,
        WorkspaceFileSourceType sourceType,
        WorkspaceFileStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) {
}
