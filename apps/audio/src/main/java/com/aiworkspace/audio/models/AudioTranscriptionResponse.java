package com.aiworkspace.audio.models;

import lombok.Builder;

@Builder
public record AudioTranscriptionResponse(
        String filename,
        long sizeBytes,
        String language,
        String text
) {
}
