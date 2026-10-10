package com.aiworkspace.audio.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WhisperSegmentResponse(
        String text,
        String transcript,
        String start,
        String end,
        String speaker,
        WhisperTimestampsResponse timestamps
) {
}
