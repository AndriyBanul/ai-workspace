package com.aiworkspace.workspaces.services;

import com.aiworkspace.workspaces.models.CreateWorkspaceRequest;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceValidator {

    public void validateCreateRequest(CreateWorkspaceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be empty");
        }
    }

    public void validateOwnerId(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("Workspace owner ID must not be blank");
        }
    }

    public void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Workspace name must not be blank");
        }
    }

    public void validateWorkspaceId(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("Workspace ID must not be blank");
        }
    }
}
