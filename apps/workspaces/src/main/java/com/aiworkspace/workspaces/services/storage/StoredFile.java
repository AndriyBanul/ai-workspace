package com.aiworkspace.workspaces.services.storage;

import lombok.Builder;

@Builder
public record StoredFile(
        String storageKey,
        long sizeBytes,
        String checksumSha256
) {
}
