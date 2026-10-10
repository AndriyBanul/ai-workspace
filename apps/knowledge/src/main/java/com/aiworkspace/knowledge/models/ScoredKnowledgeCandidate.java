package com.aiworkspace.knowledge.models;

public record ScoredKnowledgeCandidate(KnowledgeItem item, double score, int originalRank) {
}
