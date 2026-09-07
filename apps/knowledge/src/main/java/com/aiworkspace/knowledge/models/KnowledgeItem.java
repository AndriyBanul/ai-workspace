package com.aiworkspace.knowledge.models;

import java.time.Instant;

import lombok.Builder;

@Builder
public record KnowledgeItem(
        String id,
        String workspaceId,
        KnowledgeSourceType sourceType,
        String sourceName,
        String jobId,
        String sourceId,
        String sourceUrl,
        String content,
        Instant extractedAt,
        String parserVersion,
        Instant createdAt
) {

    public KnowledgeItem(
            String id,
            String workspaceId,
            KnowledgeSourceType sourceType,
            String sourceName,
            String jobId,
            String content,
            Instant createdAt
    ) {
        this(id, workspaceId, sourceType, sourceName, jobId, null, null, content, null, null, createdAt);
    }
}
