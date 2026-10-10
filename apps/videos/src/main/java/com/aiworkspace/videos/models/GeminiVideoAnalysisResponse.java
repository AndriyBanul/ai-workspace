package com.aiworkspace.videos.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiVideoAnalysisResponse(
        String summary,
        String transcript,
        String language,
        List<GeminiVideoSegmentResponse> segments
) {
}
