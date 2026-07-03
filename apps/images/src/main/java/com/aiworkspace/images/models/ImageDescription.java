package com.aiworkspace.images.models;

import lombok.Builder;

@Builder
public record ImageDescription(String filename, String mimeType, String description) {
}
