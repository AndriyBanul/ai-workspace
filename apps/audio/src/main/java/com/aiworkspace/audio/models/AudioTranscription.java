package com.aiworkspace.audio.models;

import lombok.Builder;

@Builder
public record AudioTranscription(String filename, String text, String language) {
}
