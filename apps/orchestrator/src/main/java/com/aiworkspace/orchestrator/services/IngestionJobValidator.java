package com.aiworkspace.orchestrator.services;

import org.springframework.stereotype.Component;

@Component
public class IngestionJobValidator {

    public void validateWorkspaceId(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("Workspace ID must not be blank");
        }
    }

    public void validateJobId(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("Ingestion job ID must not be blank");
        }
    }
}
