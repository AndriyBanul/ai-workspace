package com.aiworkspace.audio.models;

import com.aiworkspace.shared.media.TranscriptSegment;
import java.util.List;
import lombok.Builder;

@Builder
public record AudioTranscriptionResponse(
        String filename,
        long sizeBytes,
        String language,
        String text,
        List<TranscriptSegment> segments
) {

    public AudioTranscriptionResponse(String filename, long sizeBytes, String language, String text) {
        this(filename, sizeBytes, language, text, List.of());
    }

    public AudioTranscriptionResponse {
        segments = segments == null ? List.of() : List.copyOf(segments);
    }
}
