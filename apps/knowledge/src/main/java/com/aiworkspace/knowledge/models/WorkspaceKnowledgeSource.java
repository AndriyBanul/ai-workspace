package com.aiworkspace.knowledge.models;

import lombok.Builder;

@Builder
public record WorkspaceKnowledgeSource(
        String id,
        String type,
        String sourceName,
        String jobId,
        String snippet,
        String sourceFileKey
) {
}
