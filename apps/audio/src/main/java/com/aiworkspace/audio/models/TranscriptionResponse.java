package com.aiworkspace.audio.models;

import com.aiworkspace.shared.media.TranscriptSegment;
import java.util.List;
import lombok.Builder;

@Builder
public record TranscriptionResponse(String text, String language, List<TranscriptSegment> segments) {

    public TranscriptionResponse(String text, String language) {
        this(text, language, List.of());
    }

    public TranscriptionResponse {
        segments = segments == null ? List.of() : List.copyOf(segments);
    }
}
