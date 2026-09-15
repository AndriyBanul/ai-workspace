package com.aiworkspace.videos.models;

import com.aiworkspace.shared.media.TranscriptSegment;
import java.util.List;
import lombok.Builder;

@Builder
public record YouTubeVideoIngestionResponse(
        String sourceId,
        String videoId,
        String url,
        String description,
        String transcript,
        String language,
        List<TranscriptSegment> segments
) {

    public YouTubeVideoIngestionResponse {
        segments = segments == null ? List.of() : List.copyOf(segments);
    }
}
