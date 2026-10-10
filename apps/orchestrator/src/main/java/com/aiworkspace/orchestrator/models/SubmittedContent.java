package com.aiworkspace.orchestrator.models;

import com.aiworkspace.workspaces.models.WorkspaceFile;

public record SubmittedContent(
        IngestionContentType contentType,
        WorkspaceFile file,
        OrchestrationContent original
) {
}
