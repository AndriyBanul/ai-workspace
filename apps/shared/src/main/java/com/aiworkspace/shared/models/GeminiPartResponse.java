package com.aiworkspace.shared.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiPartResponse(String text, boolean thought) {
}
