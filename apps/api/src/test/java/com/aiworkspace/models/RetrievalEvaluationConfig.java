package com.aiworkspace.models;


public record RetrievalEvaluationConfig(
        int chunkSentences,
        int headingWeight,
        int candidates,
        int rrf
) {
}
