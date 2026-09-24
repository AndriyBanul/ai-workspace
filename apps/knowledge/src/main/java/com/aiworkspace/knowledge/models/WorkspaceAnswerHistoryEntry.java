package com.aiworkspace.knowledge.models;

import java.time.Instant;

public record WorkspaceAnswerHistoryEntry(String id, Instant createdAt, WorkspaceKnowledgeAnswer answer) {
}
