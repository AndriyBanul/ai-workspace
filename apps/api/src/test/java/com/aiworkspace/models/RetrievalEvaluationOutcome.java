package com.aiworkspace.models;

import java.util.List;

public record RetrievalEvaluationOutcome(
        String questionId,
        int firstRelevantRank,
        List<String> expectedChunks,
        List<String> retrievedChunks,
        List<String> contextChunks,
        boolean contextHit
) {
}
