package com.aiworkspace.storage.models;

import java.io.InputStream;
import lombok.Builder;

@Builder
public record FileStorageRequest(
        String workspaceId,
        String fileId,
        String originalFilename,
        String contentType,
        InputStream content
) {
}
