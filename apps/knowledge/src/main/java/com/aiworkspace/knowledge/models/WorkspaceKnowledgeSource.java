package com.aiworkspace.knowledge.models;

import java.time.Instant;
import lombok.Builder;

@Builder
public record WorkspaceKnowledgeSource(
        String id,
        String type,
        String sourceName,
        String jobId,
        String sourceId,
        String sourceUrl,
        Instant extractedAt,
        String parserVersion,
        String snippet,
        String sourceFileKey
) {
}
