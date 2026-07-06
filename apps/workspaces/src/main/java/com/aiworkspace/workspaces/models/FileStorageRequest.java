package com.aiworkspace.workspaces.models;

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
