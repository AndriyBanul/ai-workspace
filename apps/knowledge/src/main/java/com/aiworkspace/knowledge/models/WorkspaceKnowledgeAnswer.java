package com.aiworkspace.knowledge.models;

import java.util.List;

import lombok.Builder;

@Builder
public record WorkspaceKnowledgeAnswer(
        String workspaceId,
        String question,
        String answer,
        List<WorkspaceKnowledgeSourceFile> sourceFiles,
        List<WorkspaceKnowledgeSource> sources
) {
}
