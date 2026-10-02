package com.aiworkspace.shared.media;

public record TranscriptSegment(
        long startMilliseconds,
        long endMilliseconds,
        String speaker,
        String text
) {
}
