package com.aiworkspace.knowledge.models;

import java.time.Instant;
import lombok.Builder;

@Builder
public record KnowledgeSourceMetadata(
        String sourceId,
        String sourceUrl,
        Instant extractedAt,
        String parserVersion
) {
}
