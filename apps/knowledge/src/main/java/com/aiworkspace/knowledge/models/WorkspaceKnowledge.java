package com.aiworkspace.knowledge.models;

import lombok.Builder;

@Builder
public record WorkspaceKnowledge(
        String workspaceId,
        String documentsInfo,
        String audioInfo,
        String videoInfo,
        String imagesInfo
) {
}
