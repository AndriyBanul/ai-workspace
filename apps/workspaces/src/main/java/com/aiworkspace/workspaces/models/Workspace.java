package com.aiworkspace.workspaces.models;

import java.time.Instant;

public record Workspace(
        String id,
        String name,
        Instant createdAt,
        Instant updatedAt
) {
}
