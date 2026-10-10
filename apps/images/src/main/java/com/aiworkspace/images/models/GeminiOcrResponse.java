package com.aiworkspace.images.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiOcrResponse(List<GeminiOcrRegionResponse> regions) {
}
