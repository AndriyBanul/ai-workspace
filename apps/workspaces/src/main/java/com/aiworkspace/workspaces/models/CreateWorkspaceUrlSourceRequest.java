package com.aiworkspace.workspaces.models;

import lombok.Builder;

@Builder
public record CreateWorkspaceUrlSourceRequest(
        String workspaceId,
        WorkspaceFileSourceType sourceType,
        String displayName,
        String sourceUrl,
        String contentType
) {
}
