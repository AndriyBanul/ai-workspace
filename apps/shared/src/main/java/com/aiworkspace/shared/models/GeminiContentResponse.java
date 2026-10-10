package com.aiworkspace.shared.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiContentResponse(List<GeminiPartResponse> parts) {
}
