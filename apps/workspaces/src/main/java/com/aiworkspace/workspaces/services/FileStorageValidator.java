package com.aiworkspace.workspaces.services;

import com.aiworkspace.workspaces.models.FileStorageRequest;
import org.springframework.stereotype.Component;

@Component
public class FileStorageValidator {

    public void validateRequest(FileStorageRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("File storage request must not be null");
        }

        validateIdentifier(request.workspaceId(), "Workspace ID");
        validateIdentifier(request.fileId(), "File ID");

        if (request.originalFilename() == null || request.originalFilename().isBlank()) {
            throw new IllegalArgumentException("Original filename must not be blank");
        }

        if (request.content() == null) {
            throw new IllegalArgumentException("File content must not be null");
        }
    }

    public void validateStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Storage key must not be blank");
        }
    }

    private void validateIdentifier(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        String trimmed = value.trim();
        if (trimmed.contains("/") || trimmed.contains("\\") || ".".equals(trimmed) || "..".equals(trimmed)) {
            throw new IllegalArgumentException(fieldName + " must be a storage-safe identifier");
        }
    }
}
