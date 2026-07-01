package com.aiworkspace.models;

public record VideoDescriptionResponse(
        String filename,
        long size,
        String mimeType,
        String description
) {
}
