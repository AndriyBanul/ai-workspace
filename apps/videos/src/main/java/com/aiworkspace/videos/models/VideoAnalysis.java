package com.aiworkspace.videos.models;

import com.aiworkspace.shared.media.TranscriptSegment;
import java.util.List;
import lombok.Builder;

@Builder
public record VideoAnalysis(
        String summary,
        String transcript,
        String language,
        List<TranscriptSegment> segments
) {

    public VideoAnalysis {
        segments = segments == null ? List.of() : List.copyOf(segments);
    }
}
