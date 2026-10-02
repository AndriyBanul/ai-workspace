package com.aiworkspace.documents.models;

import java.time.Instant;

public record WebPageExtractResponse(
        String sourceId,
        String url,
        String contentType,
        String title,
        int characterCount,
        int storedCharacterCount,
        int loggedCharacterCount,
        boolean truncated,
        Instant extractedAt,
        String parserVersion
) {
}
