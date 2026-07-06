package com.aiworkspace.images.models;

import lombok.Builder;

@Builder
public record ImageDescriptionResponse(
        String filename,
        long size,
        String mimeType,
        String description
) {
}
