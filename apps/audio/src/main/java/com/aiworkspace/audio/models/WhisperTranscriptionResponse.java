package com.aiworkspace.audio.models;

import lombok.Builder;

@Builder
public record WhisperTranscriptionResponse(String text, String language) {
}
