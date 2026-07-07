package com.aiworkspace.orchestrator.services;

import org.springframework.stereotype.Component;

@Component
public class OrchestratorValidator {

    public void validateOwnerId(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("Workspace owner ID must not be blank");
        }
    }

    public void validateWorkspaceId(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("Workspace ID must not be blank");
        }
    }
}
