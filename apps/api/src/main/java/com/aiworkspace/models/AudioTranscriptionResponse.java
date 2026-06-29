package com.aiworkspace.models;

public record AudioTranscriptionResponse(
        String filename,
        long sizeBytes,
        String language,
        String text
) {
}
