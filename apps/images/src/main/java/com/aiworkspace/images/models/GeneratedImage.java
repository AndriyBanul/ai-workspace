package com.aiworkspace.images.models;

import lombok.Builder;

@Builder
public record GeneratedImage(String filename, String mediaType, byte[] content) {
}
