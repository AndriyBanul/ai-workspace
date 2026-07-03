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
        String content,
        Instant createdAt
) {
}
