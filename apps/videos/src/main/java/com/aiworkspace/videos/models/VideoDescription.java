package com.aiworkspace.videos.models;

import com.aiworkspace.shared.media.TranscriptSegment;
import java.util.List;
import lombok.Builder;

@Builder
public record VideoDescription(
        String filename,
        String mimeType,
        String description,
        String transcript,
        String language,
        List<TranscriptSegment> segments
) {

    public VideoDescription(String filename, String mimeType, String description) {
        this(filename, mimeType, description, "", null, List.of());
    }

    public VideoDescription {
        segments = segments == null ? List.of() : List.copyOf(segments);
    }
}
