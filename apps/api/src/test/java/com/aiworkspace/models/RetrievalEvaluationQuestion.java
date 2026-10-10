package com.aiworkspace.models;


public record RetrievalEvaluationQuestion(
        String id,
        String split,
        String question,
        String sourceId,
        String evidence
) {
}
