package com.aiworkspace.knowledge.models;

import java.time.Instant;
import lombok.Builder;

@Builder
public record WorkspaceKnowledgeSourceFile(
        String key,
        String name,
        String type,
        String jobId,
        String sourceId,
        String sourceUrl,
        Instant extractedAt,
        String parserVersion,
        int sourceCount
) {
}
