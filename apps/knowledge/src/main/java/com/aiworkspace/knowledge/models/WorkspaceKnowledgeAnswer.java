package com.aiworkspace.knowledge.models;

import java.util.List;

public record WorkspaceKnowledgeAnswer(
        String workspaceId,
        String question,
        String answer,
        List<WorkspaceKnowledgeSource> sources
) {
}
