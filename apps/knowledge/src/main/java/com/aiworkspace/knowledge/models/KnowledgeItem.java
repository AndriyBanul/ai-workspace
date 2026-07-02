package com.aiworkspace.knowledge.models;

import java.time.Instant;

public record KnowledgeItem(
        String id,
        String workspaceId,
        KnowledgeSourceType sourceType,
        String sourceName,
        String jobId,
        String content,
        Instant createdAt
) {
}
