package com.aiworkspace.knowledge.models;

import lombok.Builder;

@Builder
public record WorkspaceKnowledgeSourceFile(
        String key,
        String name,
        String type,
        String jobId,
        int sourceCount
) {
}
