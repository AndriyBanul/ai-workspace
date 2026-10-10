package com.aiworkspace.videos.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiVideoSegmentResponse(long startMilliseconds, long endMilliseconds, String speaker, String text) {
}
