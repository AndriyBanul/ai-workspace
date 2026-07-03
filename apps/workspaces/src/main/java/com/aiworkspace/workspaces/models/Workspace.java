package com.aiworkspace.workspaces.models;

import java.time.Instant;

import lombok.Builder;

@Builder
public record Workspace(
        String id,
        String ownerId,
        String name,
        Instant createdAt,
        Instant updatedAt
) {
}
