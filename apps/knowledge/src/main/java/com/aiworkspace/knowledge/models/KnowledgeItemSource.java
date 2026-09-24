package com.aiworkspace.knowledge.models;

import java.time.Instant;
import lombok.Builder;

/** Provenance of content indexed as a knowledge item. */
@Builder
public record KnowledgeItemSource(
        KnowledgeSourceType type,
        String name,
        String jobId,
        String id,
        String url,
        Instant extractedAt,
        String parserVersion,
        String generation
) {
}
