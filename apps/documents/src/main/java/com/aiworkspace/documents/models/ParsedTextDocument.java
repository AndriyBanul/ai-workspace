package com.aiworkspace.documents.models;

import java.time.Instant;
import java.util.List;
import lombok.Builder;

@Builder
public record ParsedTextDocument(
        String filename,
        String detectedContentType,
        String title,
        String content,
        List<DocumentTextBlock> blocks,
        Instant extractedAt,
        String parserVersion
) {

    public ParsedTextDocument {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    public ParsedTextDocument(
            String filename,
            String detectedContentType,
            String title,
            String content,
            List<DocumentTextBlock> blocks
    ) {
        this(filename, detectedContentType, title, content, blocks, null, null);
    }
}
