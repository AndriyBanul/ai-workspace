package com.aiworkspace.videos.models;

import lombok.Builder;

@Builder
public record GeneratedVideo(String filename, String mediaType, byte[] content) {
}
