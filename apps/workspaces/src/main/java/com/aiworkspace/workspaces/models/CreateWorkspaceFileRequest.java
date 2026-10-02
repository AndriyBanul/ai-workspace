package com.aiworkspace.workspaces.models;

import java.io.InputStream;
import lombok.Builder;

@Builder
public record CreateWorkspaceFileRequest(
        String workspaceId,
        WorkspaceFileSourceType sourceType,
        String originalFilename,
        String contentType,
        InputStream content
) {
}
