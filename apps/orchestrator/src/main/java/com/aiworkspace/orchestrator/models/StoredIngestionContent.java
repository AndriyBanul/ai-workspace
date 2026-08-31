package com.aiworkspace.orchestrator.models;

import com.aiworkspace.workspaces.models.WorkspaceFile;

public record StoredIngestionContent(
        IngestionContentType contentType,
        WorkspaceFile file,
        OrchestrationContent original
) {
}
