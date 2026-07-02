package com.aiworkspace.knowledge.models;

public record WorkspaceKnowledgeSource(
        String id,
        String type,
        String sourceName,
        String jobId,
        String snippet
) {
}
