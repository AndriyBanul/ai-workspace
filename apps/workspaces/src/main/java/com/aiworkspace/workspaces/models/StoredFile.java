package com.aiworkspace.workspaces.models;

import lombok.Builder;

@Builder
public record StoredFile(
        String storageKey,
        long sizeBytes,
        String checksumSha256
) {
}
