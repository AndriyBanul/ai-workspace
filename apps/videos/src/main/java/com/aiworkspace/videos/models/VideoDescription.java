package com.aiworkspace.videos.models;

import lombok.Builder;

@Builder
public record VideoDescription(String filename, String mimeType, String description) {
}
