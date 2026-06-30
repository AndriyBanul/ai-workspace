package com.aiworkspace.models;

public record ImageDescriptionResponse(
        String filename,
        long size,
        String mimeType,
        String description
) {
}
