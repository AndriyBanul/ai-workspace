package com.aiworkspace.knowledge.models;

import com.fasterxml.jackson.databind.JsonNode;

public record WorkspaceKnowledge(
        String workspaceId,
        JsonNode documentsInfo,
        JsonNode audioInfo,
        JsonNode videoInfo,
        JsonNode imagesInfo
) {

    public static WorkspaceKnowledge fromSource(String requestedWorkspaceId, JsonNode source) {
        String sourceWorkspaceId = textOrDefault(source.get("workspaceId"), requestedWorkspaceId);

        return new WorkspaceKnowledge(
                sourceWorkspaceId,
                source.get("documentsInfo"),
                source.get("audioInfo"),
                source.get("videoInfo"),
                source.get("imagesInfo")
        );
    }

    private static String textOrDefault(JsonNode node, String defaultValue) {
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return defaultValue;
        }

        return node.asText();
    }
}
