package com.aiworkspace.audio.models;

import com.aiworkspace.shared.media.TranscriptSegment;
import java.util.List;
import lombok.Builder;

@Builder
public record AudioTranscription(
        String filename,
        String text,
        String language,
        List<TranscriptSegment> segments
) {

    public AudioTranscription(String filename, String text, String language) {
        this(filename, text, language, List.of());
    }

    public AudioTranscription {
        segments = segments == null ? List.of() : List.copyOf(segments);
    }
}
