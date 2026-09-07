package com.aiworkspace.documents.models;

import java.time.Instant;
import lombok.Builder;

@Builder
public record ExtractedWebPage(
        String url,
        String contentType,
        String title,
        String content,
        Instant extractedAt,
        String parserVersion
) {

    public ExtractedWebPage(String url, String title, String content) {
        this(url, null, title, content, null, null);
    }
}
