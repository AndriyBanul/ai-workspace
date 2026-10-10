package com.aiworkspace.audio.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WhisperResponse(String text, String language, List<WhisperSegmentResponse> segments) {
}
