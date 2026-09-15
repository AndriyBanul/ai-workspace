package com.aiworkspace.videos.models;

import com.aiworkspace.shared.media.TranscriptSegment;
import java.util.List;
import lombok.Builder;

@Builder
public record VideoDescriptionResponse(
        String filename,
        long size,
        String mimeType,
        String description,
        String transcript,
        String language,
        List<TranscriptSegment> segments
) {

    public VideoDescriptionResponse(String filename, long size, String mimeType, String description) {
        this(filename, size, mimeType, description, "", null, List.of());
    }

    public VideoDescriptionResponse {
        segments = segments == null ? List.of() : List.copyOf(segments);
    }
}
