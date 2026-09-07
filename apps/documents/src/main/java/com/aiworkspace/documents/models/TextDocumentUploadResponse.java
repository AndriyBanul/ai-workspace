package com.aiworkspace.documents.models;

import java.time.Instant;
import lombok.Builder;

@Builder
public record TextDocumentUploadResponse(
        String sourceId,
        String filename,
        String detectedContentType,
        String title,
        long sizeBytes,
        int characterCount,
        int blockCount,
        Instant extractedAt,
        String parserVersion
) {
}
