package com.aiworkspace.workspaces.services;

import com.aiworkspace.workspaces.models.CreateWorkspaceFileRequest;
import com.aiworkspace.workspaces.models.WorkspaceFileSourceType;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceFileValidator {

    public void validateCreateRequest(CreateWorkspaceFileRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Workspace file request must not be null");
        }

        validateWorkspaceId(request.workspaceId());
        validateSourceType(request.sourceType());
        validateOriginalFilename(request.originalFilename());

        if (request.content() == null) {
            throw new IllegalArgumentException("File content must not be null");
        }
    }

    public void validateWorkspaceId(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("Workspace ID must not be blank");
        }
    }

    public void validateFileId(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("Workspace file ID must not be blank");
        }
    }

    private void validateSourceType(WorkspaceFileSourceType sourceType) {
        if (sourceType == null) {
            throw new IllegalArgumentException("Workspace file source type must not be null");
        }
    }

    private void validateOriginalFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Original filename must not be blank");
        }
    }
}
