package com.aiworkspace.knowledge.models;

import com.fasterxml.jackson.databind.JsonNode;

public record WorkspaceKnowledge(
        String workspaceId,
        String documentsInfo,
        String audioInfo,
        String videoInfo,
        String imagesInfo
) {

    public static WorkspaceKnowledge fromSource(String requestedWorkspaceId, JsonNode source) {
        String sourceWorkspaceId = textOrDefault(source.get("workspaceId"), requestedWorkspaceId);

        return new WorkspaceKnowledge(
                sourceWorkspaceId,
                textOrNull(source.get("documentsInfo")),
                textOrNull(source.get("audioInfo")),
                textOrNull(source.get("videoInfo")),
                textOrNull(source.get("imagesInfo"))
        );
    }

    private static String textOrDefault(JsonNode node, String defaultValue) {
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return defaultValue;
        }

        return node.asText();
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        return node.asText();
    }
}
