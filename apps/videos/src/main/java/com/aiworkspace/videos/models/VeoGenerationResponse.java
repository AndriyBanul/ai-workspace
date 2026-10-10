package com.aiworkspace.videos.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VeoGenerationResponse(
        List<VeoGeneratedVideoResponse> generatedVideos,
        List<VeoGeneratedVideoResponse> generatedSamples,
        VeoGenerationResponse generateVideoResponse,
        List<VeoVideoResponse> videos,
        VeoVideoResponse video
) {
}
