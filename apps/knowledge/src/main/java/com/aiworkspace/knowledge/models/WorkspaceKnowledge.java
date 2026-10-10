package com.aiworkspace.knowledge.models;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;

@Builder
public record WorkspaceKnowledge(
        String workspaceId,
        String documentsInfo,
        String audioInfo,
        String videoInfo,
        String imagesInfo
) {

    public static WorkspaceKnowledge fromSource(String requestedWorkspaceId, JsonNode source) {
        String sourceWorkspaceId = readTextOrDefault(source.get("workspaceId"), requestedWorkspaceId);

        return new WorkspaceKnowledge(
                sourceWorkspaceId,
                readTextOrNull(source.get("documentsInfo")),
                readTextOrNull(source.get("audioInfo")),
                readTextOrNull(source.get("videoInfo")),
                readTextOrNull(source.get("imagesInfo"))
        );
    }

    private static String readTextOrDefault(JsonNode node, String defaultValue) {
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return defaultValue;
        }

        return node.asText();
    }

    private static String readTextOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        return node.asText();
    }
}
