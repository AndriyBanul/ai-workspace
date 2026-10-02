package com.aiworkspace.knowledge.models;

/** A fully written but not yet visible source generation. */
public record StagedKnowledgeIndex(String workspaceId, String sourceId, String generation, int itemCount) {
}
