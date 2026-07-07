package com.aiworkspace.audio.models;

import lombok.Builder;

@Builder
public record TranscriptionResponse(String text, String language) {
}
