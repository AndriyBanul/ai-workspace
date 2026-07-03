package com.aiworkspace.models;

import lombok.Builder;

@Builder
public record VideoDescriptionResponse(
        String filename,
        long size,
        String mimeType,
        String description
) {
}
